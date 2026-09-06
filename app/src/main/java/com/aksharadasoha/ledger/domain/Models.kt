package com.aksharadasoha.ledger.domain

data class Grade(
    val id: String,
    val name: String,
    val sortOrder: Int,
)

enum class ValueType { NUMBER, DATE, TEXT, WEEKDAY }

enum class ColumnRole { DATE, INPUT, OPENING, COMPUTED }

data class ColumnDef(
    val id: String,
    val templateId: String,
    val key: String,
    val label: String,
    val groupLabel: String?,
    val valueType: ValueType,
    val unit: String?,
    val role: ColumnRole,
    val formula: String?,
    val sortOrder: Int,
    val visible: Boolean,
    val warnNegative: Boolean,
)

data class RateDef(
    val id: String,
    val templateId: String,
    val key: String,
    val label: String,
    val value: Double,
    val unit: String?,
    val sortOrder: Int,
)

data class LedgerTemplate(
    val id: String,
    val name: String,
    val description: String,
    val sheetName: String?,
    val gradeIds: List<String>,
    val columns: List<ColumnDef>,
    val rates: List<RateDef>,
)

data class LedgerRow(
    val id: String,
    val instanceId: String,
    val date: String,
    val sortOrder: Int,
    val cells: Map<String, Double?>,
    val sourceValues: Map<String, Double?> = emptyMap(),
    val textCells: Map<String, String> = emptyMap(),
)

data class LedgerInstance(
    val id: String,
    val templateId: String,
    val year: Int,
    val month: Int,
    val title: String,
    val rows: List<LedgerRow>,
)

data class SchoolProfile(
    val name: String,
    val program: String,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TableDensity { COMPACT, COMFORTABLE }

enum class ExportFormat { PDF, EXCEL, BOTH }

enum class ColorPalette { DYNAMIC, FRESH_GREEN, OCEAN_BLUE }

object LedgerZoom {
    const val MIN = 0.8f
    const val MAX = 1.5f
    const val DEFAULT = 1.0f
    const val STEP = 0.1f

    fun clamp(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN, MAX) else DEFAULT
}

data class AppSettings(
    val school: SchoolProfile,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val tableDensity: TableDensity = TableDensity.COMPACT,
    val language: String = "en",
    val defaultExport: ExportFormat = ExportFormat.PDF,
    val colorPalette: ColorPalette = ColorPalette.DYNAMIC,
    val ledgerZoom: Float = LedgerZoom.DEFAULT,
)

data class MonthCreatePreview(
    val templateId: String,
    val year: Int,
    val month: Int,
    val exists: Boolean,
    val existingInstanceId: String? = null,
    val carryValues: Map<String, Double?> = emptyMap(),
)

data class SeedUpgradePreview(
    val currentVersion: Int,
    val bundledVersion: Int,
    val newTemplates: Int,
    val newInstances: Int,
    val newRows: Int,
) {
    val available: Boolean get() = bundledVersion > currentVersion && (newTemplates + newInstances + newRows) > 0
}

data class CorrectionNote(
    val item: String,
    val workbook: Double,
    val app: Double,
    val reason: String,
)

data class SeedPayload(
    val version: Int,
    val school: SchoolProfile,
    val grades: List<Grade>,
    val templates: List<LedgerTemplate>,
    val instances: List<LedgerInstance>,
    val corrections: List<CorrectionNote>,
)

data class BackupArchive(
    val version: Int,
    val exportedAt: String,
    val seedVersion: Int,
    val school: SchoolProfile,
    val grades: List<Grade>,
    val templates: List<LedgerTemplate>,
    val instances: List<LedgerInstance>,
    val corrections: List<CorrectionNote>,
    val settings: AppSettings? = null,
)

data class MergePreview(
    val school: String,
    val sheets: Int = 0,
    val rates: Int = 0,
    val recognized: Boolean,
    val addedInstances: Int,
    val updatedInstances: Int,
    val unchangedInstances: Int,
    val preservedInstances: Int,
    val addedRows: Int,
    val updatedRows: Int,
    val wouldRemoveInstances: Int,
    val conflicts: List<String> = emptyList(),
)

sealed class OperationState {
    data object Idle : OperationState()
    data object Running : OperationState()
    data class Success(val message: String) : OperationState()
    data class Failure(val message: String) : OperationState()
}

data class ComputedCell(
    val key: String,
    val number: Double? = null,
    val text: String? = null,
    val error: String? = null,
    val negativeWarning: Boolean = false,
    val sourceNumber: Double? = null,
    val auditDiffers: Boolean = false,
)

data class MonthlyReport(
    val templateId: String,
    val templateName: String,
    val instanceId: String,
    val title: String,
    val year: Int,
    val month: Int,
    val days: Int,
    val childrenServed: Double,
    val items: List<ReportItem>,
    val auditDifferences: Int = 0,
)

data class ReportItem(
    val key: String,
    val label: String,
    val group: String?,
    val opening: Double?,
    val supply: Double?,
    val expenditure: Double?,
    val closing: Double?,
    val unit: String?,
)

data class ComputedRow(
    val row: LedgerRow,
    val cells: Map<String, ComputedCell>,
)
