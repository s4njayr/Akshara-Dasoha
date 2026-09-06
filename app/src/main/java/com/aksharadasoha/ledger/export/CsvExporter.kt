package com.aksharadasoha.ledger.export

import com.aksharadasoha.ledger.domain.ComputedRow
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.MonthlyReport

object CsvExporter {
    fun ledger(template: LedgerTemplate, instance: LedgerInstance, rows: List<ComputedRow>): String {
        val columns = template.columns.filter { it.visible }
        val header = columns.joinToString(",") { csv(listOfNotNull(it.groupLabel, it.label).joinToString(" / ")) }
        val body = rows.joinToString("\n") { row ->
            columns.joinToString(",") { column ->
                val cell = row.cells[column.key]
                csv(cell?.text ?: cell?.number?.toString() ?: cell?.error ?: "")
            }
        }
        return "${csv(instance.title)}\n$header\n$body\n"
    }

    fun reports(reports: List<MonthlyReport>): String {
        val header = "Ledger,Period,Children,Item,Opening,Supply,Expenditure,Closing"
        val body = reports.flatMap { report ->
            report.items.map { item ->
                listOf(
                    report.templateName,
                    "${report.year}-${report.month.toString().padStart(2, '0')}",
                    report.childrenServed.toString(),
                    item.label,
                    item.opening?.toString().orEmpty(),
                    item.supply?.toString().orEmpty(),
                    item.expenditure?.toString().orEmpty(),
                    item.closing?.toString().orEmpty(),
                ).joinToString(",") { csv(it) }
            }
        }.joinToString("\n")
        return "$header\n$body\n"
    }

    private fun csv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
