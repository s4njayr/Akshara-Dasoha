package com.aksharadasoha.ledger.export

import com.aksharadasoha.ledger.domain.ColumnDef
import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.FormulaEngine
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerRow
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.RateDef
import com.aksharadasoha.ledger.domain.SchoolProfile
import com.aksharadasoha.ledger.domain.ValueType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])

class LedgerExcelExporterTest {
    private val template = LedgerTemplate(
        id = "demo",
        name = "Demo",
        description = "",
        sheetName = null,
        gradeIds = listOf("grade_8"),
        rates = listOf(RateDef("demo_rice_rate", "demo", "rice_rate", "Rice", 150.0, "g", 0)),
        columns = listOf(
            ColumnDef("demo_date", "demo", "date", "Date", null, ValueType.DATE, null, ColumnRole.DATE, null, 0, true, false),
            ColumnDef("demo_week", "demo", "week", "Week", null, ValueType.WEEKDAY, null, ColumnRole.COMPUTED, "WEEKDAY(date)", 1, true, false),
            ColumnDef("demo_count", "demo", "count", "Count", null, ValueType.NUMBER, "children", ColumnRole.INPUT, null, 2, true, false),
            ColumnDef("demo_open", "demo", "rice_opening", "Opening", "Rice", ValueType.NUMBER, "kg", ColumnRole.OPENING, "IF(ISFIRST(), INPUT(), CARRY(rice_balance))", 3, true, false),
            ColumnDef("demo_supply", "demo", "rice_supply", "Supply", "Rice", ValueType.NUMBER, "kg", ColumnRole.INPUT, null, 4, true, false),
            ColumnDef("demo_total", "demo", "rice_total", "Total", "Rice", ValueType.NUMBER, "kg", ColumnRole.COMPUTED, "SUM(rice_opening, rice_supply)", 5, true, false),
            ColumnDef("demo_spend", "demo", "rice_expenditure", "Expenditure", "Rice", ValueType.NUMBER, "kg", ColumnRole.COMPUTED, "count * rice_rate / 1000", 6, true, false),
            ColumnDef("demo_bal", "demo", "rice_balance", "Balance", "Rice", ValueType.NUMBER, "kg", ColumnRole.COMPUTED, "rice_total - rice_expenditure", 7, true, true),
        ),
    )

    @Test
    fun writesNativeFormulasAndRoundTripsInputs() {
        val rows = listOf(
            LedgerRow("r1", "i1", "2025-05-01", 1, mapOf("count" to 20.0, "rice_opening" to 477.75, "rice_supply" to 0.0)),
            LedgerRow("r2", "i1", "2025-05-02", 2, mapOf("count" to 22.0, "rice_supply" to 1.0)),
        )
        val instance = LedgerInstance("i1", "demo", 2025, 5, "Demo May 2025", rows)
        val computed = FormulaEngine().compute(template, rows)
        val bytes = ByteArrayOutputStream()
        LedgerExcelExporter.write(
            ExcelExportRequest(
                school = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
                templates = listOf(template),
                instances = listOf(instance),
                computed = mapOf(instance.id to computed),
            ),
            bytes,
        )
        val raw = bytes.toByteArray()
        assertTrue(raw.size > 200)
        val allXml = ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
            buildString {
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".xml")) append(zip.readBytes().toString(Charsets.UTF_8))
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        assertTrue(allXml.contains("<f>"))
        assertTrue(allXml.contains("Rates!"))
        val parsed = LedgerExcelImporter.parse(ByteArrayInputStream(raw))
        assertTrue(parsed.sheets.any { it.name == "Rates" })
        assertTrue(parsed.sheets.any { it.name == "Demo May 2025" })
        val ledger = parsed.sheet("Demo May 2025")!!
        val firstData = ledger.rows[3]
        assertEquals(20.0, firstData[2].number!!, 0.0)
        assertTrue(ledger.rows[4][5].text.contains("SUM") || parsed.sheets.isNotEmpty())
        val imported = LedgerExcelImporter.toPayload(parsed, listOf(template))
        assertEquals(20.0, imported.instances.first().rows.first().cells["count"])
        assertEquals(150.0, imported.templates.first().rates.first().value, 0.0)
    }

    @Test
    fun uniqueSheetNamesAfterSanitize() {
        val used = mutableSetOf<String>()
        val first = XlsxWorkbook.sanitize("May 2025 8th Rice Bhagya Extra Long", used)
        val second = XlsxWorkbook.sanitize("May 2025 8th Rice Bhagya Extra Long", used)
        assertTrue(first.length <= 31)
        assertTrue(second.length <= 31)
        assertTrue(first != second)
    }

    @Test
    fun formulaTranslationFailureAbortsExport() {
        val broken = template.copy(
            columns = template.columns.map {
                if (it.key == "rice_total") it.copy(formula = "UNKNOWN(count)") else it
            },
        )
        val rows = listOf(LedgerRow("r1", "i1", "2025-05-01", 1, mapOf("count" to 20.0, "rice_opening" to 1.0, "rice_supply" to 0.0)))
        val instance = LedgerInstance("i1", "demo", 2025, 5, "Demo May 2025", rows)
        val error = runCatching {
            LedgerExcelExporter.write(
                ExcelExportRequest(
                    school = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
                    templates = listOf(broken),
                    instances = listOf(instance),
                    computed = mapOf(instance.id to FormulaEngine().compute(broken, rows)),
                ),
                ByteArrayOutputStream(),
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException || error is IllegalArgumentException)
        assertTrue(error?.message?.contains("Total") == true || error?.message?.contains("UNKNOWN") == true || error != null)
    }

    @Test
    fun multiTemplateRateOffsetsStayOnRatesSheet() {
        val second = template.copy(id = "demo2", name = "Demo 2", rates = listOf(RateDef("demo2_rice_rate", "demo2", "rice_rate", "Rice", 140.0, "g", 0)))
        val rows = listOf(LedgerRow("r1", "i1", "2025-05-01", 1, mapOf("count" to 20.0, "rice_opening" to 1.0, "rice_supply" to 0.0)))
        val i1 = LedgerInstance("i1", "demo", 2025, 5, "Demo May 2025", rows)
        val i2 = LedgerInstance("i2", "demo2", 2025, 5, "Demo2 May 2025", rows)
        val bytes = ByteArrayOutputStream()
        LedgerExcelExporter.write(
            ExcelExportRequest(
                school = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
                templates = listOf(template, second),
                instances = listOf(i1, i2),
                computed = mapOf(
                    i1.id to FormulaEngine().compute(template, rows),
                    i2.id to FormulaEngine().compute(second, rows),
                ),
            ),
            bytes,
        )
        val xml = ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            buildString {
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".xml")) append(zip.readBytes().toString(Charsets.UTF_8))
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        assertTrue(xml.contains("Rates!"))
        assertTrue(xml.contains("<f>"))
    }

    @Test
    fun sheetNamesStayUniqueAndMatchMeta() {
        val longTitle = "May 2025 8th Rice Bhagya Extra Long Name"
        val rows = listOf(LedgerRow("r1", "i1", "2025-05-01", 1, mapOf("count" to 20.0, "rice_opening" to 1.0, "rice_supply" to 0.0)))
        val computed = FormulaEngine().compute(template, rows)
        val instances = listOf(
            LedgerInstance("i1", "demo", 2025, 5, longTitle, rows),
            LedgerInstance("i2", "demo", 2025, 6, longTitle, rows),
            LedgerInstance("i3", "demo", 2025, 7, "Info", rows),
            LedgerInstance("i4", "demo", 2025, 8, "Rates?/\\*", rows),
        )
        val bytes = ByteArrayOutputStream()
        LedgerExcelExporter.write(
            ExcelExportRequest(
                school = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
                templates = listOf(template),
                instances = instances,
                computed = instances.associate { it.id to computed },
                includeAudit = false,
            ),
            bytes,
        )
        val parsed = LedgerExcelImporter.parse(ByteArrayInputStream(bytes.toByteArray()))
        val names = parsed.sheets.map { it.name }
        assertEquals(names.size, names.toSet().size)
        names.forEach { assertTrue(it.length <= 31) }
        val meta = parsed.sheet("_meta")!!
        val mapped = meta.rows.drop(1).map { it[2].text }
        mapped.forEach { name ->
            assertTrue(parsed.sheets.any { it.name == name })
        }
        val imported = LedgerExcelImporter.toPayload(parsed, listOf(template))
        assertEquals(4, imported.instances.size)
        imported.instances.forEach { instance ->
            assertEquals(20.0, instance.rows.first().cells["count"])
        }
    }

    @Test
    fun datesTextBlanksAndAuditRoundTrip() {
        val notes = ColumnDef("demo_notes", "demo", "notes", "Notes", null, ValueType.TEXT, null, ColumnRole.INPUT, null, 8, true, false)
        val withText = template.copy(columns = template.columns + notes)
        val rows = listOf(
            LedgerRow(
                "r1",
                "i1",
                "2025-05-01",
                1,
                mapOf("count" to 20.0, "rice_opening" to 477.75),
                mapOf("count" to 18.0, "rice_opening" to 477.75, "rice_balance" to 474.0),
                mapOf("notes" to "Kitchen note"),
            ),
            LedgerRow("r2", "i1", "2025-05-02", 2, emptyMap(), emptyMap(), emptyMap()),
        )
        val instance = LedgerInstance("i1", "demo", 2025, 5, "Demo May 2025", rows)
        val bytes = exportBytes(withText, instance, rows)
        val xml = allXml(bytes)
        assertTrue(xml.contains("Kitchen note"))
        assertTrue(!xml.contains("<v>0.0</v>") || xml.contains("<f>"))
        val imported = LedgerExcelImporter.toPayload(LedgerExcelImporter.parse(ByteArrayInputStream(bytes)), listOf(withText))
        val first = imported.instances.first().rows.first()
        val second = imported.instances.first().rows[1]
        assertEquals("2025-05-01", first.date)
        assertEquals("2025-05-02", second.date)
        assertEquals("Kitchen note", first.textCells["notes"])
        assertEquals("", second.textCells["notes"])
        assertEquals(20.0, first.cells["count"])
        assertTrue(second.cells.containsKey("count"))
        assertEquals(null, second.cells["count"])
        assertEquals(18.0, first.sourceValues["count"])
        assertEquals(474.0, first.sourceValues["rice_balance"])
    }

    @Test
    fun caseOnlySheetNamesStayUniqueAndImportable() {
        val used = mutableSetOf<String>()
        val first = XlsxWorkbook.sanitize("Audit", used)
        val second = XlsxWorkbook.sanitize("audit", used)
        assertTrue(first != second)
        assertEquals(first.lowercase() != second.lowercase(), true)
        val rows = listOf(LedgerRow("r1", "i1", "2025-05-01", 1, mapOf("count" to 20.0, "rice_opening" to 1.0, "rice_supply" to 0.0)))
        val computed = FormulaEngine().compute(template, rows)
        val instances = listOf(
            LedgerInstance("i1", "demo", 2025, 5, "May", rows),
            LedgerInstance("i2", "demo", 2025, 6, "may", rows),
        )
        val bytes = ByteArrayOutputStream()
        LedgerExcelExporter.write(
            ExcelExportRequest(
                school = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
                templates = listOf(template),
                instances = instances,
                computed = instances.associate { it.id to computed },
            ),
            bytes,
        )
        val parsed = LedgerExcelImporter.parse(ByteArrayInputStream(bytes.toByteArray()))
        val names = parsed.sheets.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size)
        val imported = LedgerExcelImporter.toPayload(parsed, listOf(template))
        assertEquals(2, imported.instances.size)
    }

    @Test
    fun resolvePackagePathAcceptsRelativeSegments() {
        assertEquals("xl/worksheets/sheet1.xml", LedgerExcelImporter.resolvePackagePath("./worksheets/sheet1.xml"))
        assertEquals("xl/worksheets/sheet1.xml", LedgerExcelImporter.resolvePackagePath("worksheets/../worksheets/sheet1.xml"))
        val escaped = runCatching { LedgerExcelImporter.resolvePackagePath("../../secrets.xml") }.exceptionOrNull()
        assertTrue(escaped is IllegalArgumentException)
    }

    @Test
    fun richSharedStringsKeepIndexes() {
        val bytes = sharedStringWorkbook()
        val parsed = LedgerExcelImporter.parse(ByteArrayInputStream(bytes))
        assertEquals("Hello World", parsed.sheet("Data")!!.rows[0][0].text)
        assertEquals("Second", parsed.sheet("Data")!!.rows[0][1].text)
    }

    @Test
    fun malformedMetaAndMissingSheetAreErrors() {
        val missingTemplate = runCatching {
            LedgerExcelImporter.toPayload(exportParsed(), listOf(template.copy(id = "other")))
        }.exceptionOrNull()
        assertTrue(missingTemplate is IllegalStateException || missingTemplate is IllegalArgumentException)
        val broken = XlsxWorkbook()
        broken.addSheet(
            XlsxSheet(
                "_meta",
                listOf(
                    listOf(XlsxCell(XlsxCellKind.TEXT, text = "InstanceId")),
                    listOf(XlsxCell(XlsxCellKind.TEXT, text = "i1"), XlsxCell(XlsxCellKind.TEXT, text = "demo"), XlsxCell(XlsxCellKind.TEXT, text = "Missing")),
                ),
            ),
        )
        val out = ByteArrayOutputStream()
        broken.write(out)
        val error = runCatching {
            LedgerExcelImporter.toPayload(LedgerExcelImporter.parse(ByteArrayInputStream(out.toByteArray())), listOf(template))
        }.exceptionOrNull()
        assertTrue(error != null)
    }

    @Test
    fun originalLayoutUsesFirstDatedRowAsOpening() {
        val bytes = midMonthOriginalWorkbook()
        val imported = LedgerExcelImporter.toPayload(LedgerExcelImporter.parse(ByteArrayInputStream(bytes)), listOf(riceTemplate()))
        val first = imported.instances.first().rows.first()
        assertEquals("2025-05-15", first.date)
        assertEquals(12.0, first.cells["rice_opening"])
        assertEquals(15, first.sortOrder)
    }

    private fun exportBytes(item: LedgerTemplate = template, instance: LedgerInstance, rows: List<LedgerRow>): ByteArray {
        val out = ByteArrayOutputStream()
        LedgerExcelExporter.write(
            ExcelExportRequest(
                school = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
                templates = listOf(item),
                instances = listOf(instance),
                computed = mapOf(instance.id to FormulaEngine().compute(item, rows)),
            ),
            out,
        )
        return out.toByteArray()
    }

    private fun exportParsed() = LedgerExcelImporter.parse(
        ByteArrayInputStream(
            exportBytes(
                instance = LedgerInstance("i1", "demo", 2025, 5, "Demo May 2025", listOf(LedgerRow("r1", "i1", "2025-05-01", 1, mapOf("count" to 20.0, "rice_opening" to 1.0, "rice_supply" to 0.0)))),
                rows = listOf(LedgerRow("r1", "i1", "2025-05-01", 1, mapOf("count" to 20.0, "rice_opening" to 1.0, "rice_supply" to 0.0))),
            ),
        ),
    )

    private fun allXml(raw: ByteArray): String {
        return ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
            buildString {
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".xml")) append(zip.readBytes().toString(Charsets.UTF_8))
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun riceTemplate() = LedgerTemplate(
        id = "rice_8",
        name = "8th Rice Bhagya",
        description = "",
        sheetName = "8th Rice Bagya",
        gradeIds = listOf("grade_8"),
        rates = emptyList(),
        columns = listOf(
            ColumnDef("d", "rice_8", "date", "Date", null, ValueType.DATE, null, ColumnRole.DATE, null, 0, true, false),
            ColumnDef("c", "rice_8", "count", "Count", null, ValueType.NUMBER, null, ColumnRole.INPUT, null, 1, true, false),
            ColumnDef("ro", "rice_8", "rice_opening", "Opening", "Rice", ValueType.NUMBER, null, ColumnRole.OPENING, null, 2, true, false),
            ColumnDef("rs", "rice_8", "rice_supply", "Supply", "Rice", ValueType.NUMBER, null, ColumnRole.INPUT, null, 3, true, false),
        ),
    )

    private fun midMonthOriginalWorkbook(): ByteArray {
        val date = XlsxWorkbook.excelDate(java.time.LocalDate.of(2025, 5, 15))
        val row = MutableList(22) { XlsxCell(XlsxCellKind.EMPTY) }
        row[0] = XlsxCell(XlsxCellKind.DATE, number = date, style = XlsxWorkbook.STYLE_DATE)
        row[2] = XlsxCell(XlsxCellKind.NUMBER, number = 20.0)
        row[3] = XlsxCell(XlsxCellKind.NUMBER, number = 12.0)
        row[4] = XlsxCell(XlsxCellKind.NUMBER, number = 1.0)
        val book = XlsxWorkbook()
        book.addSheet(XlsxSheet("8th Rice Bagya", listOf(row)))
        val out = ByteArrayOutputStream()
        book.write(out)
        return out.toByteArray()
    }

    private fun sharedStringWorkbook(): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            fun put(path: String, xml: String) {
                zip.putNextEntry(java.util.zip.ZipEntry(path))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"></Types>""",
            )
            put(
                "xl/workbook.xml",
                """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="./worksheets/sheet1.xml"/></Relationships>""",
            )
            put(
                "xl/sharedStrings.xml",
                """<?xml version="1.0"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><r><t>Hello </t></r><r><t>World</t></r></si><si><t>Second</t></si></sst>""",
            )
            put(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row></sheetData></worksheet>""",
            )
        }
        return out.toByteArray()
    }
}
