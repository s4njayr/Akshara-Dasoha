package com.aksharadasoha.ledger.data

import com.aksharadasoha.ledger.domain.ColumnDef
import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.Grade
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerRow
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.RateDef
import com.aksharadasoha.ledger.domain.ValueType

fun GradeEntity.toDomain() = Grade(id, name, sortOrder)

fun Grade.toEntity() = GradeEntity(id, name, sortOrder)

fun ColumnEntity.toDomain() = ColumnDef(
    id = id,
    templateId = templateId,
    key = key,
    label = label,
    groupLabel = groupLabel,
    valueType = ValueType.valueOf(valueType),
    unit = unit,
    role = ColumnRole.valueOf(role),
    formula = formula,
    sortOrder = sortOrder,
    visible = visible,
    warnNegative = warnNegative,
)

fun ColumnDef.toEntity() = ColumnEntity(
    id = id,
    templateId = templateId,
    key = key,
    label = label,
    groupLabel = groupLabel,
    valueType = valueType.name,
    unit = unit,
    role = role.name,
    formula = formula,
    sortOrder = sortOrder,
    visible = visible,
    warnNegative = warnNegative,
)

fun RateEntity.toDomain() = RateDef(id, templateId, key, label, value, unit, sortOrder)

fun RateDef.toEntity() = RateEntity(id, templateId, key, label, value, unit, sortOrder)

fun assembleTemplate(
    template: TemplateEntity,
    gradeIds: List<String>,
    columns: List<ColumnEntity>,
    rates: List<RateEntity>,
): LedgerTemplate {
    return LedgerTemplate(
        id = template.id,
        name = template.name,
        description = template.description,
        sheetName = template.sheetName,
        gradeIds = gradeIds,
        columns = columns.map { it.toDomain() }.sortedBy { it.sortOrder },
        rates = rates.map { it.toDomain() }.sortedBy { it.sortOrder },
    )
}

fun assembleInstance(
    instance: InstanceEntity,
    rows: List<RowEntity>,
    cells: List<CellEntity>,
    sourceCells: List<SourceCellEntity> = emptyList(),
    textCells: List<TextCellEntity> = emptyList(),
): LedgerInstance {
    val byRow = cells.groupBy { it.rowId }
    val sourceByRow = sourceCells.groupBy { it.rowId }
    val textByRow = textCells.groupBy { it.rowId }
    return LedgerInstance(
        id = instance.id,
        templateId = instance.templateId,
        year = instance.year,
        month = instance.month,
        title = instance.title,
        rows = rows.sortedBy { it.date }.map { row ->
            LedgerRow(
                id = row.id,
                instanceId = row.instanceId,
                date = row.date,
                sortOrder = row.sortOrder,
                cells = byRow[row.id].orEmpty().associate { it.columnKey to it.value },
                sourceValues = sourceByRow[row.id].orEmpty().associate { it.columnKey to it.value },
                textCells = textByRow[row.id].orEmpty().associate { it.columnKey to it.value },
            )
        },
    )
}
