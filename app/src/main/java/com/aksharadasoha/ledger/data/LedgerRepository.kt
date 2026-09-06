package com.aksharadasoha.ledger.data

import android.content.Context
import com.aksharadasoha.ledger.domain.AppSettings
import com.aksharadasoha.ledger.domain.BackupArchive
import com.aksharadasoha.ledger.domain.ColorPalette
import com.aksharadasoha.ledger.domain.ColumnDef
import com.aksharadasoha.ledger.domain.MergePreview
import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.CorrectionNote
import com.aksharadasoha.ledger.domain.ExportFormat
import com.aksharadasoha.ledger.domain.FormulaEngine
import com.aksharadasoha.ledger.domain.FormulaValidator
import com.aksharadasoha.ledger.domain.Grade
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerRow
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.LedgerZoom
import com.aksharadasoha.ledger.domain.MonthCreatePreview
import com.aksharadasoha.ledger.domain.RateDef
import com.aksharadasoha.ledger.domain.SchoolProfile
import com.aksharadasoha.ledger.domain.SeedPayload
import com.aksharadasoha.ledger.domain.SeedUpgradePreview
import com.aksharadasoha.ledger.domain.TableDensity
import com.aksharadasoha.ledger.domain.ThemeMode
import com.aksharadasoha.ledger.domain.ValueType
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class LedgerRepository(
    private val context: Context,
    private val dao: LedgerDao,
    private val database: LedgerDatabase? = null,
) {
    @Volatile
    var testInterrupt: String? = null
    val grades: Flow<List<Grade>> = dao.observeGrades().map { items -> items.map { it.toDomain() } }
    val templates: Flow<List<TemplateEntity>> = dao.observeTemplates()
    val instances: Flow<List<InstanceEntity>> = dao.observeInstances()

    suspend fun seedIfNeeded() {
        if (dao.getMeta("seeded") == "true") return
        val payload = bundledPayload()
        replaceFromPayload(payload)
        dao.upsertMeta(MetaEntity("seeded", "true"))
        dao.upsertMeta(MetaEntity("seedVersion", payload.version.toString()))
        persistSchool(payload.school)
        persistCorrections(payload.corrections)
    }

    suspend fun bundledPayload(): SeedPayload {
        val json = context.assets.open("seed/ledger.json").bufferedReader().use { it.readText() }
        return SeedParser.parse(json)
    }

    suspend fun seedVersion(): Int = dao.getMeta("seedVersion")?.toIntOrNull() ?: 1

    suspend fun school(): SchoolProfile {
        val name = dao.getMeta(META_SCHOOL_NAME)
        val program = dao.getMeta(META_SCHOOL_PROGRAM)
        if (name != null && program != null) return SchoolProfile(name, program)
        return bundledPayload().school
    }

    suspend fun corrections(): List<CorrectionNote> {
        val stored = dao.getMeta(META_CORRECTIONS)
        if (!stored.isNullOrBlank()) {
            return runCatching {
                SeedParser.parse("""{"version":1,"school":{"name":"","program":""},"grades":[],"templates":[],"instances":[],"corrections":$stored}""").corrections
            }.getOrElse { bundledPayload().corrections }
        }
        return bundledPayload().corrections
    }

    suspend fun settings(): AppSettings {
        return AppSettings(
            school = school(),
            themeMode = ThemeMode.entries.firstOrNull { it.name == dao.getMeta(META_THEME) } ?: ThemeMode.SYSTEM,
            tableDensity = TableDensity.entries.firstOrNull { it.name == dao.getMeta(META_DENSITY) } ?: TableDensity.COMPACT,
            language = dao.getMeta(META_LANGUAGE) ?: "en",
            defaultExport = ExportFormat.entries.firstOrNull { it.name == dao.getMeta(META_EXPORT) } ?: ExportFormat.PDF,
            colorPalette = ColorPalette.entries.firstOrNull { it.name == dao.getMeta(META_PALETTE) } ?: ColorPalette.DYNAMIC,
            ledgerZoom = LedgerZoom.clamp(dao.getMeta(META_ZOOM)?.toFloatOrNull() ?: LedgerZoom.DEFAULT),
        )
    }

    suspend fun saveSettings(settings: AppSettings) {
        persistSchool(settings.school)
        dao.upsertMeta(MetaEntity(META_THEME, settings.themeMode.name))
        dao.upsertMeta(MetaEntity(META_DENSITY, settings.tableDensity.name))
        dao.upsertMeta(MetaEntity(META_LANGUAGE, settings.language))
        dao.upsertMeta(MetaEntity(META_EXPORT, settings.defaultExport.name))
        dao.upsertMeta(MetaEntity(META_PALETTE, settings.colorPalette.name))
        dao.upsertMeta(MetaEntity(META_ZOOM, LedgerZoom.clamp(settings.ledgerZoom).toString()))
    }

    suspend fun templates(): List<LedgerTemplate> {
        val grades = dao.getTemplateGrades().groupBy { it.templateId }
        val columns = dao.getAllColumns().groupBy { it.templateId }
        val rates = dao.getAllRates().groupBy { it.templateId }
        return dao.getTemplates().map { template ->
            assembleTemplate(
                template,
                grades[template.id].orEmpty().map { it.gradeId },
                columns[template.id].orEmpty(),
                rates[template.id].orEmpty(),
            )
        }
    }

    suspend fun template(id: String): LedgerTemplate? {
        val entity = dao.getTemplate(id) ?: return null
        return assembleTemplate(
            entity,
            dao.getTemplateGrades(id).map { it.gradeId },
            dao.getColumns(id),
            dao.getRates(id),
        )
    }

    suspend fun instances(): List<LedgerInstance> {
        val rows = dao.getAllRows().groupBy { it.instanceId }
        val cells = dao.getAllCells().groupBy { it.rowId }
        val source = dao.getAllSourceCells().groupBy { it.rowId }
        val texts = dao.getAllTextCells().groupBy { it.rowId }
        return dao.getInstances().map { instance ->
            val instanceRows = rows[instance.id].orEmpty()
            assembleInstance(
                instance,
                instanceRows,
                instanceRows.flatMap { cells[it.id].orEmpty() },
                instanceRows.flatMap { source[it.id].orEmpty() },
                instanceRows.flatMap { texts[it.id].orEmpty() },
            )
        }
    }

    suspend fun instance(id: String): LedgerInstance? {
        val entity = dao.getInstance(id) ?: return null
        val rows = dao.getRows(id)
        val rowIds = rows.map { it.id }
        val cells = if (rowIds.isEmpty()) emptyList() else dao.getCells(rowIds)
        val source = if (rowIds.isEmpty()) emptyList() else dao.getSourceCells(rowIds)
        val texts = if (rowIds.isEmpty()) emptyList() else dao.getTextCells(rowIds)
        return assembleInstance(entity, rows, cells, source, texts)
    }

    suspend fun saveGrade(grade: Grade) = dao.upsertGrade(grade.toEntity())

    suspend fun deleteGrade(id: String) {
        val used = dao.getTemplateGrades().any { it.gradeId == id }
        require(!used) { "This class is used by a ledger. Remove it from templates first." }
        dao.deleteGrade(id)
    }

    suspend fun saveTemplate(template: LedgerTemplate) {
        validateTemplate(template)
        dao.upsertTemplate(TemplateEntity(template.id, template.name, template.description, template.sheetName))
        dao.deleteTemplateGrades(template.id)
        dao.upsertTemplateGrades(template.gradeIds.map { TemplateGradeEntity(template.id, it) })
        dao.upsertColumns(template.columns.map { it.toEntity() })
        dao.upsertRates(template.rates.map { it.toEntity() })
    }

    suspend fun createTemplate(name: String, description: String, gradeIds: List<String>): LedgerTemplate {
        val id = "template_${UUID.randomUUID().toString().take(8)}"
        val template = LedgerTemplate(
            id = id,
            name = name,
            description = description,
            sheetName = null,
            gradeIds = gradeIds,
            columns = listOf(
                ColumnDef("${id}_date", id, "date", "Date", null, ValueType.DATE, null, ColumnRole.DATE, null, 0, true, false),
                ColumnDef("${id}_week", id, "week", "Week", null, ValueType.WEEKDAY, null, ColumnRole.COMPUTED, "WEEKDAY(date)", 1, true, false),
                ColumnDef("${id}_count", id, "count", "Count", null, ValueType.NUMBER, "children", ColumnRole.INPUT, null, 2, true, false),
            ),
            rates = emptyList(),
        )
        saveTemplate(template)
        return template
    }

    suspend fun deleteTemplate(id: String) = dao.deleteTemplate(id)

    fun validateTemplate(template: LedgerTemplate): List<String> {
        val names = template.columns.map { it.key }.toSet() + template.rates.map { it.key }.toSet() + setOf("date")
        val errors = mutableListOf<String>()
        template.columns.forEach { column ->
            if (column.key.isBlank()) errors += "Column key is required"
            column.formula?.takeIf { it.isNotBlank() && it != "null" }?.let { formula ->
                errors += FormulaValidator.validate(formula, names)
            }
        }
        errors += FormulaValidator.hasCycle(template.columns)
        if (errors.isNotEmpty()) throw IllegalArgumentException(errors.distinct().joinToString("\n"))
        return errors
    }

    suspend fun saveColumn(column: ColumnDef) {
        val current = template(column.templateId) ?: error("Template not found")
        val previous = current.columns.firstOrNull { it.id == column.id }
        val rewritten = if (previous != null && previous.key != column.key) {
            current.copy(
                columns = (current.columns.filterNot { it.id == column.id } + column).map { item ->
                    val formula = item.formula ?: return@map item
                    if (item.id == column.id || !FormulaValidator.referencedColumns(formula).contains(previous.key)) item
                    else item.copy(formula = formula.replace(Regex("""\b${Regex.escape(previous.key)}\b"""), column.key))
                }.sortedBy { it.sortOrder },
            )
        } else {
            current.copy(columns = (current.columns.filterNot { it.id == column.id } + column).sortedBy { it.sortOrder })
        }
        validateTemplate(rewritten)
        if (previous != null && previous.key != column.key) {
            dao.renameCellKey(previous.key, column.key)
            dao.renameSourceCellKey(previous.key, column.key)
            dao.renameTextCellKey(previous.key, column.key)
            rewritten.columns.forEach { dao.upsertColumn(it.toEntity()) }
        } else {
            dao.upsertColumn(column.toEntity())
        }
    }

    suspend fun deleteColumn(templateId: String, columnId: String) {
        val current = template(templateId) ?: error("Template not found")
        val target = current.columns.first { it.id == columnId }
        val dependents = current.columns.filter { other ->
            other.id != columnId && FormulaValidator.referencedColumns(other.formula).contains(target.key)
        }
        if (dependents.isNotEmpty()) {
            throw IllegalStateException(
                "Cannot delete ${target.label}. Update formulas on: ${dependents.joinToString { it.label }}",
            )
        }
        dao.deleteColumn(columnId)
        dao.deleteCellsForColumn(target.key)
        dao.deleteSourceCellsForColumn(target.key)
        dao.deleteTextCellsForColumn(target.key)
    }

    suspend fun saveRate(rate: RateDef) {
        require(rate.value.isFinite()) { "Enter a valid number" }
        val current = template(rate.templateId) ?: error("Template not found")
        val previous = current.rates.firstOrNull { it.id == rate.id }
        val nextRates = (current.rates.filterNot { it.id == rate.id } + rate).sortedBy { it.sortOrder }
        val rewritten = if (previous != null && previous.key != rate.key) {
            rewriteRateFormulas(current.copy(rates = nextRates), previous.key, rate.key)
        } else {
            current.copy(rates = nextRates)
        }
        validateTemplate(rewritten)
        dao.upsertRate(rate.toEntity())
        if (previous != null && previous.key != rate.key) {
            rewritten.columns.forEach { dao.upsertColumn(it.toEntity()) }
        }
    }

    suspend fun deleteRate(id: String) {
        val owner = templates().firstOrNull { template -> template.rates.any { it.id == id } }
            ?: run {
                dao.deleteRate(id)
                return
            }
        val rate = owner.rates.first { it.id == id }
        val dependents = owner.columns.filter { FormulaValidator.referencedColumns(it.formula).contains(rate.key) }
        if (dependents.isNotEmpty()) {
            throw IllegalStateException(
                "Cannot delete ${rate.label}. Update formulas on: ${dependents.joinToString { it.label }}",
            )
        }
        dao.deleteRate(id)
    }

    suspend fun previewMonth(templateId: String, year: Int, month: Int): MonthCreatePreview {
        val existing = dao.getInstances().firstOrNull { it.templateId == templateId && it.year == year && it.month == month }
        return MonthCreatePreview(
            templateId = templateId,
            year = year,
            month = month,
            exists = existing != null,
            existingInstanceId = existing?.id,
            carryValues = if (existing == null) carryForward(templateId, year, month) else emptyMap(),
        )
    }

    suspend fun createMonth(templateId: String, year: Int, month: Int): LedgerInstance {
        val existing = dao.getInstances().firstOrNull { it.templateId == templateId && it.year == year && it.month == month }
        if (existing != null) return instance(existing.id)!!
        val template = template(templateId) ?: error("Template not found")
        val id = "${templateId}_${year}_${month.toString().padStart(2, '0')}"
        val title = "${template.name} ${YearMonth.of(year, month).month.name.lowercase().replaceFirstChar { it.titlecase() }} $year"
        dao.upsertInstance(InstanceEntity(id, templateId, year, month, title))
        val ym = YearMonth.of(year, month)
        val previousClosing = carryForward(templateId, year, month)
        val rows = (1..ym.lengthOfMonth()).map { day ->
            val date = ym.atDay(day).toString()
            val rowId = "${id}_$date"
            val cells = mutableMapOf<String, Double?>()
            if (day == 1) {
                template.columns.filter { it.role == ColumnRole.OPENING }.forEach { column ->
                    val from = FormulaValidator.referencedColumns(column.formula, includeCarry = true)
                        .firstOrNull { ref -> ref.endsWith("balance") || ref.contains("_balance") }
                    cells[column.key] = from?.let { previousClosing[it] } ?: previousClosing[column.key] ?: 0.0
                }
            }
            RowEntity(rowId, id, date, day) to cells
        }
        dao.upsertRows(rows.map { it.first })
        dao.upsertCells(
            rows.flatMap { (row, cells) ->
                cells.map { (key, value) -> CellEntity(row.id, key, value) }
            },
        )
        return instance(id)!!
    }

    suspend fun deleteInstance(id: String) = dao.deleteInstance(id)

    suspend fun addRow(instanceId: String, date: String): LedgerRow {
        val rowId = "${instanceId}_$date"
        val day = LocalDate.parse(date).dayOfMonth
        dao.upsertRow(RowEntity(rowId, instanceId, date, day))
        return LedgerRow(rowId, instanceId, date, day, emptyMap())
    }

    suspend fun deleteRow(rowId: String) = dao.deleteRow(rowId)

    fun parseNumericInput(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.toDoubleOrNull() ?: throw IllegalArgumentException("Enter a valid number")
    }

    suspend fun updateCell(rowId: String, columnKey: String, value: Double?) {
        dao.upsertCells(listOf(CellEntity(rowId, columnKey, value)))
    }

    suspend fun updateTextCell(rowId: String, columnKey: String, value: String) {
        if (value.isBlank()) {
            dao.deleteTextCell(rowId, columnKey)
            return
        }
        dao.upsertTextCells(listOf(TextCellEntity(rowId, columnKey, value)))
    }

    suspend fun previewSeedUpgrade(): SeedUpgradePreview {
        val bundled = bundledPayload()
        val currentVersion = seedVersion()
        val existingTemplates = dao.getTemplates().map { it.id }.toSet()
        val existingInstances = dao.getInstances().map { it.id }.toSet()
        val existingRows = dao.getAllRows().map { it.id }.toSet()
        return SeedUpgradePreview(
            currentVersion = currentVersion,
            bundledVersion = bundled.version,
            newTemplates = bundled.templates.count { it.id !in existingTemplates },
            newInstances = bundled.instances.count { it.id !in existingInstances },
            newRows = bundled.instances.sumOf { instance -> instance.rows.count { it.id !in existingRows } },
        )
    }

    suspend fun applySeedUpgrade() {
        inTransaction {
            val bundled = bundledPayload()
            maybeFail("after-seed-begin")
            val existingTemplates = dao.getTemplates().map { it.id }.toSet()
            val existingInstances = dao.getInstances().map { it.id }.toSet()
            val existingRows = dao.getAllRows().map { it.id }.toSet()
            bundled.templates.filter { it.id !in existingTemplates }.forEach { saveTemplate(it) }
            maybeFail("after-seed-templates")
            bundled.instances.filter { it.id !in existingInstances }.forEach { incoming ->
                dao.upsertInstance(InstanceEntity(incoming.id, incoming.templateId, incoming.year, incoming.month, incoming.title))
                mergeRows(incoming, existingRows)
            }
            bundled.instances.filter { it.id in existingInstances }.forEach { incoming ->
                mergeRows(incoming, existingRows)
            }
            maybeFail("after-seed-instances")
            persistCorrections(bundled.corrections)
            dao.upsertMeta(MetaEntity("seedVersion", bundled.version.toString()))
            dao.upsertMeta(MetaEntity("seeded", "true"))
        }
    }

    suspend fun replaceFromExcel(payload: SeedPayload) {
        val existing = exportArchive()
        val merged = payload.copy(
            grades = if (payload.grades.isEmpty()) existing.grades else payload.grades,
            templates = payload.templates.ifEmpty { existing.templates },
            instances = payload.instances,
            corrections = payload.corrections.ifEmpty { existing.corrections },
        )
        val json = JSONObject(SeedParser.toJson(merged))
            .put("archiveVersion", ARCHIVE_VERSION)
            .put("exportedAt", existing.exportedAt)
            .put("settings", SeedParser.settingsJson(existing.settings ?: settings()))
            .toString()
        restore(json)
    }

    suspend fun exportArchive(): BackupArchive {
        return BackupArchive(
            version = ARCHIVE_VERSION,
            exportedAt = LocalDate.now().toString(),
            seedVersion = seedVersion(),
            school = school(),
            grades = dao.getGrades().map { it.toDomain() },
            templates = templates(),
            instances = instances(),
            corrections = corrections(),
            settings = settings(),
        )
    }

    suspend fun exportJson(): String {
        val archive = exportArchive()
        val payload = SeedPayload(archive.seedVersion, archive.school, archive.grades, archive.templates, archive.instances, archive.corrections)
        val json = JSONObject(SeedParser.toJson(payload))
        json.put("archiveVersion", archive.version)
        json.put("exportedAt", archive.exportedAt)
        archive.settings?.let { json.put("settings", SeedParser.settingsJson(it)) }
        return json.toString(2)
    }

    fun previewRestore(json: String): RestorePreview {
        val payload = SeedParser.parse(json)
        require(payload.grades.isNotEmpty()) { "Backup has no classes" }
        require(payload.templates.isNotEmpty()) { "Backup has no ledgers" }
        payload.instances.forEach { instance ->
            require(payload.templates.any { it.id == instance.templateId }) { "Backup references a missing ledger" }
        }
        return RestorePreview(
            grades = payload.grades.size,
            templates = payload.templates.size,
            instances = payload.instances.size,
            rows = payload.instances.sumOf { it.rows.size },
            school = payload.school.name,
            archiveVersion = JSONObject(json).optInt("archiveVersion", 1),
            exportedAt = JSONObject(json).optString("exportedAt").ifBlank { null },
        )
    }

    suspend fun restore(json: String) {
        val root = JSONObject(json)
        val archiveVersion = root.optInt("archiveVersion", 1)
        val payload = SeedParser.parse(json)
        validatePayload(payload, archiveVersion)
        inTransaction {
            replaceFromPayload(payload)
            maybeFail("after-replace")
            dao.upsertMeta(MetaEntity("seeded", "true"))
            dao.upsertMeta(MetaEntity("seedVersion", payload.version.toString()))
            persistSchool(payload.school)
            persistCorrections(payload.corrections)
            val settings = SeedParser.parseSettings(root.optJSONObject("settings"))
            if (settings != null) saveSettings(settings)
            else saveSettings(AppSettings(payload.school))
        }
    }

    fun validatePayload(payload: SeedPayload, archiveVersion: Int = 1) {
        require(archiveVersion in 1..ARCHIVE_VERSION) { "This backup version is not supported" }
        require(payload.grades.isNotEmpty()) { "Backup has no classes" }
        require(payload.templates.isNotEmpty()) { "Backup has no ledgers" }
        requireUnique(payload.grades.map { it.id }, "Backup has duplicate class ids")
        requireUnique(payload.templates.map { it.id }, "Backup has duplicate ledger ids")
        requireUnique(payload.instances.map { it.id }, "Backup has duplicate month ids")
        requireUnique(payload.instances.flatMap { instance -> instance.rows.map { it.id } }, "Backup has duplicate day ids")
        requireUnique(payload.templates.flatMap { it.columns.map { column -> column.id } }, "Backup has duplicate column ids")
        requireUnique(payload.templates.flatMap { it.rates.map { rate -> rate.id } }, "Backup has duplicate rate ids")
        val gradeIds = payload.grades.map { it.id }.toSet()
        val templatesById = payload.templates.associateBy { it.id }
        payload.templates.forEach { template ->
            requireUnique(template.columns.map { it.key }, "Ledger ${template.name} has duplicate column keys")
            requireUnique(template.rates.map { it.key }, "Ledger ${template.name} has duplicate rate keys")
            template.gradeIds.forEach { gradeId ->
                require(gradeId in gradeIds) { "Ledger ${template.name} references a missing class" }
            }
            template.rates.forEach { rate ->
                require(rate.value.isFinite()) { "Rate ${rate.label} is not a valid number" }
            }
            template.columns.forEach { column ->
                require(column.key.isNotBlank()) { "A column key is blank" }
            }
            validateTemplate(template)
        }
        payload.instances.forEach { instance ->
            val template = templatesById[instance.templateId]
            require(template != null) { "Backup references a missing ledger" }
            require(instance.year in 2000..2100) { "Month year is invalid" }
            require(instance.month in 1..12) { "Month value is invalid" }
            val knownKeys = template.columns.map { it.key }.toSet()
            requireUnique(instance.rows.map { it.date }, "A month has duplicate dates")
            instance.rows.forEach { row ->
                val date = runCatching { LocalDate.parse(row.date) }.getOrNull()
                require(date != null) { "A day has an invalid date" }
                val monthStart = YearMonth.of(instance.year, instance.month).atDay(1)
                val overflow = YearMonth.of(instance.year, instance.month).plusMonths(1).atDay(1)
                require(!date.isBefore(monthStart) && !date.isAfter(overflow)) {
                    "A day is outside its month"
                }
                row.cells.keys.forEach { key ->
                    require(key in knownKeys) { "A cell uses an unknown column" }
                }
                row.sourceValues.keys.forEach { key ->
                    require(key in knownKeys) { "A source value uses an unknown column" }
                }
                row.textCells.keys.forEach { key ->
                    require(key in knownKeys) { "A text cell uses an unknown column" }
                }
                row.cells.values.filterNotNull().forEach { value ->
                    require(value.isFinite()) { "A cell contains an invalid number" }
                }
                row.sourceValues.values.filterNotNull().forEach { value ->
                    require(value.isFinite()) { "A source value is not a valid number" }
                }
            }
        }
    }

    suspend fun previewMerge(payload: SeedPayload): MergePreview {
        validatePayload(payload)
        val existing = instances()
        val existingIds = existing.map { it.id }.toSet()
        val incomingIds = payload.instances.map { it.id }.toSet()
        val existingRows = existing.flatMap { it.rows }.associateBy { it.id }
        var addedRows = 0
        var updatedRows = 0
        payload.instances.forEach { instance ->
            instance.rows.forEach { row ->
                val previous = existingRows[row.id]
                when {
                    previous == null -> addedRows++
                    previous.cells != row.cells -> updatedRows++
                }
            }
        }
        return MergePreview(
            school = payload.school.name,
            recognized = payload.instances.isNotEmpty(),
            addedInstances = payload.instances.count { it.id !in existingIds },
            updatedInstances = payload.instances.count { it.id in existingIds },
            unchangedInstances = existing.count { it.id in incomingIds && payload.instances.none { incoming -> incoming.id == it.id && incoming.rows != it.rows } },
            preservedInstances = existing.count { it.id !in incomingIds },
            addedRows = addedRows,
            updatedRows = updatedRows,
            wouldRemoveInstances = existing.count { it.id !in incomingIds },
        )
    }

    suspend fun mergePayload(payload: SeedPayload) {
        validatePayload(payload)
        inTransaction {
            payload.grades.forEach { saveGrade(it) }
            maybeFail("after-grades")
            payload.templates.forEach { incoming ->
                val current = template(incoming.id)
                if (current == null) {
                    saveTemplate(incoming)
                } else {
                    saveTemplate(mergeTemplate(current, incoming))
                }
            }
            maybeFail("after-templates")
            payload.instances.forEach { incoming ->
                dao.upsertInstance(InstanceEntity(incoming.id, incoming.templateId, incoming.year, incoming.month, incoming.title))
                incoming.rows.forEach { row ->
                    mergeMappedRow(incoming.id, row)
                }
            }
            maybeFail("after-instances")
            if (payload.corrections.isNotEmpty()) persistCorrections(payload.corrections)
            persistSchool(payload.school)
            dao.upsertMeta(MetaEntity("seeded", "true"))
        }
    }

    private fun mergeTemplate(current: LedgerTemplate, incoming: LedgerTemplate): LedgerTemplate {
        val incomingColumns = incoming.columns.associateBy { it.id.ifBlank { it.key } }
        val incomingByKey = incoming.columns.associateBy { it.key }
        val columns = current.columns.map { existing ->
            val match = incomingColumns[existing.id] ?: incomingByKey[existing.key]
            match?.copy(id = existing.id, templateId = existing.templateId) ?: existing
        } + incoming.columns.filter { column ->
            current.columns.none { it.id == column.id || it.key == column.key }
        }
        val incomingRates = incoming.rates.associateBy { it.id.ifBlank { it.key } }
        val incomingRateKeys = incoming.rates.associateBy { it.key }
        val rates = current.rates.map { existing ->
            val match = incomingRates[existing.id] ?: incomingRateKeys[existing.key]
            match?.copy(id = existing.id, templateId = existing.templateId) ?: existing
        } + incoming.rates.filter { rate ->
            current.rates.none { it.id == rate.id || it.key == rate.key }
        }
        return current.copy(
            name = incoming.name.ifBlank { current.name },
            description = incoming.description.ifBlank { current.description },
            sheetName = incoming.sheetName ?: current.sheetName,
            gradeIds = incoming.gradeIds.ifEmpty { current.gradeIds },
            columns = columns.sortedBy { it.sortOrder },
            rates = rates.sortedBy { it.sortOrder },
        )
    }

    private suspend fun mergeRows(incoming: LedgerInstance, existingRows: Set<String>) {
        incoming.rows.filter { it.id !in existingRows }.forEach { row ->
            mergeMappedRow(incoming.id, row)
        }
    }

    private suspend fun mergeMappedRow(instanceId: String, row: LedgerRow) {
        dao.upsertRow(RowEntity(row.id, instanceId, row.date, row.sortOrder))
        row.cells.forEach { (key, value) ->
            if (value == null) dao.deleteCell(row.id, key)
            else dao.upsertCells(listOf(CellEntity(row.id, key, value)))
        }
        row.sourceValues.forEach { (key, value) ->
            if (value == null) dao.deleteSourceCell(row.id, key)
            else dao.upsertSourceCells(listOf(SourceCellEntity(row.id, key, value)))
        }
        row.textCells.forEach { (key, value) ->
            if (value.isBlank()) dao.deleteTextCell(row.id, key)
            else dao.upsertTextCells(listOf(TextCellEntity(row.id, key, value)))
        }
    }

    private suspend fun rewriteFormulas(template: LedgerTemplate, oldKey: String, newKey: String) {
        template.columns.forEach { column ->
            val formula = column.formula ?: return@forEach
            if (!FormulaValidator.referencedColumns(formula).contains(oldKey)) return@forEach
            val rewritten = formula.replace(Regex("""\b${Regex.escape(oldKey)}\b"""), newKey)
            dao.upsertColumn(column.copy(formula = rewritten).toEntity())
        }
    }

    private fun rewriteRateFormulas(template: LedgerTemplate, oldKey: String, newKey: String): LedgerTemplate {
        return template.copy(
            columns = template.columns.map { column ->
                val formula = column.formula ?: return@map column
                if (!FormulaValidator.referencedColumns(formula).contains(oldKey)) column
                else column.copy(formula = formula.replace(Regex("""\b${Regex.escape(oldKey)}\b"""), newKey))
            },
        )
    }

    private suspend fun carryForward(templateId: String, year: Int, month: Int): Map<String, Double?> {
        val template = template(templateId) ?: return emptyMap()
        val previous = dao.getInstances()
            .filter { it.templateId == templateId }
            .sortedWith(compareBy({ it.year }, { it.month }))
            .lastOrNull { it.year < year || (it.year == year && it.month < month) }
            ?: return emptyMap()
        val prevInstance = instance(previous.id) ?: return emptyMap()
        val last = FormulaEngine().compute(template, prevInstance.rows).lastOrNull() ?: return emptyMap()
        return last.cells.mapValues { it.value.number }
    }

    private suspend fun persistSchool(school: SchoolProfile) {
        dao.upsertMeta(MetaEntity(META_SCHOOL_NAME, school.name))
        dao.upsertMeta(MetaEntity(META_SCHOOL_PROGRAM, school.program))
    }

    private suspend fun persistCorrections(notes: List<CorrectionNote>) {
        val payload = SeedParser.toJson(
            SeedPayload(1, SchoolProfile("", ""), emptyList(), emptyList(), emptyList(), notes),
        )
        val array = JSONObject(payload).optJSONArray("corrections")?.toString() ?: "[]"
        dao.upsertMeta(MetaEntity(META_CORRECTIONS, array))
    }

    private suspend fun replaceFromPayload(payload: SeedPayload) {
        dao.replaceAll(
            grades = payload.grades.map { it.toEntity() },
            templates = payload.templates.map { TemplateEntity(it.id, it.name, it.description, it.sheetName) },
            templateGrades = payload.templates.flatMap { template -> template.gradeIds.map { TemplateGradeEntity(template.id, it) } },
            columns = payload.templates.flatMap { template -> template.columns.map { it.toEntity() } },
            rates = payload.templates.flatMap { template -> template.rates.map { it.toEntity() } },
            instances = payload.instances.map { InstanceEntity(it.id, it.templateId, it.year, it.month, it.title) },
            rows = payload.instances.flatMap { instance ->
                instance.rows.map { RowEntity(it.id, instance.id, it.date, it.sortOrder) }
            },
            cells = payload.instances.flatMap { instance ->
                instance.rows.flatMap { row ->
                    row.cells.map { (key, value) -> CellEntity(row.id, key, value) }
                }
            },
            sourceCells = payload.instances.flatMap { instance ->
                instance.rows.flatMap { row ->
                    row.sourceValues.map { (key, value) -> SourceCellEntity(row.id, key, value) }
                }
            },
            textCells = payload.instances.flatMap { instance ->
                instance.rows.flatMap { row ->
                    row.textCells.map { (key, value) -> TextCellEntity(row.id, key, value) }
                }
            },
        )
    }

    private suspend fun <T> inTransaction(block: suspend () -> T): T {
        val db = database
        return if (db != null) db.withTransaction { block() } else block()
    }

    private fun maybeFail(step: String) {
        if (testInterrupt == step) error("Interrupted at $step")
    }

    private fun requireUnique(values: List<String>, message: String) {
        require(values.size == values.toSet().size) { message }
    }

    companion object {
        const val ARCHIVE_VERSION = 2
        private const val META_SCHOOL_NAME = "schoolName"
        private const val META_SCHOOL_PROGRAM = "schoolProgram"
        private const val META_CORRECTIONS = "corrections"
        private const val META_THEME = "themeMode"
        private const val META_DENSITY = "tableDensity"
        private const val META_LANGUAGE = "language"
        private const val META_EXPORT = "defaultExport"
        private const val META_PALETTE = "colorPalette"
        private const val META_ZOOM = "ledgerZoom"
    }
}

data class RestorePreview(
    val grades: Int,
    val templates: Int,
    val instances: Int,
    val rows: Int,
    val school: String,
    val archiveVersion: Int = 1,
    val exportedAt: String? = null,
)
