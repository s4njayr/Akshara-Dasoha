package com.aksharadasoha.ledger.ui.preview

import com.aksharadasoha.ledger.domain.AppSettings
import com.aksharadasoha.ledger.domain.ColumnDef
import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.ComputedCell
import com.aksharadasoha.ledger.domain.ComputedRow
import com.aksharadasoha.ledger.domain.CorrectionNote
import com.aksharadasoha.ledger.domain.Grade
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerRow
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.MonthlyReport
import com.aksharadasoha.ledger.domain.RateDef
import com.aksharadasoha.ledger.domain.ReportItem
import com.aksharadasoha.ledger.domain.SchoolProfile
import com.aksharadasoha.ledger.domain.ValueType
import com.aksharadasoha.ledger.ui.ConfigState
import com.aksharadasoha.ledger.ui.HomeState
import com.aksharadasoha.ledger.ui.LedgerUiState
import com.aksharadasoha.ledger.ui.ReportsState

object PreviewData {
    private val school = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger")
    private val grade = Grade("grade_8", "8th", 0)
    val template = LedgerTemplate(
        id = "rice_8",
        name = "8th Rice Bhagya",
        description = "Rice ledger",
        sheetName = "Rice",
        gradeIds = listOf("grade_8"),
        rates = listOf(RateDef("r", "rice_8", "rice_rate", "Rice", 150.0, "g", 0)),
        columns = listOf(
            ColumnDef("d", "rice_8", "date", "Date", null, ValueType.DATE, null, ColumnRole.DATE, null, 0, true, false),
            ColumnDef("w", "rice_8", "week", "Week", null, ValueType.WEEKDAY, null, ColumnRole.COMPUTED, "WEEKDAY(date)", 1, true, false),
            ColumnDef("c", "rice_8", "count", "Count", null, ValueType.NUMBER, "children", ColumnRole.INPUT, null, 2, true, false),
            ColumnDef("ro", "rice_8", "rice_opening", "Opening", "Rice", ValueType.NUMBER, "kg", ColumnRole.OPENING, null, 3, true, false),
            ColumnDef("rs", "rice_8", "rice_supply", "Supply", "Rice", ValueType.NUMBER, "kg", ColumnRole.INPUT, null, 4, true, false),
            ColumnDef("rt", "rice_8", "rice_total", "Total", "Rice", ValueType.NUMBER, "kg", ColumnRole.COMPUTED, null, 5, true, false),
            ColumnDef("re", "rice_8", "rice_expenditure", "Spend", "Rice", ValueType.NUMBER, "kg", ColumnRole.COMPUTED, null, 6, true, false),
            ColumnDef("rb", "rice_8", "rice_balance", "Balance", "Rice", ValueType.NUMBER, "kg", ColumnRole.COMPUTED, null, 7, true, true),
            ColumnDef("do", "rice_8", "dal_opening", "Opening", "Dal", ValueType.NUMBER, "kg", ColumnRole.OPENING, null, 8, true, false),
            ColumnDef("ds", "rice_8", "dal_supply", "Supply", "Dal", ValueType.NUMBER, "kg", ColumnRole.INPUT, null, 9, true, false),
            ColumnDef("db", "rice_8", "dal_balance", "Balance", "Dal", ValueType.NUMBER, "kg", ColumnRole.COMPUTED, null, 10, true, true),
            ColumnDef("n", "rice_8", "notes", "Notes", null, ValueType.TEXT, null, ColumnRole.INPUT, null, 11, true, false),
        ),
    )
    private val row = LedgerRow(
        "r1",
        "i1",
        "2025-05-01",
        1,
        mapOf("count" to 20.0, "rice_opening" to 477.75, "rice_supply" to 0.0, "dal_opening" to 44.85, "dal_supply" to 0.0),
        mapOf("count" to 20.0, "rice_opening" to 477.75),
        mapOf("notes" to "First day"),
    )
    private val instance = LedgerInstance("i1", "rice_8", 2025, 5, "May 2025", listOf(row))
    val home = HomeState(
        school = school,
        grades = listOf(grade),
        templates = listOf(template),
        instances = listOf(instance),
        ready = true,
        settings = AppSettings(school),
        corrections = listOf(CorrectionNote("oil_rate_9_10", 76.5, 6.5, "Header rate")),
    )
    val ledger = LedgerUiState(
        template = template,
        instance = instance,
        rows = listOf(
            ComputedRow(
                row,
                mapOf(
                    "date" to ComputedCell("date", text = "2025-05-01"),
                    "week" to ComputedCell("week", text = "Thursday"),
                    "count" to ComputedCell("count", number = 20.0),
                    "rice_opening" to ComputedCell("rice_opening", number = 477.75),
                    "rice_supply" to ComputedCell("rice_supply", number = 0.0),
                    "rice_total" to ComputedCell("rice_total", number = 477.75),
                    "rice_expenditure" to ComputedCell("rice_expenditure", number = 3.0),
                    "rice_balance" to ComputedCell("rice_balance", number = 474.75, sourceNumber = 477.75, auditDiffers = true),
                    "dal_opening" to ComputedCell("dal_opening", number = 44.85),
                    "dal_supply" to ComputedCell("dal_supply", number = 0.0),
                    "dal_balance" to ComputedCell("dal_balance", number = 44.25, negativeWarning = true),
                    "notes" to ComputedCell("notes", text = "First day"),
                ),
            ),
        ),
        showAudit = true,
    )
    val config = ConfigState(templates = listOf(template), grades = listOf(grade), selected = template)
    val reports = ReportsState(
        reports = listOf(
            MonthlyReport("rice_8", "8th Rice Bhagya", "i1", "May 2025", 2025, 5, 1, 20.0, listOf(ReportItem("rice_balance", "Rice", "Rice", 477.0, 0.0, 3.0, 474.0, "kg")), auditDifferences = 1),
        ),
    )
}
