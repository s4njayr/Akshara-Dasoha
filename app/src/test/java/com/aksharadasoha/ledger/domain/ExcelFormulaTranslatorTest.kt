package com.aksharadasoha.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelFormulaTranslatorTest {
    private val translator = ExcelFormulaTranslator()
    private val context = ExcelFormulaContext(
        columnLetters = mapOf(
            "date" to "A",
            "count" to "C",
            "rice_opening" to "D",
            "rice_supply" to "E",
            "rice_total" to "F",
            "rice_expenditure" to "G",
            "rice_balance" to "H",
        ),
        rateRefs = mapOf("rice_rate" to "Rates!\$D\$2"),
        row = 5,
        firstDataRow = 4,
        currentColumnKey = "rice_opening",
    )

    @Test
    fun translatesSumProductAndRates() {
        assertEquals("(D5+E5)", translator.translate("SUM(rice_opening, rice_supply)", context))
        assertEquals("((C5*Rates!\$D\$2)/1000)", translator.translate("count * rice_rate / 1000", context))
    }

    @Test
    fun translatesCarryAndFirstDay() {
        val carry = translator.translate("IF(ISFIRST(), INPUT(), CARRY(rice_balance))", context)
        assertTrue(carry.contains("IF(ROW()=4,1,0)"))
        assertTrue(carry.contains("H4") || carry.contains("IF(ROW()=4,0,H4)"))
    }

    @Test
    fun translatesWeekday() {
        assertEquals("TEXT(A5,\"dddd\")", translator.translate("WEEKDAY(date)", context))
    }
}
