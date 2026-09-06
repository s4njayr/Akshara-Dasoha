package com.aksharadasoha.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaEngineTest {
    private val engine = FormulaEngine()

    private fun commodityTemplate(): LedgerTemplate {
        val id = "demo"
        return LedgerTemplate(
            id = id,
            name = "Demo",
            description = "",
            sheetName = null,
            gradeIds = listOf("grade_8"),
            rates = listOf(RateDef("${id}_rice_rate", id, "rice_rate", "Rice", 150.0, "g", 0)),
            columns = listOf(
                ColumnDef("${id}_date", id, "date", "Date", null, ValueType.DATE, null, ColumnRole.DATE, null, 0, true, false),
                ColumnDef("${id}_week", id, "week", "Week", null, ValueType.WEEKDAY, null, ColumnRole.COMPUTED, "WEEKDAY(date)", 1, true, false),
                ColumnDef("${id}_count", id, "count", "Count", null, ValueType.NUMBER, "children", ColumnRole.INPUT, null, 2, true, false),
                ColumnDef("${id}_open", id, "rice_opening", "Opening", "Rice", ValueType.NUMBER, "kg", ColumnRole.OPENING, "IF(ISFIRST(), INPUT(), CARRY(rice_balance))", 3, true, false),
                ColumnDef("${id}_supply", id, "rice_supply", "Supply", "Rice", ValueType.NUMBER, "kg", ColumnRole.INPUT, null, 4, true, false),
                ColumnDef("${id}_total", id, "rice_total", "Total", "Rice", ValueType.NUMBER, "kg", ColumnRole.COMPUTED, "SUM(rice_opening, rice_supply)", 5, true, false),
                ColumnDef("${id}_spend", id, "rice_expenditure", "Expenditure", "Rice", ValueType.NUMBER, "kg", ColumnRole.COMPUTED, "count * rice_rate / 1000", 6, true, false),
                ColumnDef("${id}_bal", id, "rice_balance", "Balance", "Rice", ValueType.NUMBER, "kg", ColumnRole.COMPUTED, "rice_total - rice_expenditure", 7, true, true),
            ),
        )
    }

    @Test
    fun parsesArithmeticAndFunctions() {
        val expr = FormulaParser("SUM(count * rice_rate / 1000, 2)").parse()
        assertTrue(expr is Expr.Call)
    }

    @Test
    fun rejectsUnknownTokens() {
        val errors = FormulaValidator.validate("NOPE(count)", setOf("count"))
        assertTrue(errors.any { it.contains("Unknown") })
    }

    @Test
    fun detectsSameRowCycles() {
        val columns = listOf(
            ColumnDef("a", "t", "a", "A", null, ValueType.NUMBER, null, ColumnRole.COMPUTED, "b", 0, true, false),
            ColumnDef("b", "t", "b", "B", null, ValueType.NUMBER, null, ColumnRole.COMPUTED, "a", 1, true, false),
        )
        assertTrue(FormulaValidator.hasCycle(columns).isNotEmpty())
    }

    @Test
    fun carryIsNotASameRowCycle() {
        val cycles = FormulaValidator.hasCycle(commodityTemplate().columns)
        assertTrue(cycles.isEmpty())
    }

    @Test
    fun computesCarryForwardAndExpenditure() {
        val template = commodityTemplate()
        val rows = listOf(
            LedgerRow("r1", "i", "2025-05-01", 1, mapOf("count" to 20.0, "rice_opening" to 477.75, "rice_supply" to 0.0)),
            LedgerRow("r2", "i", "2025-05-02", 2, mapOf("count" to 0.0, "rice_supply" to 0.0)),
            LedgerRow("r3", "i", "2025-05-03", 3, mapOf("count" to 22.0, "rice_supply" to 0.0)),
        )
        val computed = engine.compute(template, rows)
        assertEquals("Thursday", computed[0].cells["week"]?.text)
        assertEquals(3.0, computed[0].cells["rice_expenditure"]?.number!!, 0.0001)
        assertEquals(474.75, computed[0].cells["rice_balance"]?.number!!, 0.0001)
        assertEquals(474.75, computed[1].cells["rice_opening"]?.number!!, 0.0001)
        assertEquals(3.3, computed[2].cells["rice_expenditure"]?.number!!, 0.0001)
        assertEquals(471.45, computed[2].cells["rice_balance"]?.number!!, 0.0001)
    }

    @Test
    fun flagsNegativeBalances() {
        val template = commodityTemplate()
        val rows = listOf(
            LedgerRow("r1", "i", "2025-03-01", 1, mapOf("count" to 111.0, "rice_opening" to 0.0, "rice_supply" to 0.0)),
        )
        val computed = engine.compute(template, rows).first()
        assertTrue(computed.cells["rice_balance"]?.negativeWarning == true)
        assertTrue(computed.cells["rice_balance"]?.number!! < 0)
    }

    @Test
    fun circularReferenceBecomesCellError() {
        val template = LedgerTemplate(
            "t", "T", "", null, emptyList(),
            columns = listOf(
                ColumnDef("a", "t", "a", "A", null, ValueType.NUMBER, null, ColumnRole.COMPUTED, "b", 0, true, false),
                ColumnDef("b", "t", "b", "B", null, ValueType.NUMBER, null, ColumnRole.COMPUTED, "a", 1, true, false),
            ),
            rates = emptyList(),
        )
        val computed = engine.compute(template, listOf(LedgerRow("r", "i", "2025-01-01", 1, emptyMap())))
        assertTrue(computed.first().cells["a"]?.error != null)
    }
}
