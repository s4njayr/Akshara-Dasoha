package com.aksharadasoha.ledger

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aksharadasoha.ledger.data.LedgerDatabase
import com.aksharadasoha.ledger.data.LedgerRepository
import com.aksharadasoha.ledger.domain.FormulaEngine
import com.aksharadasoha.ledger.print.LedgerPrinter
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrintAndRestoreTest {
    @Test
    fun writesPdfAndRestoresBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<LedgerApplication>()
        val db = LedgerDatabase.inMemory(context)
        val repository = LedgerRepository(context, db.dao())
        val json = context.assets.open("seed/ledger.json").bufferedReader().use { it.readText() }
        repository.restore(json)
        val exported = repository.exportJson()
        repository.restore(exported)
        val template = repository.template("rice_8")!!
        val instance = repository.instances().first { it.templateId == "rice_8" }
        val rows = FormulaEngine().compute(template, instance.rows)
        val file = File(context.cacheDir, "test-ledger.pdf")
        LedgerPrinter.writePdf(context, repository.school(), template, instance, rows, file)
        assertTrue(file.exists() && file.length() > 100)
        assertTrue(repository.instances().sumOf { it.rows.size } >= 150)
        db.close()
    }
}
