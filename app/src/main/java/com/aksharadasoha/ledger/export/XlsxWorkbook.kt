package com.aksharadasoha.ledger.export

import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class XlsxCellKind { NUMBER, TEXT, DATE, FORMULA, EMPTY }

data class XlsxCell(
    val kind: XlsxCellKind,
    val number: Double? = null,
    val text: String? = null,
    val formula: String? = null,
    val style: Int = 0,
)

data class XlsxSheet(
    val name: String,
    val rows: List<List<XlsxCell>>,
    val freezeRows: Int = 0,
    val freezeCols: Int = 0,
    val columnWidths: List<Int> = emptyList(),
    val autoFilter: String? = null,
)

class XlsxWorkbook {
    private val sheets = mutableListOf<XlsxSheet>()

    private val usedNames = mutableSetOf<String>()

    fun addSheet(sheet: XlsxSheet) {
        sheets += sheet.copy(name = sanitize(sheet.name, usedNames))
    }

    fun write(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            write(zip, "[Content_Types].xml", contentTypes())
            write(zip, "_rels/.rels", rootRels())
            write(zip, "docProps/app.xml", appProps())
            write(zip, "docProps/core.xml", coreProps())
            write(zip, "xl/workbook.xml", workbook())
            write(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            write(zip, "xl/styles.xml", styles())
            sheets.forEachIndexed { index, sheet ->
                write(zip, "xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet))
            }
        }
    }

    private fun write(zip: ZipOutputStream, path: String, xml: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(xml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes(): String {
        val extras = sheets.indices.joinToString("") { index ->
            """<Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
$extras
</Types>"""
    }

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

    private fun workbookRels(): String {
        val sheetRels = sheets.indices.joinToString("") { index ->
            """<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${index + 1}.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$sheetRels
<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun workbook(): String {
        val sheetNodes = sheets.mapIndexed { index, sheet ->
            """<sheet name="${escape(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>$sheetNodes</sheets>
</workbook>"""
    }

    private fun styles(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3">
<font><sz val="10"/><name val="Calibri"/></font>
<font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
<font><b/><sz val="14"/><color rgb="FF1B5E20"/><name val="Calibri"/></font>
</fonts>
<fills count="5">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF1B5E20"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFF8E1"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFF1F8E9"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border/>
<border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/></border>
</borders>
<cellStyleXfs count="1"><xf/></cellStyleXfs>
<cellXfs count="7">
<xf xfId="0"/>
<xf numFmtId="14" xfId="0" applyNumberFormat="1" applyBorder="1" borderId="1"/>
<xf xfId="0" applyFont="1" fontId="1" applyFill="1" fillId="2" applyBorder="1" borderId="1" applyAlignment="1"><alignment wrapText="1" horizontal="center"/></xf>
<xf xfId="0" applyFill="1" fillId="3" applyBorder="1" borderId="1"/>
<xf xfId="0" applyFill="1" fillId="4" applyBorder="1" borderId="1"/>
<xf numFmtId="2" xfId="0" applyNumberFormat="1" applyFill="1" fillId="3" applyBorder="1" borderId="1"/>
<xf xfId="0" applyFont="1" fontId="2"/>
</cellXfs>
</styleSheet>"""
    }

    private fun sheetXml(sheet: XlsxSheet): String {
        val lastCol = sheet.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val lastRow = sheet.rows.size.coerceAtLeast(1)
        val ref = "A1:${colName(lastCol)}$lastRow"
        val cols = sheet.columnWidths.mapIndexed { index, width ->
            """<col min="${index + 1}" max="${index + 1}" width="$width" customWidth="1"/>"""
        }.joinToString("")
        val sheetData = sheet.rows.mapIndexed { rowIndex, row ->
            val cells = row.mapIndexed { colIndex, cell -> cellXml(colIndex + 1, rowIndex + 1, cell) }.joinToString("")
            """<row r="${rowIndex + 1}">$cells</row>"""
        }.joinToString("")
        val freeze = if (sheet.freezeRows > 0 || sheet.freezeCols > 0) {
            val topLeft = "${colName(sheet.freezeCols + 1)}${sheet.freezeRows + 1}"
            """<pane xSplit="${sheet.freezeCols}" ySplit="${sheet.freezeRows}" topLeftCell="$topLeft" activePane="bottomRight" state="frozen"/>"""
        } else {
            ""
        }
        val filter = sheet.autoFilter?.let { """<autoFilter ref="$it"/>""" } ?: ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetViews><sheetView workbookViewId="0">$freeze</sheetView></sheetViews>
<cols>$cols</cols>
<sheetData>$sheetData</sheetData>
$filter
</worksheet>"""
    }

    private fun cellXml(col: Int, row: Int, cell: XlsxCell): String {
        val ref = "${colName(col)}$row"
        val style = if (cell.style != 0) """ s="${cell.style}"""" else ""
        return when (cell.kind) {
            XlsxCellKind.EMPTY -> """<c r="$ref"$style/>"""
            XlsxCellKind.TEXT -> """<c r="$ref"$style t="inlineStr"><is><t>${escape(cell.text.orEmpty())}</t></is></c>"""
            XlsxCellKind.NUMBER -> """<c r="$ref"$style><v>${cell.number ?: 0.0}</v></c>"""
            XlsxCellKind.DATE -> """<c r="$ref"$style><v>${cell.number ?: 0.0}</v></c>"""
            XlsxCellKind.FORMULA -> {
                val cached = cell.number?.let { "<v>$it</v>" } ?: ""
                """<c r="$ref"$style><f>${escape(cell.formula.orEmpty())}</f>$cached</c>"""
            }
        }
    }

    private fun appProps() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
<Application>Akshara Dasoha Ledger</Application>
</Properties>"""

    private fun coreProps(): String {
        val now = DateTimeFormatter.ISO_INSTANT.format(LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC))
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<dc:title>Akshara Dasoha Ledger</dc:title>
<dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created>
</cp:coreProperties>"""
    }

    companion object {
        const val STYLE_DEFAULT = 0
        const val STYLE_DATE = 1
        const val STYLE_HEADER = 2
        const val STYLE_INPUT = 3
        const val STYLE_COMPUTED = 4
        const val STYLE_INPUT_NUMBER = 5
        const val STYLE_TITLE = 6

        fun colName(index: Int): String {
            var n = index
            val chars = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                chars.insert(0, ('A' + rem))
                n = (n - 1) / 26
            }
            return chars.toString()
        }

        fun excelDate(date: LocalDate): Double {
            return (date.toEpochDay() - LocalDate.of(1899, 12, 30).toEpochDay()).toDouble()
        }

        fun sanitize(name: String, used: MutableSet<String> = mutableSetOf()): String {
            val cleaned = name.replace(Regex("""[:\\/?*\[\]]"""), " ").trim().ifBlank { "Sheet" }.take(31).trim()
            var candidate = cleaned
            var index = 2
            val usedLower = used.map { it.lowercase() }.toMutableSet()
            while (candidate.lowercase() in usedLower) {
                val suffix = "_$index"
                candidate = cleaned.take(31 - suffix.length) + suffix
                index++
            }
            used += candidate
            return candidate
        }

        fun escape(value: String): String {
            return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
        }
    }
}
