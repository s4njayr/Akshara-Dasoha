package com.aksharadasoha.ledger.data

import com.aksharadasoha.ledger.domain.FormulaEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedBackupTest {
    private fun seedJson(): String {
        return javaClass.classLoader!!.getResourceAsStream("seed/ledger.json")!!.bufferedReader().use { it.readText() }
    }

    @Test
    fun parsesSeedAndRoundTrips() {
        val payload = SeedParser.parse(seedJson())
        assertEquals(3, payload.grades.size)
        assertEquals(5, payload.templates.size)
        assertEquals(5, payload.instances.size)
        val again = SeedParser.parse(SeedParser.toJson(payload))
        assertEquals(payload.templates.map { it.id }, again.templates.map { it.id })
        assertEquals(payload.instances.sumOf { it.rows.size }, again.instances.sumOf { it.rows.size })
    }

    @Test
    fun riceEighthMatchesWorkbookClosings() {
        val payload = SeedParser.parse(seedJson())
        val template = payload.templates.first { it.id == "rice_8" }
        val instance = payload.instances.first { it.templateId == "rice_8" }
        val computed = FormulaEngine().compute(template, instance.rows)
        val last = computed.last()
        // Excel left first-day rice/dal expenditure blank; the app applies the header rates.
        assertEquals(457.8, last.cells["rice_balance"]?.number!!, 0.01)
        assertEquals(40.86, last.cells["dal_balance"]?.number!!, 0.01)
        assertEquals(3.9205, last.cells["oil_balance"]?.number!!, 0.01)
        assertEquals(32.35, last.cells["wheat_balance"]?.number!!, 0.01)
    }

    @Test
    fun documentsCorrectedRates() {
        val payload = SeedParser.parse(seedJson())
        val oil = payload.templates.first { it.id == "rice_9_10" }.rates.first { it.key == "oil_rate" }
        val banana = payload.templates.first { it.id == "egg_banana" }.rates.first { it.key == "banana_rate" }
        assertEquals(6.5, oil.value, 0.0)
        assertEquals(5.7, banana.value, 0.0)
        assertTrue(payload.corrections.any { it.item == "oil_rate_9_10" })
        assertTrue(payload.corrections.any { it.item == "banana_rate" })
    }

    @Test
    fun keepsImportedSourceValuesForAudit() {
        val payload = SeedParser.parse(seedJson())
        val first = payload.instances.first { it.templateId == "rice_8" }.rows.first()
        assertEquals(477.75, first.sourceValues["rice_opening"]!!, 0.0)
        assertEquals(477.75, first.sourceValues["rice_balance"]!!, 0.0)
        val again = SeedParser.parse(SeedParser.toJson(payload))
        assertEquals(first.sourceValues["rice_opening"], again.instances.first { it.templateId == "rice_8" }.rows.first().sourceValues["rice_opening"])
    }
}
