package com.aksharadasoha.ledger.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.ValueType
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
class LedgerRepositoryTest {
    private lateinit var repository: LedgerRepository
    private lateinit var db: LedgerDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = LedgerDatabase.inMemory(context)
        repository = LedgerRepository(context, db.dao(), db)
        runBlocking {
            val json = context.assets.open("seed/ledger.json").bufferedReader().use { it.readText() }
            repository.restore(json)
        }
    }

    @Test
    fun seedsGradesAndProtectsReferencedColumns() = runBlocking {
        assertEquals(3, db.dao().getGrades().size)
        val template = repository.template("rice_8")!!
        val opening = template.columns.first { it.key == "rice_opening" }
        val error = runCatching { repository.deleteColumn(template.id, opening.id) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun canAddAndDeleteUnreferencedColumn() = runBlocking {
        val template = repository.template("rice_8")!!
        val column = template.columns.first { it.role == ColumnRole.INPUT && it.key == "count" }.copy(
            id = "rice_8_notes",
            key = "notes",
            label = "Notes",
            valueType = ValueType.NUMBER,
            formula = null,
            sortOrder = 99,
        )
        repository.saveColumn(column)
        repository.deleteColumn("rice_8", "rice_8_notes")
        assertTrue(repository.template("rice_8")!!.columns.none { it.key == "notes" })
    }

    @Test
    fun backupRoundTripPreservesRowCount() = runBlocking {
        val json = repository.exportJson()
        val preview = repository.previewRestore(json)
        assertEquals(5, preview.templates)
        assertEquals(155, preview.rows)
        repository.restore(json)
        assertEquals(155, repository.instances().sumOf { it.rows.size })
        assertTrue(repository.instances().first { it.templateId == "rice_8" }.rows.first().sourceValues.isNotEmpty())
    }

    @Test
    fun rejectsInvalidNumericInput() {
        val error = runCatching { repository.parseNumericInput("12.3.4") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun createMonthCarriesOpeningBalances() = runBlocking {
        val created = repository.createMonth("rice_8", 2025, 6)
        val opening = created.rows.first().cells["rice_opening"]
        assertTrue(opening != null && opening > 0)
        val preview = repository.previewMonth("rice_8", 2025, 6)
        assertTrue(preview.exists)
        repository.deleteInstance(created.id)
        assertTrue(repository.instances().none { it.id == created.id })
    }

    @Test
    fun persistsSchoolSettings() = runBlocking {
        repository.saveSettings(repository.settings().copy(school = com.aksharadasoha.ledger.domain.SchoolProfile("Test School", "MDM")))
        assertEquals("Test School", repository.school().name)
    }

    @Test
    fun mergeKeepsUnrelatedMonths() = runBlocking {
        val before = repository.instances()
        val target = before.first { it.templateId == "rice_8" }
        val edited = target.copy(
            rows = target.rows.mapIndexed { index, row ->
                if (index == 0) row.copy(cells = row.cells + ("count" to 99.0)) else row
            },
        )
        val payload = com.aksharadasoha.ledger.domain.SeedPayload(
            version = 1,
            school = repository.school(),
            grades = listOf(com.aksharadasoha.ledger.domain.Grade("grade_8", "8th", 0)),
            templates = listOf(repository.template("rice_8")!!),
            instances = listOf(edited),
            corrections = emptyList(),
        )
        val preview = repository.previewMerge(payload)
        assertTrue(preview.preservedInstances >= 4)
        repository.mergePayload(payload)
        assertEquals(before.size, repository.instances().size)
        assertEquals(99.0, repository.instance(target.id)!!.rows.first().cells["count"])
        assertTrue(repository.instances().any { it.templateId != "rice_8" })
    }

    @Test
    fun restoresV1BackupWithoutSettings() = runBlocking {
        val root = org.json.JSONObject(repository.exportJson())
        root.remove("settings")
        root.put("archiveVersion", 1)
        repository.saveSettings(repository.settings().copy(language = "kn"))
        repository.restore(root.toString())
        assertEquals(155, repository.instances().sumOf { it.rows.size })
        assertEquals("en", repository.settings().language)
        assertEquals(com.aksharadasoha.ledger.domain.ThemeMode.SYSTEM, repository.settings().themeMode)
        assertEquals(com.aksharadasoha.ledger.domain.ColorPalette.DYNAMIC, repository.settings().colorPalette)
        assertEquals(1.0f, repository.settings().ledgerZoom, 0.0f)
    }

    @Test
    fun rejectsInvalidRate() = runBlocking {
        val template = repository.template("rice_8")!!
        val rate = template.rates.first()
        val error = runCatching { repository.saveRate(rate.copy(value = Double.NaN)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(rate.value, repository.template("rice_8")!!.rates.first { it.id == rate.id }.value, 0.0)
    }

    @Test
    fun replaceRemovesUnrelatedMonths() = runBlocking {
        val before = repository.instances()
        val target = before.first { it.templateId == "rice_8" }
        val payload = com.aksharadasoha.ledger.domain.SeedPayload(
            version = 1,
            school = repository.school(),
            grades = repository.exportArchive().grades,
            templates = repository.templates(),
            instances = listOf(target),
            corrections = emptyList(),
        )
        repository.restore(
            org.json.JSONObject(SeedParser.toJson(payload)).put("archiveVersion", 2).toString(),
        )
        assertEquals(1, repository.instances().size)
        assertEquals(target.id, repository.instances().first().id)
    }

    @Test
    fun v2ArchiveRoundTripsSettings() = runBlocking {
        repository.saveSettings(
            repository.settings().copy(
                language = "en",
                defaultExport = com.aksharadasoha.ledger.domain.ExportFormat.EXCEL,
                colorPalette = com.aksharadasoha.ledger.domain.ColorPalette.OCEAN_BLUE,
                ledgerZoom = 1.3f,
            ),
        )
        val json = repository.exportJson()
        assertTrue(json.contains("\"defaultExport\""))
        assertTrue(json.contains("\"colorPalette\""))
        assertTrue(json.contains("\"ledgerZoom\""))
        repository.saveSettings(repository.settings().copy(defaultExport = com.aksharadasoha.ledger.domain.ExportFormat.PDF, colorPalette = com.aksharadasoha.ledger.domain.ColorPalette.FRESH_GREEN, ledgerZoom = 1f))
        repository.restore(json)
        assertEquals(com.aksharadasoha.ledger.domain.ExportFormat.EXCEL, repository.settings().defaultExport)
        assertEquals(com.aksharadasoha.ledger.domain.ColorPalette.OCEAN_BLUE, repository.settings().colorPalette)
        assertEquals(1.3f, repository.settings().ledgerZoom, 0.0f)
    }

    @Test
    fun renamingColumnMigratesCellKeys() = runBlocking {
        val template = repository.template("rice_8")!!
        val count = template.columns.first { it.key == "count" }
        val instance = repository.instances().first { it.templateId == "rice_8" }
        val row = instance.rows.first()
        repository.updateCell(row.id, "count", 21.0)
        repository.saveColumn(count.copy(key = "head_count"))
        val updated = repository.instance(instance.id)!!
        assertTrue(updated.rows.first().cells.containsKey("head_count"))
    }

    @Test
    fun mergeRollsBackWhenInterrupted() = runBlocking {
        val before = repository.exportJson()
        val archive = repository.exportArchive()
        val payload = com.aksharadasoha.ledger.domain.SeedPayload(
            version = archive.seedVersion,
            school = archive.school,
            grades = archive.grades,
            templates = archive.templates,
            instances = archive.instances,
            corrections = archive.corrections,
        )
        repository.testInterrupt = "after-templates"
        val error = runCatching { repository.mergePayload(payload) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        repository.testInterrupt = null
        assertEquals(org.json.JSONObject(before).getJSONArray("instances").length(), repository.instances().size)
        assertEquals(155, repository.instances().sumOf { it.rows.size })
    }

    @Test
    fun replaceRollsBackWhenInterrupted() = runBlocking {
        val beforeCount = repository.instances().size
        val json = repository.exportJson()
        repository.testInterrupt = "after-replace"
        val error = runCatching { repository.restore(json) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        repository.testInterrupt = null
        assertEquals(beforeCount, repository.instances().size)
    }

    @Test
    fun rejectsInvalidArchiveRules() = runBlocking {
        val archive = repository.exportArchive()
        val payload = com.aksharadasoha.ledger.domain.SeedPayload(
            version = archive.seedVersion,
            school = archive.school,
            grades = archive.grades,
            templates = archive.templates,
            instances = archive.instances,
            corrections = archive.corrections,
        )
        val duplicateIds = payload.copy(grades = payload.grades + payload.grades.first())
        assertTrue(runCatching { repository.validatePayload(duplicateIds) }.exceptionOrNull() is IllegalArgumentException)
        val badYear = payload.copy(instances = payload.instances.map { it.copy(year = 1999) })
        assertTrue(runCatching { repository.validatePayload(badYear) }.exceptionOrNull() is IllegalArgumentException)
        val badMonth = payload.copy(instances = payload.instances.map { it.copy(month = 13) })
        assertTrue(runCatching { repository.validatePayload(badMonth) }.exceptionOrNull() is IllegalArgumentException)
        val badDate = payload.copy(
            instances = payload.instances.map { instance ->
                instance.copy(rows = instance.rows.map { it.copy(date = "not-a-date") })
            },
        )
        assertTrue(runCatching { repository.validatePayload(badDate) }.exceptionOrNull() is IllegalArgumentException)
        val nanCell = payload.copy(
            instances = payload.instances.map { instance ->
                instance.copy(rows = instance.rows.map { it.copy(cells = it.cells + ("count" to Double.NaN)) })
            },
        )
        assertTrue(runCatching { repository.validatePayload(nanCell) }.exceptionOrNull() is IllegalArgumentException)
        val missingTemplate = payload.copy(instances = payload.instances.map { it.copy(templateId = "missing") })
        assertTrue(runCatching { repository.validatePayload(missingTemplate) }.exceptionOrNull() is IllegalArgumentException)
        val badVersion = runCatching { repository.validatePayload(payload, 9) }.exceptionOrNull()
        assertTrue(badVersion is IllegalArgumentException)
        val duplicateColumns = payload.templates.first().let { template ->
            payload.copy(templates = payload.templates.map { if (it.id == template.id) template.copy(columns = template.columns + template.columns.first()) else it })
        }
        assertTrue(runCatching { repository.validatePayload(duplicateColumns) }.exceptionOrNull() is IllegalArgumentException)
        val brokenFormula = payload.templates.first().let { template ->
            payload.copy(
                templates = payload.templates.map {
                    if (it.id != template.id) it
                    else it.copy(columns = it.columns.map { column -> if (column.formula != null) column.copy(formula = "UNKNOWN(count)") else column })
                },
            )
        }
        assertTrue(runCatching { repository.validatePayload(brokenFormula) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun persistsTextCells() = runBlocking {
        val instance = repository.instances().first { it.templateId == "rice_8" }
        val row = instance.rows.first()
        repository.updateTextCell(row.id, "notes", "Kitchen note")
        assertEquals("Kitchen note", repository.instance(instance.id)!!.rows.first().textCells["notes"])
        repository.updateTextCell(row.id, "notes", "")
        assertTrue(repository.instance(instance.id)!!.rows.first().textCells["notes"].isNullOrBlank())
    }

    @Test
    fun mergeUpdatesExistingTemplateMetadata() = runBlocking {
        val current = repository.template("rice_8")!!
        val incoming = current.copy(
            name = "8th Rice Bhagya Updated",
            description = "Updated description",
            columns = current.columns.map { if (it.key == "count") it.copy(label = "Head count") else it },
            rates = current.rates.map { if (it.key == "oil_rate") it.copy(value = 7.0) else it },
        )
        val payload = com.aksharadasoha.ledger.domain.SeedPayload(
            version = 1,
            school = repository.school(),
            grades = repository.exportArchive().grades,
            templates = listOf(incoming),
            instances = emptyList(),
            corrections = emptyList(),
        )
        repository.mergePayload(payload)
        val merged = repository.template("rice_8")!!
        assertEquals("8th Rice Bhagya Updated", merged.name)
        assertEquals("Head count", merged.columns.first { it.key == "count" }.label)
        assertEquals(7.0, merged.rates.first { it.key == "oil_rate" }.value, 0.0)
        assertTrue(merged.columns.size >= current.columns.size)
        assertEquals(155, repository.instances().sumOf { it.rows.size })
    }

    @Test
    fun templateMetadataUpdateKeepsChildRows() = runBlocking {
        val beforeRows = repository.instances().first { it.templateId == "rice_8" }.rows.size
        val current = repository.template("rice_8")!!
        repository.saveTemplate(current.copy(name = "8th Rice Bhagya Renamed", description = "Renamed"))
        assertEquals("8th Rice Bhagya Renamed", repository.template("rice_8")!!.name)
        assertEquals(beforeRows, repository.instances().first { it.templateId == "rice_8" }.rows.size)
        assertTrue(repository.instances().first { it.templateId == "rice_8" }.rows.first().cells.isNotEmpty())
    }

    @Test
    fun mergeKeepsExistingIdsWhenKeysMatch() = runBlocking {
        val current = repository.template("rice_8")!!
        val count = current.columns.first { it.key == "count" }
        val rate = current.rates.first { it.key == "oil_rate" }
        val incoming = current.copy(
            columns = current.columns.map { if (it.key == "count") it.copy(id = "incoming_count", label = "Head count") else it },
            rates = current.rates.map { if (it.key == "oil_rate") it.copy(id = "incoming_oil", value = 8.0) else it },
        )
        repository.mergePayload(
            com.aksharadasoha.ledger.domain.SeedPayload(
                version = 1,
                school = repository.school(),
                grades = repository.exportArchive().grades,
                templates = listOf(incoming),
                instances = emptyList(),
                corrections = emptyList(),
            ),
        )
        val merged = repository.template("rice_8")!!
        assertEquals(count.id, merged.columns.first { it.key == "count" }.id)
        assertEquals("Head count", merged.columns.first { it.key == "count" }.label)
        assertEquals(rate.id, merged.rates.first { it.key == "oil_rate" }.id)
        assertEquals(8.0, merged.rates.first { it.key == "oil_rate" }.value, 0.0)
    }

    @Test
    fun mergeMappedBlankClearsAndLeavesUnmapped() = runBlocking {
        val target = repository.instances().first { it.templateId == "rice_8" }
        val first = target.rows.first()
        repository.updateTextCell(first.id, "notes", "Keep me")
        repository.updateCell(first.id, "count", 21.0)
        val riceOpening = first.cells["rice_opening"]
        val current = repository.template("rice_8")!!
        val withNotes = current.copy(
            columns = current.columns + com.aksharadasoha.ledger.domain.ColumnDef(
                id = "rice_8_notes",
                templateId = "rice_8",
                key = "notes",
                label = "Notes",
                groupLabel = null,
                valueType = ValueType.TEXT,
                unit = null,
                role = ColumnRole.INPUT,
                formula = null,
                sortOrder = 99,
                visible = true,
                warnNegative = false,
            ),
        )
        val partial = target.copy(
            rows = listOf(
                first.copy(
                    cells = mapOf("count" to null),
                    sourceValues = mapOf("count" to null),
                    textCells = mapOf("notes" to ""),
                ),
            ),
        )
        repository.mergePayload(
            com.aksharadasoha.ledger.domain.SeedPayload(
                version = 1,
                school = repository.school(),
                grades = repository.exportArchive().grades,
                templates = listOf(withNotes),
                instances = listOf(partial),
                corrections = emptyList(),
            ),
        )
        val updated = repository.instance(target.id)!!
        assertTrue(updated.rows.first().cells["count"] == null)
        assertTrue(updated.rows.first().textCells["notes"].isNullOrBlank())
        assertEquals(riceOpening, updated.rows.first().cells["rice_opening"])
        assertEquals(target.rows.size, updated.rows.size)
        assertTrue(repository.instances().any { it.templateId != "rice_8" })
    }

    @Test
    fun rejectsNewValidationRules() = runBlocking {
        val archive = repository.exportArchive()
        val payload = com.aksharadasoha.ledger.domain.SeedPayload(
            version = archive.seedVersion,
            school = archive.school,
            grades = archive.grades,
            templates = archive.templates,
            instances = archive.instances,
            corrections = archive.corrections,
        )
        val duplicateColumnIds = payload.templates.first().let { template ->
            val other = payload.templates[1]
            payload.copy(
                templates = payload.templates.map {
                    if (it.id == other.id) it.copy(columns = it.columns.mapIndexed { index, column -> if (index == 0) column.copy(id = template.columns.first().id) else column }) else it
                },
            )
        }
        assertTrue(runCatching { repository.validatePayload(duplicateColumnIds) }.exceptionOrNull() is IllegalArgumentException)
        val unknownCell = payload.copy(
            instances = payload.instances.map { instance ->
                instance.copy(rows = instance.rows.map { it.copy(cells = it.cells + ("missing_col" to 1.0)) })
            },
        )
        assertTrue(runCatching { repository.validatePayload(unknownCell) }.exceptionOrNull() is IllegalArgumentException)
        val nanSource = payload.copy(
            instances = payload.instances.map { instance ->
                instance.copy(rows = instance.rows.map { it.copy(sourceValues = it.sourceValues + ("count" to Double.POSITIVE_INFINITY)) })
            },
        )
        assertTrue(runCatching { repository.validatePayload(nanSource) }.exceptionOrNull() is IllegalArgumentException)
        val duplicateDate = payload.copy(
            instances = payload.instances.map { instance ->
                instance.copy(rows = instance.rows + instance.rows.first().copy(id = instance.rows.first().id + "_dup"))
            },
        )
        assertTrue(runCatching { repository.validatePayload(duplicateDate) }.exceptionOrNull() is IllegalArgumentException)
        val outsideMonth = payload.copy(
            instances = payload.instances.map { instance ->
                instance.copy(rows = instance.rows.mapIndexed { index, row -> if (index == 0) row.copy(date = "2024-01-01") else row })
            },
        )
        assertTrue(runCatching { repository.validatePayload(outsideMonth) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun seedUpgradeRollsBackFully() = runBlocking {
        val before = repository.instances().size
        val target = repository.instances().first()
        repository.deleteInstance(target.id)
        db.dao().upsertMeta(com.aksharadasoha.ledger.data.MetaEntity("seedVersion", "0"))
        repository.testInterrupt = "after-seed-instances"
        val error = runCatching { repository.applySeedUpgrade() }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        repository.testInterrupt = null
        assertEquals(before - 1, repository.instances().size)
        assertTrue(repository.instances().none { it.id == target.id })
        assertEquals(0, repository.seedVersion())
    }

    @Test
    fun excelReplacePreservesSettings() = runBlocking {
        repository.saveSettings(
            repository.settings().copy(
                themeMode = com.aksharadasoha.ledger.domain.ThemeMode.DARK,
                tableDensity = com.aksharadasoha.ledger.domain.TableDensity.COMFORTABLE,
                defaultExport = com.aksharadasoha.ledger.domain.ExportFormat.BOTH,
                colorPalette = com.aksharadasoha.ledger.domain.ColorPalette.OCEAN_BLUE,
                ledgerZoom = 1.2f,
                school = com.aksharadasoha.ledger.domain.SchoolProfile("Kept School", "Kept Program"),
            ),
        )
        val target = repository.instances().first { it.templateId == "rice_8" }
        val payload = com.aksharadasoha.ledger.domain.SeedPayload(
            version = 1,
            school = com.aksharadasoha.ledger.domain.SchoolProfile("Imported School", "Imported"),
            grades = repository.exportArchive().grades,
            templates = repository.templates(),
            instances = listOf(target),
            corrections = emptyList(),
        )
        repository.replaceFromExcel(payload)
        val settings = repository.settings()
        assertEquals(com.aksharadasoha.ledger.domain.ThemeMode.DARK, settings.themeMode)
        assertEquals(com.aksharadasoha.ledger.domain.TableDensity.COMFORTABLE, settings.tableDensity)
        assertEquals(com.aksharadasoha.ledger.domain.ExportFormat.BOTH, settings.defaultExport)
        assertEquals(com.aksharadasoha.ledger.domain.ColorPalette.OCEAN_BLUE, settings.colorPalette)
        assertEquals(1.2f, settings.ledgerZoom, 0.0f)
        assertEquals("Kept School", settings.school.name)
        assertEquals(1, repository.instances().size)
    }
}
