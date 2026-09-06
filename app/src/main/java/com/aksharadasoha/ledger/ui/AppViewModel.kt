package com.aksharadasoha.ledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aksharadasoha.ledger.LedgerApplication
import com.aksharadasoha.ledger.data.LedgerRepository
import com.aksharadasoha.ledger.data.RestorePreview
import com.aksharadasoha.ledger.data.toDomain
import com.aksharadasoha.ledger.domain.AppSettings
import com.aksharadasoha.ledger.domain.ColumnDef
import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.ComputedRow
import com.aksharadasoha.ledger.domain.CorrectionNote
import com.aksharadasoha.ledger.domain.ExportFormat
import com.aksharadasoha.ledger.domain.FormulaEngine
import com.aksharadasoha.ledger.domain.Grade
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.MonthCreatePreview
import com.aksharadasoha.ledger.domain.MonthlyReport
import com.aksharadasoha.ledger.domain.OperationState
import com.aksharadasoha.ledger.domain.RateDef
import com.aksharadasoha.ledger.domain.ReportBuilder
import com.aksharadasoha.ledger.domain.SchoolProfile
import com.aksharadasoha.ledger.domain.SeedUpgradePreview
import com.aksharadasoha.ledger.domain.ValueType
import com.aksharadasoha.ledger.export.CombinedExport
import com.aksharadasoha.ledger.export.CsvExporter
import com.aksharadasoha.ledger.export.ExcelExportRequest
import com.aksharadasoha.ledger.export.ExcelImportPreview
import com.aksharadasoha.ledger.export.LedgerExcelExporter
import com.aksharadasoha.ledger.export.LedgerExcelImporter
import java.io.InputStream
import java.io.OutputStream
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val school: SchoolProfile = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
    val grades: List<Grade> = emptyList(),
    val templates: List<LedgerTemplate> = emptyList(),
    val instances: List<LedgerInstance> = emptyList(),
    val selectedGradeId: String? = null,
    val selectedTemplateId: String? = null,
    val ready: Boolean = false,
    val message: String? = null,
    val corrections: List<CorrectionNote> = emptyList(),
    val monthPicker: MonthCreatePreview? = null,
    val confirmDeleteInstanceId: String? = null,
    val settings: AppSettings = AppSettings(SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger")),
)

data class LedgerUiState(
    val template: LedgerTemplate? = null,
    val instance: LedgerInstance? = null,
    val rows: List<ComputedRow> = emptyList(),
    val editing: Pair<String, String>? = null,
    val confirmDeleteRowId: String? = null,
    val message: String? = null,
    val cellError: Pair<String, String>? = null,
    val showAudit: Boolean = false,
    val busy: Boolean = false,
    val pendingSave: Boolean = false,
)

data class ConfigState(
    val templates: List<LedgerTemplate> = emptyList(),
    val grades: List<Grade> = emptyList(),
    val selected: LedgerTemplate? = null,
    val preview: ComputedRow? = null,
    val message: String? = null,
    val confirmDeleteColumnId: String? = null,
    val confirmDeleteTemplateId: String? = null,
    val confirmDeleteGradeId: String? = null,
    val confirmDeleteRateId: String? = null,
)

data class BackupState(
    val corrections: List<CorrectionNote> = emptyList(),
    val preview: RestorePreview? = null,
    val pendingJson: String? = null,
    val excelPreview: ExcelImportPreview? = null,
    val seedUpgrade: SeedUpgradePreview? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val confirmReplace: Boolean = false,
)

data class ReportsState(
    val reports: List<MonthlyReport> = emptyList(),
    val selectedTemplateId: String? = null,
    val selectedYear: Int? = null,
    val selectedMonth: Int? = null,
    val message: String? = null,
)

enum class DownloadKind { PDF, EXCEL, CSV, BOTH_ZIP, REPORT_PDF, REPORT_EXCEL, REPORT_CSV }

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: LedgerRepository = (application as LedgerApplication).repository
    private val engine = FormulaEngine()

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home

    private val _ledger = MutableStateFlow(LedgerUiState())
    val ledger: StateFlow<LedgerUiState> = _ledger

    private val _config = MutableStateFlow(ConfigState())
    val config: StateFlow<ConfigState> = _config

    private val _backup = MutableStateFlow(BackupState())
    val backup: StateFlow<BackupState> = _backup

    private val _reports = MutableStateFlow(ReportsState())
    val reports: StateFlow<ReportsState> = _reports

    private val _operation = MutableStateFlow<OperationState>(OperationState.Idle)
    val operation: StateFlow<OperationState> = _operation

    init {
        viewModelScope.launch {
            runCatching { repository.seedIfNeeded() }
                .onFailure { _home.update { state -> state.copy(message = it.message, ready = true) } }
            combine(repository.grades, repository.instances) { _, _ -> refreshHome() }
                .collect {}
        }
    }

    fun consumeOperation() {
        _operation.value = OperationState.Idle
    }

    fun reportSuccess(message: String) {
        _operation.value = OperationState.Success(message)
    }

    fun reportFailure(message: String) {
        _operation.value = OperationState.Failure(message)
    }

    fun beginOperation() {
        _operation.value = OperationState.Running
    }

    fun selectGrade(id: String?) = _home.update { it.copy(selectedGradeId = id, selectedTemplateId = null) }

    fun selectTemplate(id: String?) = _home.update { it.copy(selectedTemplateId = id) }

    fun openLedger(instanceId: String) {
        viewModelScope.launch { loadLedger(instanceId) }
    }

    fun openLatest(templateId: String) {
        viewModelScope.launch {
            val latest = repository.instances().filter { it.templateId == templateId }.maxWithOrNull(compareBy({ it.year }, { it.month }))
            if (latest != null) loadLedger(latest.id)
            else _home.update { it.copy(message = "No month exists yet for this ledger") }
        }
    }

    fun beginCreateMonth(templateId: String) {
        viewModelScope.launch {
            val now = YearMonth.now()
            _home.update { it.copy(monthPicker = repository.previewMonth(templateId, now.year, now.monthValue)) }
        }
    }

    fun updateMonthPicker(year: Int? = null, month: Int? = null) {
        val current = _home.value.monthPicker ?: return
        viewModelScope.launch {
            _home.update {
                it.copy(
                    monthPicker = repository.previewMonth(
                        current.templateId,
                        year ?: current.year,
                        month ?: current.month,
                    ),
                )
            }
        }
    }

    fun confirmCreateMonth() {
        val picker = _home.value.monthPicker ?: return
        viewModelScope.launch {
            val created = repository.createMonth(picker.templateId, picker.year, picker.month)
            _home.update { it.copy(monthPicker = null) }
            refreshHome()
            loadLedger(created.id)
        }
    }

    fun dismissMonthPicker() = _home.update { it.copy(monthPicker = null) }

    fun requestDeleteInstance(id: String?) = _home.update { it.copy(confirmDeleteInstanceId = id) }

    fun confirmDeleteInstance() {
        viewModelScope.launch {
            val id = _home.value.confirmDeleteInstanceId ?: return@launch
            repository.deleteInstance(id)
            _home.update { it.copy(confirmDeleteInstanceId = null) }
            refreshHome()
            refreshReports()
        }
    }

    fun createMonth(templateId: String, year: Int = YearMonth.now().year, month: Int = YearMonth.now().monthValue) {
        viewModelScope.launch {
            val created = repository.createMonth(templateId, year, month)
            refreshHome()
            loadLedger(created.id)
        }
    }

    fun updateCell(rowId: String, columnKey: String, raw: String) {
        viewModelScope.launch {
            _ledger.update { it.copy(pendingSave = true) }
            runCatching {
                val template = _ledger.value.template
                val column = template?.columns?.firstOrNull { it.key == columnKey }
                if (column?.valueType == ValueType.TEXT) {
                    repository.updateTextCell(rowId, columnKey, raw)
                } else {
                    val value = repository.parseNumericInput(raw)
                    repository.updateCell(rowId, columnKey, value)
                }
                _ledger.value.instance?.id?.let { loadLedger(it) }
            }.onFailure { error ->
                _ledger.update { it.copy(message = error.message ?: "Enter a valid number", pendingSave = false) }
            }
        }
    }

    fun addDay() {
        viewModelScope.launch {
            val instance = _ledger.value.instance ?: return@launch
            val existing = instance.rows.map { it.date }.toSet()
            val month = YearMonth.of(instance.year, instance.month)
            val next = (1..month.lengthOfMonth()).map { month.atDay(it).toString() }.firstOrNull { it !in existing }
            if (next == null) {
                _ledger.update { it.copy(message = "This month already has every day") }
                return@launch
            }
            repository.addRow(instance.id, next)
            loadLedger(instance.id)
        }
    }

    fun requestDeleteRow(rowId: String?) = _ledger.update { it.copy(confirmDeleteRowId = rowId) }

    fun confirmDeleteRow() {
        viewModelScope.launch {
            val rowId = _ledger.value.confirmDeleteRowId ?: return@launch
            repository.deleteRow(rowId)
            _ledger.update { it.copy(confirmDeleteRowId = null) }
            _ledger.value.instance?.id?.let { loadLedger(it) }
        }
    }

    fun showCellError(message: String?) = _ledger.update { it.copy(cellError = message?.let { text -> "Error" to text }) }

    fun toggleAudit() = _ledger.update { it.copy(showAudit = !it.showAudit) }

    fun consumeLedgerMessage() = _ledger.update { it.copy(message = null, cellError = null) }

    fun loadConfig(templateId: String? = _config.value.selected?.id) {
        viewModelScope.launch {
            val templates = repository.templates()
            val selected = templates.firstOrNull { it.id == templateId } ?: templates.firstOrNull()
            val gradeList = getApplication<LedgerApplication>().database.dao().getGrades().map { it.toDomain() }
            _config.update {
                it.copy(
                    templates = templates,
                    grades = gradeList,
                    selected = selected,
                    preview = selected?.let { template -> engine.preview(template) },
                )
            }
        }
    }

    fun selectConfigTemplate(id: String) {
        val selected = _config.value.templates.firstOrNull { it.id == id }
        _config.update { it.copy(selected = selected, preview = selected?.let { template -> engine.preview(template) }) }
    }

    fun saveTemplateName(name: String, description: String, gradeIds: List<String>) {
        viewModelScope.launch {
            val current = _config.value.selected ?: return@launch
            runCatching {
                repository.saveTemplate(current.copy(name = name, description = description, gradeIds = gradeIds))
                loadConfig(current.id)
                refreshHome()
            }.onFailure { error -> _config.update { it.copy(message = error.message) } }
        }
    }

    fun addColumn() {
        val current = _config.value.selected ?: return
        val key = "col_${UUID.randomUUID().toString().take(6)}"
        val column = ColumnDef(
            id = "${current.id}_$key",
            templateId = current.id,
            key = key,
            label = "New column",
            groupLabel = null,
            valueType = ValueType.NUMBER,
            unit = null,
            role = ColumnRole.INPUT,
            formula = null,
            sortOrder = current.columns.size,
            visible = true,
            warnNegative = false,
        )
        viewModelScope.launch {
            runCatching {
                repository.saveColumn(column)
                loadConfig(current.id)
            }.onFailure { error -> _config.update { it.copy(message = error.message) } }
        }
    }

    fun saveColumn(column: ColumnDef) {
        viewModelScope.launch {
            runCatching {
                repository.saveColumn(column)
                loadConfig(column.templateId)
            }.onFailure { error -> _config.update { it.copy(message = error.message) } }
        }
    }

    fun requestDeleteColumn(id: String?) = _config.update { it.copy(confirmDeleteColumnId = id) }

    fun confirmDeleteColumn() {
        viewModelScope.launch {
            val template = _config.value.selected ?: return@launch
            val columnId = _config.value.confirmDeleteColumnId ?: return@launch
            runCatching {
                repository.deleteColumn(template.id, columnId)
                _config.update { it.copy(confirmDeleteColumnId = null) }
                loadConfig(template.id)
            }.onFailure { error -> _config.update { it.copy(message = error.message, confirmDeleteColumnId = null) } }
        }
    }

    fun moveColumn(columnId: String, delta: Int) {
        val template = _config.value.selected ?: return
        val ordered = template.columns.sortedBy { it.sortOrder }.toMutableList()
        val index = ordered.indexOfFirst { it.id == columnId }
        val target = index + delta
        if (index < 0 || target !in ordered.indices) return
        val item = ordered.removeAt(index)
        ordered.add(target, item)
        viewModelScope.launch {
            ordered.forEachIndexed { sort, column -> repository.saveColumn(column.copy(sortOrder = sort)) }
            loadConfig(template.id)
        }
    }

    fun saveRate(rate: RateDef) {
        viewModelScope.launch {
            runCatching {
                repository.saveRate(rate)
                loadConfig(rate.templateId)
            }.onFailure { error -> _config.update { it.copy(message = error.message) } }
        }
    }

    fun addRate() {
        val template = _config.value.selected ?: return
        val key = "rate_${UUID.randomUUID().toString().take(5)}"
        viewModelScope.launch {
            runCatching {
                repository.saveRate(RateDef("${template.id}_$key", template.id, key, "New rate", 0.0, null, template.rates.size))
                loadConfig(template.id)
            }.onFailure { error -> _config.update { it.copy(message = error.message) } }
        }
    }

    fun requestDeleteRate(id: String?) = _config.update { it.copy(confirmDeleteRateId = id) }

    fun deleteRate(id: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteRate(id)
                _config.update { it.copy(confirmDeleteRateId = null) }
                _config.value.selected?.id?.let { loadConfig(it) }
            }.onFailure { error -> _config.update { it.copy(message = error.message, confirmDeleteRateId = null) } }
        }
    }

    fun addGrade(name: String) {
        viewModelScope.launch {
            val id = "grade_${name.lowercase().replace(" ", "_")}"
            repository.saveGrade(Grade(id, name, _home.value.grades.size))
            refreshHome()
            loadConfig()
        }
    }

    fun requestDeleteGrade(id: String?) = _config.update { it.copy(confirmDeleteGradeId = id) }

    fun deleteGrade(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteGrade(id) }
                .onFailure { error -> _config.update { it.copy(message = error.message) } }
            _config.update { it.copy(confirmDeleteGradeId = null) }
            refreshHome()
            loadConfig()
        }
    }

    fun createTemplate(name: String, description: String, gradeIds: List<String>) {
        viewModelScope.launch {
            val created = repository.createTemplate(name, description, gradeIds)
            loadConfig(created.id)
            refreshHome()
        }
    }

    fun requestDeleteTemplate(id: String?) = _config.update { it.copy(confirmDeleteTemplateId = id) }

    fun deleteTemplate(id: String) {
        viewModelScope.launch {
            repository.deleteTemplate(id)
            _config.update { it.copy(confirmDeleteTemplateId = null) }
            loadConfig()
            refreshHome()
        }
    }

    suspend fun exportJson(): String = repository.exportJson()

    fun previewRestore(json: String): RestorePreview = repository.previewRestore(json)

    fun setPendingRestore(json: String?) {
        _backup.update {
            val preview = json?.let { raw -> runCatching { repository.previewRestore(raw) }.getOrNull() }
            it.copy(
                pendingJson = json,
                preview = preview,
                error = if (json != null && preview == null) "This file is not a valid ledger backup" else null,
            )
        }
    }

    suspend fun restore(json: String) {
        _operation.value = OperationState.Running
        runCatching {
            repository.restore(json)
            refreshHome()
            refreshReports()
            loadBackupExtras()
        }.onSuccess {
            _operation.value = OperationState.Success("Backup restored")
        }.onFailure { error ->
            _operation.value = OperationState.Failure(error.message ?: "Could not restore this backup")
            throw error
        }
    }

    fun loadBackupExtras() {
        viewModelScope.launch {
            _backup.update {
                it.copy(
                    corrections = repository.corrections(),
                    seedUpgrade = runCatching { repository.previewSeedUpgrade() }.getOrNull(),
                    busy = false,
                )
            }
        }
    }

    fun applySeedUpgrade() {
        viewModelScope.launch {
            _backup.update { it.copy(busy = true) }
            _operation.value = OperationState.Running
            runCatching { repository.applySeedUpgrade() }
                .onSuccess {
                    _backup.update { it.copy(busy = false, message = "Workbook data merged") }
                    _operation.value = OperationState.Success("Workbook data merged")
                }
                .onFailure { error ->
                    _backup.update { it.copy(busy = false, error = error.message) }
                    _operation.value = OperationState.Failure(error.message ?: "Could not merge workbook data")
                }
            refreshHome()
            loadBackupExtras()
        }
    }

    fun previewExcel(input: InputStream) {
        viewModelScope.launch {
            runCatching {
                val bytes = input.readBytes()
                val templates = repository.templates()
                val payload = LedgerExcelImporter.import(
                    bytes.inputStream(),
                    templates,
                    _home.value.grades,
                    repository.school(),
                    repository.corrections(),
                )
                val merge = repository.previewMerge(payload)
                val basic = LedgerExcelImporter.preview(bytes.inputStream(), templates)
                basic.copy(
                    addedInstances = merge.addedInstances,
                    updatedInstances = merge.updatedInstances,
                    unchangedInstances = merge.unchangedInstances,
                    preservedInstances = merge.preservedInstances,
                    wouldRemoveInstances = merge.wouldRemoveInstances,
                    conflicts = merge.conflicts,
                )
            }.onSuccess { preview ->
                _backup.update { it.copy(excelPreview = preview, confirmReplace = false, error = null) }
            }.onFailure { error ->
                _backup.update { it.copy(error = error.message ?: "This Excel file could not be read") }
                _operation.value = OperationState.Failure(error.message ?: "This Excel file could not be read")
            }
        }
    }

    fun importExcel(input: InputStream, replace: Boolean = false) {
        viewModelScope.launch {
            if (replace && !_backup.value.confirmReplace) {
                _backup.update { it.copy(confirmReplace = true) }
                return@launch
            }
            _backup.update { it.copy(busy = true) }
            _operation.value = OperationState.Running
            runCatching {
                val payload = LedgerExcelImporter.import(
                    input,
                    repository.templates(),
                    _home.value.grades,
                    repository.school(),
                    repository.corrections(),
                )
                if (replace) {
                    repository.replaceFromExcel(payload)
                } else {
                    repository.mergePayload(payload)
                }
            }.onSuccess {
                _backup.update { it.copy(busy = false, excelPreview = null, confirmReplace = false, message = "Excel imported") }
                _operation.value = OperationState.Success(
                    if (replace) "Excel replaced local data" else "Excel merged without changing other months",
                )
                refreshHome()
                refreshReports()
            }.onFailure { error ->
                _backup.update { it.copy(busy = false, error = error.message, confirmReplace = false) }
                _operation.value = OperationState.Failure(error.message ?: "This Excel file could not be imported")
            }
        }
    }

    fun cancelReplaceConfirm() = _backup.update { it.copy(confirmReplace = false) }

    fun consumeBackupMessage() = _backup.update { it.copy(message = null, error = null) }

    fun previewSettings(settings: AppSettings) {
        _home.update { it.copy(settings = settings) }
    }

    fun saveSettings(settings: AppSettings, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _operation.value = OperationState.Running
            runCatching {
                repository.saveSettings(settings)
                refreshHome()
            }.onSuccess {
                _operation.value = OperationState.Success("Settings saved")
                onResult(true)
            }.onFailure { error ->
                refreshHome()
                _operation.value = OperationState.Failure(error.message ?: "Could not save settings")
                onResult(false)
            }
        }
    }

    fun filterReports(templateId: String?, year: Int? = _reports.value.selectedYear, month: Int? = _reports.value.selectedMonth) {
        _reports.update { it.copy(selectedTemplateId = templateId, selectedYear = year, selectedMonth = month) }
        viewModelScope.launch { refreshReports() }
    }

    fun discardSettingsPreview() {
        viewModelScope.launch { refreshHome() }
    }

    fun updateLedgerZoom(zoom: Float) {
        val next = com.aksharadasoha.ledger.domain.LedgerZoom.clamp(zoom)
        val current = _home.value.settings
        if (current.ledgerZoom == next) return
        _home.update { it.copy(settings = current.copy(ledgerZoom = next)) }
        viewModelScope.launch {
            runCatching { repository.saveSettings(_home.value.settings.copy(ledgerZoom = next)) }
        }
    }

    fun defaultDownloadKind(): DownloadKind {
        return when (_home.value.settings.defaultExport) {
            ExportFormat.EXCEL -> DownloadKind.EXCEL
            ExportFormat.BOTH -> DownloadKind.BOTH_ZIP
            ExportFormat.PDF -> DownloadKind.PDF
        }
    }

    suspend fun writeDownload(kind: DownloadKind, out: OutputStream) {
        when (kind) {
            DownloadKind.PDF -> {
                val template = _ledger.value.template ?: return
                val instance = _ledger.value.instance ?: return
                com.aksharadasoha.ledger.print.LedgerPrinter.writePdf(
                    getApplication(),
                    _home.value.school,
                    template,
                    instance,
                    _ledger.value.rows,
                    out,
                )
            }
            DownloadKind.EXCEL -> {
                val template = _ledger.value.template ?: return
                val instance = _ledger.value.instance ?: return
                LedgerExcelExporter.write(
                    ExcelExportRequest(
                        school = _home.value.school,
                        templates = listOf(template),
                        instances = listOf(instance),
                        computed = mapOf(instance.id to _ledger.value.rows),
                        corrections = _home.value.corrections,
                    ),
                    out,
                )
            }
            DownloadKind.CSV -> {
                val template = _ledger.value.template ?: return
                val instance = _ledger.value.instance ?: return
                out.write(CsvExporter.ledger(template, instance, _ledger.value.rows).toByteArray())
            }
            DownloadKind.BOTH_ZIP -> {
                val template = _ledger.value.template ?: return
                val instance = _ledger.value.instance ?: return
                val pdf = java.io.ByteArrayOutputStream()
                com.aksharadasoha.ledger.print.LedgerPrinter.writePdf(
                    getApplication(),
                    _home.value.school,
                    template,
                    instance,
                    _ledger.value.rows,
                    pdf,
                )
                val xlsx = java.io.ByteArrayOutputStream()
                LedgerExcelExporter.write(
                    ExcelExportRequest(
                        school = _home.value.school,
                        templates = listOf(template),
                        instances = listOf(instance),
                        computed = mapOf(instance.id to _ledger.value.rows),
                        corrections = _home.value.corrections,
                    ),
                    xlsx,
                )
                CombinedExport.writeZip("${instance.id}.pdf", pdf.toByteArray(), "${instance.id}.xlsx", xlsx.toByteArray(), out)
            }
            DownloadKind.REPORT_PDF -> {
                com.aksharadasoha.ledger.print.ReportPrinter.writePdf(getApplication(), _home.value.school, _reports.value.reports, out)
            }
            DownloadKind.REPORT_EXCEL -> {
                val templates = repository.templates()
                val instances = repository.instances().filter { instance -> _reports.value.reports.any { it.instanceId == instance.id } }
                val computed = instances.associate { instance ->
                    val template = templates.first { it.id == instance.templateId }
                    instance.id to engine.compute(template, instance.rows)
                }
                LedgerExcelExporter.write(
                    ExcelExportRequest(_home.value.school, templates, instances, computed, _home.value.corrections, reports = _reports.value.reports),
                    out,
                )
            }
            DownloadKind.REPORT_CSV -> out.write(CsvExporter.reports(_reports.value.reports).toByteArray())
        }
    }

    fun consumeMessage() {
        _home.update { it.copy(message = null) }
        _config.update { it.copy(message = null) }
    }

    private suspend fun refreshHome() {
        val settings = repository.settings()
        _home.update {
            it.copy(
                school = settings.school,
                grades = getApplication<LedgerApplication>().database.dao().getGrades().map { it.toDomain() },
                templates = repository.templates(),
                instances = repository.instances(),
                corrections = repository.corrections(),
                settings = settings,
                ready = true,
            )
        }
        refreshReports()
        _backup.update { it.copy(corrections = repository.corrections()) }
    }

    private suspend fun refreshReports() {
        val templates = repository.templates()
        val instances = repository.instances()
        val selected = _reports.value.selectedTemplateId
        val year = _reports.value.selectedYear
        val month = _reports.value.selectedMonth
        val reports = instances
            .filter { selected == null || it.templateId == selected }
            .filter { year == null || it.year == year }
            .filter { month == null || it.month == month }
            .mapNotNull { instance ->
                val template = templates.firstOrNull { it.id == instance.templateId } ?: return@mapNotNull null
                ReportBuilder.monthly(template, instance, engine.compute(template, instance.rows))
            }
        _reports.update { it.copy(reports = reports) }
    }

    private suspend fun loadLedger(instanceId: String) {
        _ledger.update { it.copy(busy = true, pendingSave = true) }
        val instance = repository.instance(instanceId) ?: return
        val template = repository.template(instance.templateId) ?: return
        _ledger.value = LedgerUiState(
            template = template,
            instance = instance,
            rows = engine.compute(template, instance.rows),
            showAudit = _ledger.value.showAudit,
        )
    }
}
