package com.aksharadasoha.ledger.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

object ReportFormat {
    fun number(value: Double?): String {
        if (value == null || !value.isFinite()) return "—"
        val rounded = round(value * 100.0) / 100.0
        return if (abs(rounded - rounded.toLong()) < 1e-9) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded)
        }
    }

    fun quantity(value: Double?, unit: String?): String {
        val formatted = number(value)
        val suffix = unit?.trim().orEmpty()
        return if (formatted == "—" || suffix.isEmpty()) formatted else "$formatted $suffix"
    }
}

object ReportBuilder {
    fun monthly(template: LedgerTemplate, instance: LedgerInstance, rows: List<ComputedRow>): MonthlyReport {
        val first = rows.firstOrNull()
        val last = rows.lastOrNull()
        val children = rows.sumOf { it.cells["count"]?.number ?: 0.0 }
        val groups = template.columns
            .filter { it.visible && (it.groupLabel != null || it.key.endsWith("_balance") || it.key.endsWith("_total")) }
            .groupBy { it.groupLabel ?: it.label }
        val items = groups.map { (group, columns) ->
            val opening = columns.firstOrNull { it.role == ColumnRole.OPENING || it.key.endsWith("_opening") }
            val supply = columns.firstOrNull { it.key.endsWith("_supply") }
            val spend = columns.firstOrNull { it.key.endsWith("_expenditure") }
            val balance = columns.firstOrNull { it.key.endsWith("_balance") } ?: columns.last()
            ReportItem(
                key = balance.key,
                label = group,
                group = columns.first().groupLabel,
                opening = opening?.let { first?.cells?.get(it.key)?.number },
                supply = supply?.let { key -> rows.sumOf { it.cells[key.key]?.number ?: 0.0 } },
                expenditure = spend?.let { key -> rows.sumOf { it.cells[key.key]?.number ?: 0.0 } },
                closing = last?.cells?.get(balance.key)?.number,
                unit = balance.unit,
            )
        }
        return MonthlyReport(
            templateId = template.id,
            templateName = template.name,
            instanceId = instance.id,
            title = instance.title,
            year = instance.year,
            month = instance.month,
            days = rows.size,
            childrenServed = children,
            items = items,
            auditDifferences = rows.sumOf { row -> row.cells.values.count { it.auditDiffers } },
        )
    }
}
