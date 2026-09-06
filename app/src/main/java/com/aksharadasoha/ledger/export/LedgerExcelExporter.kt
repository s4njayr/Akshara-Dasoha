package com.aksharadasoha.ledger.export

import com.aksharadasoha.ledger.domain.ColumnDef
import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.ComputedRow
import com.aksharadasoha.ledger.domain.CorrectionNote
import com.aksharadasoha.ledger.domain.ExcelFormulaContext
import com.aksharadasoha.ledger.domain.ExcelFormulaTranslator
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.MonthlyReport
import com.aksharadasoha.ledger.domain.SchoolProfile
import com.aksharadasoha.ledger.domain.ValueType
import java.io.OutputStream
import java.time.LocalDate

data class ExcelExportRequest(
    val school: SchoolProfile,
    val templates: List<LedgerTemplate>,
    val instances: List<LedgerInstance>,
    val computed: Map<String, List<ComputedRow>>,
    val corrections: List<CorrectionNote> = emptyList(),
    val includeAudit: Boolean = true,
    val reports: List<MonthlyReport> = emptyList(),
)

object LedgerExcelExporter {
    private val translator = ExcelFormulaTranslator()
    const val FIRST_DATA_ROW = 4

    fun write(request: ExcelExportRequest, out: OutputStream) {
        val book = XlsxWorkbook()
        val used = mutableSetOf<String>()
        listOf("Info", "Rates", "Audit", "Corrections", "Reports", "_meta").forEach { reserved ->
            XlsxWorkbook.sanitize(reserved, used)
        }
        val sheetNames = request.instances.associate { instance ->
            instance.id to XlsxWorkbook.sanitize(instance.title, used)
        }
        book.addSheet(infoSheet(request))
        book.addSheet(ratesSheet(request))
        request.instances.forEach { instance ->
            val template = request.templates.first { it.id == instance.templateId }
            val rows = request.computed[instance.id].orEmpty()
            book.addSheet(ledgerSheet(request.school, template, instance, rows, request.templates, sheetNames.getValue(instance.id)))
        }
        if (request.includeAudit) book.addSheet(auditSheet(request))
        if (request.corrections.isNotEmpty()) book.addSheet(correctionsSheet(request.corrections))
        if (request.reports.isNotEmpty()) book.addSheet(reportSheet(request.reports))
        book.addSheet(metaSheet(request, sheetNames))
        book.write(out)
    }

    private fun infoSheet(request: ExcelExportRequest): XlsxSheet {
        val rows = listOf(
            listOf(title("Akshara Dasoha Ledger")),
            listOf(text("School"), text(request.school.name)),
            listOf(text("Program"), text(request.school.program)),
            listOf(text("Exported"), text(LocalDate.now().toString())),
            listOf(text("Ledgers"), number(request.instances.size.toDouble())),
            listOf(text("Edit the yellow input cells. Green cells contain Excel formulas that recalculate on this computer.")),
        )
        return XlsxSheet("Info", rows, columnWidths = listOf(18, 60))
    }

    private fun ratesSheet(request: ExcelExportRequest): XlsxSheet {
        val header = listOf(header("Template"), header("Key"), header("Label"), header("Value"), header("Unit"))
        val body = request.templates.flatMap { template ->
            template.rates.map { rate ->
                listOf(text(template.id), text(rate.key), text(rate.label), number(rate.value), text(rate.unit.orEmpty()))
            }
        }
        return XlsxSheet("Rates", listOf(header) + body, freezeRows = 1, autoFilter = "A1:E${body.size + 1}", columnWidths = listOf(18, 18, 24, 12, 10))
    }

    private fun ledgerSheet(
        school: SchoolProfile,
        template: LedgerTemplate,
        instance: LedgerInstance,
        rows: List<ComputedRow>,
        allTemplates: List<LedgerTemplate>,
        sheetName: String,
    ): XlsxSheet {
        val columns = template.columns.filter { it.visible }
        val letters = columns.mapIndexed { index, column -> column.key to XlsxWorkbook.colName(index + 1) }.toMap()
        val actualRateRefs = rateLookup(template, allTemplates)
        val titleRow = listOf(title("${school.name} — ${instance.title}")) + columns.drop(1).map { empty() }
        val groupRow = columns.map { header(it.groupLabel.orEmpty()) }
        val labelRow = columns.map { header(listOfNotNull(it.label, it.unit).joinToString(" ")) }
        val data = rows.mapIndexed { index, computed ->
            val excelRow = FIRST_DATA_ROW + index
            columns.map { column ->
                cellFor(column, computed, excelRow, letters, actualRateRefs)
            }
        }
        val lastCol = XlsxWorkbook.colName(columns.size.coerceAtLeast(1))
        return XlsxSheet(
            name = sheetName,
            rows = listOf(titleRow, groupRow, labelRow) + data,
            freezeRows = 3,
            freezeCols = 1,
            columnWidths = columns.map { 14 },
            autoFilter = "A3:${lastCol}${3 + data.size}",
        )
    }

    private fun rateLookup(template: LedgerTemplate, allTemplates: List<LedgerTemplate>): Map<String, String> {
        var row = 2
        allTemplates.forEach { item ->
            if (item.id == template.id) {
                return item.rates.mapIndexed { index, rate ->
                    rate.key to "Rates!\$D\$${row + index}"
                }.toMap()
            }
            row += item.rates.size
        }
        return template.rates.associate { it.key to it.value.toString() }
    }

    private fun cellFor(
        column: ColumnDef,
        computed: ComputedRow,
        excelRow: Int,
        letters: Map<String, String>,
        rateRefs: Map<String, String>,
    ): XlsxCell {
        val cell = computed.cells[column.key]
        val cached = cell?.number
        val first = computed.row.sortOrder == 1
        val editable = column.role == ColumnRole.INPUT || (column.role == ColumnRole.OPENING && first)
        return when {
            column.valueType == ValueType.DATE || column.role == ColumnRole.DATE -> {
                val date = runCatching { LocalDate.parse(computed.row.date) }.getOrNull()
                if (date != null) XlsxCell(XlsxCellKind.DATE, number = XlsxWorkbook.excelDate(date), style = XlsxWorkbook.STYLE_DATE)
                else text(computed.row.date, XlsxWorkbook.STYLE_COMPUTED)
            }
            column.valueType == ValueType.WEEKDAY -> {
                val formula = """TEXT(${letters["date"] ?: "A"}$excelRow,"dddd")"""
                XlsxCell(XlsxCellKind.FORMULA, formula = formula, text = cell?.text, style = XlsxWorkbook.STYLE_COMPUTED)
            }
            column.valueType == ValueType.TEXT && editable -> {
                val value = cell?.text ?: computed.row.textCells[column.key]
                if (value.isNullOrBlank()) empty().copy(style = XlsxWorkbook.STYLE_INPUT)
                else text(value, XlsxWorkbook.STYLE_INPUT)
            }
            editable -> {
                if (column.key !in computed.row.cells) empty().copy(style = XlsxWorkbook.STYLE_INPUT_NUMBER)
                else XlsxCell(XlsxCellKind.NUMBER, number = cached ?: 0.0, style = XlsxWorkbook.STYLE_INPUT_NUMBER)
            }
            !column.formula.isNullOrBlank() -> {
                val context = ExcelFormulaContext(letters, rateRefs, excelRow, FIRST_DATA_ROW, column.key)
                val formula = runCatching { translator.translate(column.formula, context) }
                    .getOrElse { error("Could not convert formula for ${column.label}: ${it.message}") }
                XlsxCell(XlsxCellKind.FORMULA, formula = formula, number = cached, style = XlsxWorkbook.STYLE_COMPUTED)
            }
            cell?.text != null -> text(cell.text, XlsxWorkbook.STYLE_COMPUTED)
            else -> XlsxCell(XlsxCellKind.NUMBER, number = cached ?: 0.0, style = XlsxWorkbook.STYLE_COMPUTED)
        }
    }

    private fun auditSheet(request: ExcelExportRequest): XlsxSheet {
        val header = listOf(header("InstanceId"), header("Date"), header("Column"), header("Imported"), header("Calculated"), header("Difference"), header("Ledger"))
        val rows = request.instances.flatMap { instance ->
            request.computed[instance.id].orEmpty().flatMap { row ->
                row.cells.values.filter { it.sourceNumber != null }.map { cell ->
                    val calc = cell.number
                    val imported = cell.sourceNumber
                    listOf(
                        text(instance.id),
                        text(row.row.date),
                        text(cell.key),
                        number(imported),
                        number(calc),
                        number(if (imported != null && calc != null) calc - imported else null),
                        text(instance.title),
                    )
                }
            }
        }
        return XlsxSheet("Audit", listOf(header) + rows, freezeRows = 1, autoFilter = "A1:G${rows.size + 1}", columnWidths = listOf(28, 14, 18, 12, 12, 12, 28))
    }

    private fun correctionsSheet(notes: List<CorrectionNote>): XlsxSheet {
        val header = listOf(header("Item"), header("Workbook"), header("App"), header("Reason"))
        val rows = notes.map { listOf(text(it.item), number(it.workbook), number(it.app), text(it.reason)) }
        return XlsxSheet("Corrections", listOf(header) + rows, freezeRows = 1, columnWidths = listOf(20, 12, 12, 60))
    }

    private fun reportSheet(reports: List<MonthlyReport>): XlsxSheet {
        val header = listOf(header("Ledger"), header("Period"), header("Children"), header("Item"), header("Opening"), header("Supply"), header("Expenditure"), header("Closing"))
        val rows = reports.flatMap { report ->
            report.items.map { item ->
                listOf(
                    text(report.templateName),
                    text("${report.year}-${report.month.toString().padStart(2, '0')}"),
                    number(report.childrenServed),
                    text(item.label),
                    number(item.opening),
                    number(item.supply),
                    number(item.expenditure),
                    number(item.closing),
                )
            }
        }
        return XlsxSheet("Reports", listOf(header) + rows, freezeRows = 1, columnWidths = listOf(22, 10, 12, 18, 12, 12, 14, 12))
    }

    private fun metaSheet(request: ExcelExportRequest, sheetNames: Map<String, String>): XlsxSheet {
        val header = listOf(header("InstanceId"), header("TemplateId"), header("Sheet"), header("Year"), header("Month"), header("Columns"))
        val rows = request.instances.map { instance ->
            val template = request.templates.first { it.id == instance.templateId }
            val columns = template.columns.filter { it.visible }.mapIndexed { index, column ->
                "${column.key}=${XlsxWorkbook.colName(index + 1)}"
            }.joinToString(",")
            listOf(text(instance.id), text(instance.templateId), text(sheetNames.getValue(instance.id)), number(instance.year.toDouble()), number(instance.month.toDouble()), text(columns))
        }
        return XlsxSheet("_meta", listOf(header) + rows, columnWidths = listOf(28, 18, 28, 8, 8, 80))
    }

    private fun title(value: String) = XlsxCell(XlsxCellKind.TEXT, text = value, style = XlsxWorkbook.STYLE_TITLE)
    private fun header(value: String) = XlsxCell(XlsxCellKind.TEXT, text = value, style = XlsxWorkbook.STYLE_HEADER)
    private fun text(value: String?, style: Int = XlsxWorkbook.STYLE_DEFAULT) = XlsxCell(XlsxCellKind.TEXT, text = value.orEmpty(), style = style)
    private fun number(value: Double?, style: Int = XlsxWorkbook.STYLE_DEFAULT) = if (value == null) XlsxCell(XlsxCellKind.EMPTY, style = style) else XlsxCell(XlsxCellKind.NUMBER, number = value, style = style)
    private fun empty() = XlsxCell(XlsxCellKind.EMPTY)
}
