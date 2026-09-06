package com.aksharadasoha.ledger.export

import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.CorrectionNote
import com.aksharadasoha.ledger.domain.Grade
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerRow
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.SchoolProfile
import com.aksharadasoha.ledger.domain.SeedPayload
import com.aksharadasoha.ledger.domain.ValueType
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class ExcelImportPreview(
    val school: String,
    val sheets: Int,
    val instances: Int,
    val rows: Int,
    val rates: Int,
    val recognized: Boolean,
    val addedInstances: Int = 0,
    val updatedInstances: Int = 0,
    val unchangedInstances: Int = 0,
    val preservedInstances: Int = 0,
    val wouldRemoveInstances: Int = 0,
    val conflicts: List<String> = emptyList(),
)

object LedgerExcelImporter {
    private val originalSheets = setOf(
        "8th Rice Bagya",
        "9th & 10 Rice Bagya",
        "Milk & Biscut",
        "Contengency",
        "Egg & Bannana & nutbar",
    )

    fun preview(input: InputStream, templates: List<LedgerTemplate>): ExcelImportPreview {
        val workbook = parse(input)
        val payload = toPayload(workbook, templates)
        return ExcelImportPreview(
            school = payload.school.name,
            sheets = workbook.sheets.size,
            instances = payload.instances.size,
            rows = payload.instances.sumOf { it.rows.size },
            rates = payload.templates.sumOf { it.rates.size },
            recognized = workbook.sheets.any { it.name == "_meta" || it.name == "Rates" || it.name in originalSheets },
        )
    }

    fun import(input: InputStream, templates: List<LedgerTemplate>, grades: List<Grade>, school: SchoolProfile, corrections: List<CorrectionNote>): SeedPayload {
        return toPayload(parse(input), templates, grades, school, corrections)
    }

    internal fun parse(input: InputStream): ParsedWorkbook {
        val files = mutableMapOf<String, String>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val workbookXml = files["xl/workbook.xml"] ?: error("Not a valid Excel workbook")
        val rels = parseRels(files["xl/_rels/workbook.xml.rels"].orEmpty())
        val shared = parseSharedStrings(files["xl/sharedStrings.xml"].orEmpty())
        val declared = sheetDeclarations(workbookXml)
        val sheets = declared.map { (name, rId) ->
            val target = rels[rId] ?: error("Missing workbook relationship for $name")
            val path = resolvePackagePath(target)
            val xml = files[path] ?: files.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
                ?: error("Missing worksheet $name")
            ParsedSheet(name, parseSheet(xml, shared))
        }
        return ParsedWorkbook(sheets)
    }

    internal fun toPayload(
        workbook: ParsedWorkbook,
        templates: List<LedgerTemplate>,
        grades: List<Grade> = emptyList(),
        school: SchoolProfile = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
        corrections: List<CorrectionNote> = emptyList(),
    ): SeedPayload {
        val info = workbook.sheet("Info")
        val schoolName = info?.cell(2, 2)?.text ?: school.name
        val program = info?.cell(3, 2)?.text ?: school.program
        val rates = workbook.sheet("Rates")
        val updatedTemplates = templates.map { template ->
            val nextRates = template.rates.map { rate ->
                val row = rates?.rows?.drop(1)?.firstOrNull { it.getOrNull(1)?.text == rate.key && (it.getOrNull(0)?.text == template.id || it.getOrNull(0)?.text.isNullOrBlank()) }
                val value = row?.getOrNull(3)?.number ?: rate.value
                rate.copy(value = value, label = row?.getOrNull(2)?.text ?: rate.label)
            }
            template.copy(rates = nextRates)
        }
        val meta = workbook.sheet("_meta")
        val auditSources = parseAuditSources(workbook)
        val instances = if (meta != null) {
            meta.rows.drop(1).mapNotNull { row ->
                if (row.all { it.text.isBlank() && it.number == null }) return@mapNotNull null
                val instanceId = row.getOrNull(0)?.text?.takeIf { it.isNotBlank() }
                    ?: error("A _meta row is missing InstanceId")
                val templateId = row.getOrNull(1)?.text?.takeIf { it.isNotBlank() }
                    ?: error("A _meta row is missing TemplateId")
                val sheetName = row.getOrNull(2)?.text?.takeIf { it.isNotBlank() }
                    ?: error("A _meta row is missing Sheet")
                val year = row.getOrNull(3)?.number?.toInt()
                    ?: row.getOrNull(3)?.text?.toIntOrNull()
                    ?: error("A _meta row is missing Year")
                val month = row.getOrNull(4)?.number?.toInt()
                    ?: row.getOrNull(4)?.text?.toIntOrNull()
                    ?: error("A _meta row is missing Month")
                val mapping = parseMapping(row.getOrNull(5)?.text.orEmpty())
                require(mapping.isNotEmpty()) { "A _meta row has no column mapping" }
                val template = updatedTemplates.firstOrNull { it.id == templateId }
                    ?: error("Workbook references missing ledger $templateId")
                val sheet = workbook.sheet(sheetName)
                    ?: error("Workbook is missing sheet $sheetName")
                instanceFromSheet(instanceId, template, year, month, sheet, mapping, auditSources)
            }
        } else {
            instancesFromOriginalWorkbook(workbook, updatedTemplates)
        }
        return SeedPayload(
            version = 1,
            school = SchoolProfile(schoolName, program),
            grades = grades,
            templates = updatedTemplates,
            instances = instances,
            corrections = corrections,
        )
    }

    private fun instanceFromSheet(
        instanceId: String,
        template: LedgerTemplate,
        year: Int,
        month: Int,
        sheet: ParsedSheet,
        mapping: Map<String, Int>,
        auditSources: Map<Triple<String, String, String>, Double?> = emptyMap(),
    ): LedgerInstance {
        val inputKeys = template.columns.filter { it.role == ColumnRole.INPUT || it.role == ColumnRole.OPENING }.map { it.key }.toSet()
        val textKeys = template.columns.filter { it.valueType == ValueType.TEXT }.map { it.key }.toSet()
        val rows = sheet.rows.drop(3).mapIndexedNotNull { index, row ->
            val dateCell = mapping["date"]?.let { row.getOrNull(it) }
            val date = parseExportedDate(dateCell) ?: return@mapIndexedNotNull null
            val cells = linkedMapOf<String, Double?>()
            val source = linkedMapOf<String, Double?>()
            val texts = linkedMapOf<String, String>()
            mapping.forEach { (key, colIndex) ->
                if (key == "date") return@forEach
                val cell = row.getOrNull(colIndex)
                if (key in textKeys) {
                    texts[key] = cell?.text.orEmpty()
                } else if (key in inputKeys) {
                    cells[key] = cell?.number
                    source[key] = cell?.number
                }
            }
            auditSources.filter { (identity, _) ->
                identity.second == date && (identity.first == instanceId || identity.first == sheet.name)
            }.forEach { (identity, value) -> source[identity.third] = value }
            LedgerRow("${instanceId}_$date", instanceId, date, index + 1, cells, source, texts)
        }
        return LedgerInstance(instanceId, template.id, year, month, sheet.name, rows)
    }

    private fun instancesFromOriginalWorkbook(workbook: ParsedWorkbook, templates: List<LedgerTemplate>): List<LedgerInstance> {
        return OriginalWorkbookMaps.layouts.mapNotNull { layout ->
            val template = templates.firstOrNull { it.id == layout.templateId } ?: return@mapNotNull null
            val sheet = workbook.sheets.firstOrNull { namesMatch(it.name, layout.sheetName) || namesMatch(it.name, template.sheetName) || namesMatch(it.name, template.name) }
                ?: return@mapNotNull null
            val datedRows = sheet.rows.mapNotNull { row ->
                val date = parseDateCell(row.getOrNull(0)) ?: return@mapNotNull null
                row to date
            }
            if (datedRows.isEmpty()) return@mapNotNull null
            val firstDate = datedRows.first().second
            val dated = datedRows.map { (row, date) -> layout.read(row, date == firstDate) to date }
            val first = dated.first().second
            val id = "${template.id}_${first.year}_${first.monthValue.toString().padStart(2, '0')}"
            LedgerInstance(
                id = id,
                templateId = template.id,
                year = first.year,
                month = first.monthValue,
                title = "${template.name} ${first.month.name.lowercase().replaceFirstChar { it.titlecase() }} ${first.year}",
                rows = dated.mapIndexed { index, (values, date) ->
                    LedgerRow("${id}_$date", id, date.toString(), date.dayOfMonth, values.cells, values.source, values.texts)
                },
            )
        }
    }

    private fun namesMatch(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        fun clean(value: String) = value.lowercase().replace(Regex("[^a-z0-9]"), "")
        return clean(left) == clean(right) || clean(left).contains(clean(right)) || clean(right).contains(clean(left))
    }

    private fun parseDateCell(cell: ParsedCell?): LocalDate? {
        if (cell == null) return null
        cell.number?.takeIf { it in 20000.0..60000.0 }?.let { return excelToDate(it) }
        cell.text.toDoubleOrNull()?.takeIf { it in 20000.0..60000.0 }?.let { return excelToDate(it) }
        return runCatching { LocalDate.parse(cell.text.take(10)) }.getOrNull()
    }

    private fun parseExportedDate(cell: ParsedCell?): String? {
        return parseDateCell(cell)?.toString()
    }

    private fun parseAuditSources(workbook: ParsedWorkbook): Map<Triple<String, String, String>, Double?> {
        val audit = workbook.sheet("Audit") ?: return emptyMap()
        val header = audit.rows.firstOrNull()?.map { it.text.trim() }.orEmpty()
        if (header.isEmpty()) return emptyMap()
        val instanceIdx = header.indexOfFirst { it.equals("InstanceId", true) }.takeIf { it >= 0 } ?: -1
        val dateIdx = header.indexOfFirst { it.equals("Date", true) }.takeIf { it >= 0 } ?: 1
        val columnIdx = header.indexOfFirst { it.equals("Column", true) }.takeIf { it >= 0 } ?: 2
        val importedIdx = header.indexOfFirst { it.equals("Imported", true) }.takeIf { it >= 0 } ?: 3
        val titleIdx = header.indexOfFirst { it.equals("Ledger", true) }
        val result = mutableMapOf<Triple<String, String, String>, Double?>()
        audit.rows.drop(1).forEach { row ->
            val date = parseExportedDate(row.getOrNull(dateIdx)) ?: return@forEach
            val column = row.getOrNull(columnIdx)?.text?.takeIf { it.isNotBlank() } ?: return@forEach
            val imported = row.getOrNull(importedIdx)?.number
            val instanceId = row.getOrNull(instanceIdx)?.text?.takeIf { it.isNotBlank() }
                ?: row.getOrNull(titleIdx)?.text?.takeIf { it.isNotBlank() }
                ?: return@forEach
            result[Triple(instanceId, date, column)] = imported
        }
        return result
    }

    private fun parseMapping(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").associate { part ->
            val pieces = part.split("=")
            require(pieces.size == 2 && pieces[0].isNotBlank() && pieces[1].isNotBlank()) {
                "A _meta row has an invalid column mapping"
            }
            pieces[0].trim() to (letterToIndex(pieces[1].trim()) - 1)
        }
    }

    private fun letterToIndex(letter: String): Int {
        var n = 0
        letter.uppercase().forEach { n = n * 26 + (it - 'A' + 1) }
        return n
    }

    private fun excelToDate(serial: Double): LocalDate {
        return LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
    }

    private fun sheetDeclarations(xml: String): List<Pair<String, String>> {
        val names = mutableListOf<Pair<String, String>>()
        val parser = parser(xml)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name").orEmpty()
                val rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    ?: parser.getAttributeValue(null, "id").orEmpty()
                names += name to rId
            }
            event = parser.next()
        }
        return names
    }

    private fun parseRels(xml: String): Map<String, String> {
        if (xml.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val parser = parser(xml)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                result[parser.getAttributeValue(null, "Id").orEmpty()] = parser.getAttributeValue(null, "Target").orEmpty()
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSharedStrings(xml: String): List<String> {
        if (xml.isBlank()) return emptyList()
        val values = mutableListOf<String>()
        val parser = parser(xml)
        var event = parser.eventType
        var inText = false
        val run = StringBuilder()
        val item = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> item.clear()
                    "t" -> {
                        inText = true
                        run.clear()
                    }
                }
                XmlPullParser.TEXT -> if (inText) run.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> {
                        item.append(run)
                        inText = false
                    }
                    "si" -> values += item.toString()
                }
            }
            event = parser.next()
        }
        return values
    }

    internal fun resolvePackagePath(target: String): String {
        val raw = when {
            target.startsWith("/") -> target.drop(1)
            target.startsWith("xl/") -> target
            else -> "xl/$target"
        }
        val parts = mutableListOf<String>()
        raw.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    require(parts.isNotEmpty()) { "Workbook relationship leaves the package" }
                    parts.removeAt(parts.lastIndex)
                }
                else -> parts += segment
            }
        }
        val path = parts.joinToString("/")
        require(path.startsWith("xl/") && ".." !in parts) { "Workbook relationship leaves the package" }
        return path
    }

    private fun parseSheet(xml: String, shared: List<String>): List<List<ParsedCell>> {
        val rows = mutableMapOf<Int, MutableMap<Int, ParsedCell>>()
        val parser = parser(xml)
        var event = parser.eventType
        var row = 0
        var col = 0
        var type = ""
        var inValue = false
        var inText = false
        var inFormula = false
        var buffer = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r").orEmpty()
                        val parsed = parseRef(ref)
                        row = parsed.first
                        col = parsed.second
                        type = parser.getAttributeValue(null, "t").orEmpty()
                        buffer = StringBuilder()
                    }
                    "v" -> inValue = true
                    "t" -> inText = true
                    "f" -> inFormula = true
                }
                XmlPullParser.TEXT -> if (inValue || inText || inFormula) buffer.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> {
                        val raw = buffer.toString()
                        val resolved = if (type == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                        val number = if (type != "inlineStr" && type != "s" && type != "str") raw.toDoubleOrNull() else resolved.toDoubleOrNull()
                        rows.getOrPut(row) { mutableMapOf() }[col] = ParsedCell(resolved, number)
                        inValue = false
                        inText = false
                        buffer = StringBuilder()
                    }
                    "f" -> {
                        val existing = rows.getOrPut(row) { mutableMapOf() }[col]
                        rows[row]!![col] = ParsedCell(buffer.toString().ifBlank { existing?.text.orEmpty() }, existing?.number)
                        inFormula = false
                        buffer = StringBuilder()
                    }
                }
            }
            event = parser.next()
        }
        val maxRow = rows.keys.maxOrNull() ?: 0
        return (1..maxRow).map { r ->
            val cols = rows[r].orEmpty()
            val maxCol = cols.keys.maxOrNull() ?: 0
            (1..maxCol).map { c -> cols[c] ?: ParsedCell("", null) }
        }
    }

    private fun parseRef(ref: String): Pair<Int, Int> {
        val letters = ref.takeWhile { it.isLetter() }
        val number = ref.dropWhile { it.isLetter() }.toIntOrNull() ?: 1
        return number to letterToIndex(letters)
    }

    private fun parser(xml: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newPullParser().apply { setInput(xml.reader()) }
    }

    data class ParsedWorkbook(val sheets: List<ParsedSheet>) {
        fun sheet(name: String) = sheets.firstOrNull { it.name == name }
            ?: sheets.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    data class ParsedSheet(val name: String, val rows: List<List<ParsedCell>>) {
        fun cell(row: Int, col: Int) = rows.getOrNull(row - 1)?.getOrNull(col - 1)
    }

    data class ParsedCell(val text: String, val number: Double?)
}

internal data class OriginalRowValues(
    val cells: Map<String, Double?>,
    val source: Map<String, Double?>,
    val texts: Map<String, String> = emptyMap(),
)

internal data class OriginalLayout(
    val sheetName: String,
    val templateId: String,
    val read: (List<LedgerExcelImporter.ParsedCell>, Boolean) -> OriginalRowValues,
)

internal object OriginalWorkbookMaps {
    private fun num(row: List<LedgerExcelImporter.ParsedCell>, col1: Int): Double? = row.getOrNull(col1 - 1)?.number

    private fun commodityInputs(row: List<LedgerExcelImporter.ParsedCell>, prefix: String, startCol: Int, first: Boolean): Map<String, Double?> {
        val opening = num(row, startCol)
        val supply = num(row, startCol + 1)
        val cells = mutableMapOf<String, Double?>( "${prefix}_supply" to (supply ?: 0.0) )
        if (first) cells["${prefix}_opening"] = opening ?: 0.0
        return cells
    }

    private fun commoditySource(row: List<LedgerExcelImporter.ParsedCell>, prefix: String, startCol: Int): Map<String, Double?> {
        val keys = listOf("${prefix}_opening", "${prefix}_supply", "${prefix}_total", "${prefix}_expenditure", "${prefix}_balance")
        return keys.mapIndexed { index, key -> key to num(row, startCol + index) }.toMap()
    }

    val layouts = listOf(
        OriginalLayout("8th Rice Bagya", "rice_8") { row, first ->
            val cells = mutableMapOf<String, Double?>("count" to num(row, 3))
            val source = mutableMapOf<String, Double?>("count" to cells["count"])
            listOf("rice" to 4, "dal" to 9, "oil" to 14, "wheat" to 19).forEach { (prefix, col) ->
                cells += commodityInputs(row, prefix, col, first)
                source += commoditySource(row, prefix, col)
            }
            OriginalRowValues(cells, source)
        },
        OriginalLayout("9th & 10 Rice Bagya", "rice_9_10") { row, first ->
            val cells = mutableMapOf<String, Double?>("count" to num(row, 3))
            val source = mutableMapOf<String, Double?>("count" to cells["count"])
            listOf("rice" to 4, "dal" to 9, "oil" to 14).forEach { (prefix, col) ->
                cells += commodityInputs(row, prefix, col, first)
                source += commoditySource(row, prefix, col)
            }
            OriginalRowValues(cells, source)
        },
        OriginalLayout("Milk & Biscut", "milk_biscuit") { row, first ->
            val cells = mutableMapOf<String, Double?>("count" to num(row, 3))
            val source = mutableMapOf<String, Double?>("count" to cells["count"])
            listOf("milk" to 4, "malt" to 9).forEach { (prefix, col) ->
                cells += commodityInputs(row, prefix, col, first)
                source += commoditySource(row, prefix, col)
            }
            OriginalRowValues(cells, source)
        },
        OriginalLayout("Contengency", "contingency") { row, first ->
            val cells = mutableMapOf(
                "count_8" to num(row, 3),
                "count_9" to num(row, 9),
                "count_10" to num(row, 10),
            )
            val source = cells.toMutableMap()
            source["count_9_10"] = num(row, 11)
            source["count_all"] = num(row, 17)
            cells += commodityInputs(row, "cont_8", 4, first)
            cells += commodityInputs(row, "cont_9_10", 12, first)
            cells += commodityInputs(row, "sugar", 18, first)
            source += commoditySource(row, "cont_8", 4)
            source += commoditySource(row, "cont_9_10", 12)
            source += commoditySource(row, "sugar", 18)
            OriginalRowValues(cells, source)
        },
        OriginalLayout("Egg & Bannana & nutbar", "egg_banana") { row, first ->
            val cells = mutableMapOf(
                "egg_8" to num(row, 3),
                "banana_8" to num(row, 4),
                "egg_9" to num(row, 6),
                "banana_9" to num(row, 7),
                "egg_10" to num(row, 9),
                "banana_10" to num(row, 10),
                "cash_supply" to (num(row, 18) ?: 0.0),
            )
            if (first) cells["cash_opening"] = num(row, 17) ?: 0.0
            val source = mutableMapOf(
                "egg_8" to cells["egg_8"],
                "banana_8" to cells["banana_8"],
                "egg_9" to cells["egg_9"],
                "banana_9" to cells["banana_9"],
                "egg_10" to cells["egg_10"],
                "banana_10" to cells["banana_10"],
                "total_8" to num(row, 5),
                "total_9" to num(row, 8),
                "total_10" to num(row, 11),
                "egg_total" to num(row, 12),
                "banana_total" to num(row, 13),
                "combo_total" to num(row, 14),
                "egg_cont" to num(row, 15),
                "banana_cont" to num(row, 16),
                "cash_opening" to num(row, 17),
                "cash_supply" to num(row, 18),
                "cash_total" to num(row, 19),
                "cash_expenditure" to num(row, 20),
                "cash_balance" to num(row, 21),
            )
            OriginalRowValues(cells, source)
        },
    )
}
