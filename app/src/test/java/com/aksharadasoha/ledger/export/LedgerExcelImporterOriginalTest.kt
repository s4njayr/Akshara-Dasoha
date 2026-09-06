package com.aksharadasoha.ledger.export

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.aksharadasoha.ledger.data.SeedParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerExcelImporterOriginalTest {
    @Test
    fun originalWorkbookMatchesPythonSeed() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val seed = SeedParser.parse(context.assets.open("seed/ledger.json").bufferedReader().use { it.readText() })
        val workbook = originalWorkbook()
        val preview = LedgerExcelImporter.preview(workbook.inputStream(), seed.templates)
        assertTrue(preview.recognized)
        assertEquals(5, preview.instances)
        assertEquals(155, preview.rows)
        val imported = LedgerExcelImporter.import(workbook.inputStream(), seed.templates, seed.grades, seed.school, seed.corrections)
        assertEquals(5, imported.templates.size)
        assertEquals(5, imported.instances.size)
        assertEquals(155, imported.instances.sumOf { it.rows.size })
        imported.instances.forEach { assertEquals(31, it.rows.size) }
        val oil = imported.templates.first { it.id == "rice_9_10" }.rates.first { it.key == "oil_rate" }
        val banana = imported.templates.first { it.id == "egg_banana" }.rates.first { it.key == "banana_rate" }
        assertEquals(6.5, oil.value, 0.0)
        assertEquals(5.7, banana.value, 0.0)
        val rice = imported.instances.first { it.templateId == "rice_8" }
        val seedRice = seed.instances.first { it.templateId == "rice_8" }
        val first = rice.rows.first()
        val seedFirst = seedRice.rows.first()
        assertEquals(477.75, first.cells["rice_opening"]!!, 0.0)
        assertEquals(44.85, first.cells["dal_opening"]!!, 0.0)
        assertEquals(seedFirst.cells["count"], first.cells["count"])
        assertEquals(seedFirst.sourceValues["rice_opening"], first.sourceValues["rice_opening"])
        assertEquals(460.8, rice.rows.last().sourceValues["rice_balance"]!!, 0.02)
        seed.instances.forEach { expected ->
            val actual = imported.instances.first { it.templateId == expected.templateId }
            assertEquals(expected.rows.size, actual.rows.size)
            expected.rows.forEachIndexed { index, row ->
                val importedRow = actual.rows[index]
                row.cells.forEach { (key, value) ->
                    assertEquals("$key on ${row.date}", value, importedRow.cells[key])
                }
                row.sourceValues.forEach { (key, value) ->
                    val importedValue = importedRow.sourceValues[key]
                    if (value != null && importedValue != null) {
                        assertEquals("$key source on ${row.date}", value, importedValue, 0.001)
                    }
                }
            }
        }
    }

    private fun originalWorkbook(): File {
        val candidates = listOf(
            File("Ledger.xlsx"),
            File("../Ledger.xlsx"),
            File("../../Ledger.xlsx"),
        )
        return candidates.firstOrNull { it.exists() } ?: error("Ledger.xlsx was not found next to the project")
    }
}
