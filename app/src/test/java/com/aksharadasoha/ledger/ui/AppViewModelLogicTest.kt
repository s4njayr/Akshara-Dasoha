package com.aksharadasoha.ledger.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.aksharadasoha.ledger.data.LedgerDatabase
import com.aksharadasoha.ledger.data.LedgerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppViewModelLogicTest {
    private lateinit var repository: LedgerRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        repository = LedgerRepository(context, LedgerDatabase.inMemory(context).dao())
        runBlocking { repository.seedIfNeeded() }
    }

    @Test
    fun mergePreviewKeepsUnrelatedMonths() = runBlocking {
        val before = repository.instances()
        val target = before.first { it.templateId == "rice_8" }
        val payload = com.aksharadasoha.ledger.domain.SeedPayload(
            version = 1,
            school = repository.school(),
            grades = listOf(com.aksharadasoha.ledger.domain.Grade("grade_8", "8th", 0)),
            templates = listOf(repository.template("rice_8")!!),
            instances = listOf(target),
            corrections = emptyList(),
        )
        val preview = repository.previewMerge(payload)
        assertTrue(preview.preservedInstances >= 4)
        assertTrue(preview.wouldRemoveInstances >= 4)
    }

    @Test
    fun invalidInputDoesNotWrite() = runBlocking {
        val instance = repository.instances().first()
        val row = instance.rows.first()
        val before = row.cells["count"]
        val error = runCatching { repository.parseNumericInput("abc") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(before == null || before >= 0)
    }

    @Test
    fun defaultExportBothWritesPdfAndXlsx() {
        val pdf = "%PDF-1.4 dummy".toByteArray()
        val xlsx = java.io.ByteArrayOutputStream().also { out ->
            val instance = runBlocking { repository.instances().first() }
            val template = runBlocking { repository.template(instance.templateId)!! }
            com.aksharadasoha.ledger.export.LedgerExcelExporter.write(
                com.aksharadasoha.ledger.export.ExcelExportRequest(
                    school = runBlocking { repository.school() },
                    templates = listOf(template),
                    instances = listOf(instance),
                    computed = mapOf(instance.id to com.aksharadasoha.ledger.domain.FormulaEngine().compute(template, instance.rows)),
                ),
                out,
            )
        }.toByteArray()
        val zipBytes = java.io.ByteArrayOutputStream().also { out ->
            com.aksharadasoha.ledger.export.CombinedExport.writeZip("month.pdf", pdf, "month.xlsx", xlsx, out)
        }.toByteArray()
        val entries = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        assertEquals(setOf("month.pdf", "month.xlsx"), entries.keys)
        assertTrue(entries.getValue("month.pdf").decodeToString().startsWith("%PDF"))
        assertEquals('P'.code.toByte(), entries.getValue("month.xlsx")[0])
        assertEquals('K'.code.toByte(), entries.getValue("month.xlsx")[1])
        assertTrue(entries.getValue("month.xlsx").size > 200)
    }
}
