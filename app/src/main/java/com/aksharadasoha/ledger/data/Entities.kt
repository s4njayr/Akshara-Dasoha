package com.aksharadasoha.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "grades")
data class GradeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val sheetName: String?,
)

@Entity(
    tableName = "template_grades",
    primaryKeys = ["templateId", "gradeId"],
    foreignKeys = [
        ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GradeEntity::class, parentColumns = ["id"], childColumns = ["gradeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("gradeId")],
)
data class TemplateGradeEntity(
    val templateId: String,
    val gradeId: String,
)

@Entity(
    tableName = "columns",
    foreignKeys = [
        ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("templateId"), Index(value = ["templateId", "key"], unique = true)],
)
data class ColumnEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val key: String,
    val label: String,
    val groupLabel: String?,
    val valueType: String,
    val unit: String?,
    val role: String,
    val formula: String?,
    val sortOrder: Int,
    val visible: Boolean,
    val warnNegative: Boolean,
)

@Entity(
    tableName = "rates",
    foreignKeys = [
        ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("templateId"), Index(value = ["templateId", "key"], unique = true)],
)
data class RateEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val key: String,
    val label: String,
    val value: Double,
    val unit: String?,
    val sortOrder: Int,
)

@Entity(
    tableName = "instances",
    foreignKeys = [
        ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("templateId"), Index(value = ["templateId", "year", "month"], unique = true)],
)
data class InstanceEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val year: Int,
    val month: Int,
    val title: String,
)

@Entity(
    tableName = "rows",
    foreignKeys = [
        ForeignKey(entity = InstanceEntity::class, parentColumns = ["id"], childColumns = ["instanceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("instanceId"), Index(value = ["instanceId", "date"], unique = true)],
)
data class RowEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val date: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "cells",
    primaryKeys = ["rowId", "columnKey"],
    foreignKeys = [
        ForeignKey(entity = RowEntity::class, parentColumns = ["id"], childColumns = ["rowId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("rowId")],
)
data class CellEntity(
    val rowId: String,
    val columnKey: String,
    val value: Double?,
)

@Entity(
    tableName = "source_cells",
    primaryKeys = ["rowId", "columnKey"],
    foreignKeys = [
        ForeignKey(entity = RowEntity::class, parentColumns = ["id"], childColumns = ["rowId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("rowId")],
)
data class SourceCellEntity(
    val rowId: String,
    val columnKey: String,
    val value: Double?,
)

@Entity(
    tableName = "text_cells",
    primaryKeys = ["rowId", "columnKey"],
    foreignKeys = [
        ForeignKey(entity = RowEntity::class, parentColumns = ["id"], childColumns = ["rowId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("rowId")],
)
data class TextCellEntity(
    val rowId: String,
    val columnKey: String,
    val value: String,
)

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
