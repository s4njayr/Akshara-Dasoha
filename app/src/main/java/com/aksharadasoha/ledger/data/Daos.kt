package com.aksharadasoha.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM grades ORDER BY sortOrder")
    fun observeGrades(): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades ORDER BY sortOrder")
    suspend fun getGrades(): List<GradeEntity>

    @Query("SELECT * FROM templates ORDER BY name")
    fun observeTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY name")
    suspend fun getTemplates(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplate(id: String): TemplateEntity?

    @Query("SELECT * FROM template_grades")
    suspend fun getTemplateGrades(): List<TemplateGradeEntity>

    @Query("SELECT * FROM template_grades WHERE templateId = :templateId")
    suspend fun getTemplateGrades(templateId: String): List<TemplateGradeEntity>

    @Query("SELECT * FROM columns WHERE templateId = :templateId ORDER BY sortOrder")
    suspend fun getColumns(templateId: String): List<ColumnEntity>

    @Query("SELECT * FROM columns ORDER BY sortOrder")
    suspend fun getAllColumns(): List<ColumnEntity>

    @Query("SELECT * FROM rates WHERE templateId = :templateId ORDER BY sortOrder")
    suspend fun getRates(templateId: String): List<RateEntity>

    @Query("SELECT * FROM rates ORDER BY sortOrder")
    suspend fun getAllRates(): List<RateEntity>

    @Query("SELECT * FROM instances ORDER BY year DESC, month DESC")
    fun observeInstances(): Flow<List<InstanceEntity>>

    @Query("SELECT * FROM instances ORDER BY year DESC, month DESC")
    suspend fun getInstances(): List<InstanceEntity>

    @Query("SELECT * FROM instances WHERE id = :id")
    suspend fun getInstance(id: String): InstanceEntity?

    @Query("SELECT * FROM instances WHERE templateId = :templateId ORDER BY year DESC, month DESC")
    fun observeInstancesForTemplate(templateId: String): Flow<List<InstanceEntity>>

    @Query("SELECT * FROM rows WHERE instanceId = :instanceId ORDER BY date")
    suspend fun getRows(instanceId: String): List<RowEntity>

    @Query("SELECT * FROM rows ORDER BY date")
    suspend fun getAllRows(): List<RowEntity>

    @Query("SELECT * FROM cells WHERE rowId IN (:rowIds)")
    suspend fun getCells(rowIds: List<String>): List<CellEntity>

    @Query("SELECT * FROM cells")
    suspend fun getAllCells(): List<CellEntity>

    @Query("SELECT * FROM source_cells WHERE rowId IN (:rowIds)")
    suspend fun getSourceCells(rowIds: List<String>): List<SourceCellEntity>

    @Query("SELECT * FROM source_cells")
    suspend fun getAllSourceCells(): List<SourceCellEntity>

    @Query("SELECT * FROM text_cells WHERE rowId IN (:rowIds)")
    suspend fun getTextCells(rowIds: List<String>): List<TextCellEntity>

    @Query("SELECT * FROM text_cells")
    suspend fun getAllTextCells(): List<TextCellEntity>

    @Query("SELECT value FROM meta WHERE key = :key")
    suspend fun getMeta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(entity: MetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGrades(items: List<GradeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplates(items: List<TemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplateGrades(items: List<TemplateGradeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertColumns(items: List<ColumnEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRates(items: List<RateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstances(items: List<InstanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRows(items: List<RowEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCells(items: List<CellEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceCells(items: List<SourceCellEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTextCells(items: List<TextCellEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGrade(item: GradeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplate(item: TemplateEntity): Long

    @Update
    suspend fun updateTemplate(item: TemplateEntity)

    @Transaction
    suspend fun upsertTemplate(item: TemplateEntity) {
        if (insertTemplate(item) == -1L) updateTemplate(item)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertColumn(item: ColumnEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(item: RateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInstance(item: InstanceEntity): Long

    @Update
    suspend fun updateInstance(item: InstanceEntity)

    @Transaction
    suspend fun upsertInstance(item: InstanceEntity) {
        if (insertInstance(item) == -1L) updateInstance(item)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRow(item: RowEntity): Long

    @Update
    suspend fun updateRow(item: RowEntity)

    @Transaction
    suspend fun upsertRow(item: RowEntity) {
        if (insertRow(item) == -1L) updateRow(item)
    }

    @Update
    suspend fun updateColumn(item: ColumnEntity)

    @Query("DELETE FROM grades WHERE id = :id")
    suspend fun deleteGrade(id: String)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("DELETE FROM template_grades WHERE templateId = :templateId")
    suspend fun deleteTemplateGrades(templateId: String)

    @Query("DELETE FROM columns WHERE id = :id")
    suspend fun deleteColumn(id: String)

    @Query("DELETE FROM rates WHERE id = :id")
    suspend fun deleteRate(id: String)

    @Query("DELETE FROM instances WHERE id = :id")
    suspend fun deleteInstance(id: String)

    @Query("DELETE FROM rows WHERE id = :id")
    suspend fun deleteRow(id: String)

    @Query("DELETE FROM cells WHERE rowId = :rowId AND columnKey = :columnKey")
    suspend fun deleteCell(rowId: String, columnKey: String)

    @Query("DELETE FROM cells WHERE columnKey = :columnKey")
    suspend fun deleteCellsForColumn(columnKey: String)

    @Query("UPDATE cells SET columnKey = :newKey WHERE columnKey = :oldKey")
    suspend fun renameCellKey(oldKey: String, newKey: String)

    @Query("DELETE FROM source_cells WHERE columnKey = :columnKey")
    suspend fun deleteSourceCellsForColumn(columnKey: String)

    @Query("DELETE FROM source_cells WHERE rowId = :rowId AND columnKey = :columnKey")
    suspend fun deleteSourceCell(rowId: String, columnKey: String)

    @Query("DELETE FROM text_cells WHERE rowId = :rowId AND columnKey = :columnKey")
    suspend fun deleteTextCell(rowId: String, columnKey: String)

    @Query("DELETE FROM text_cells WHERE columnKey = :columnKey")
    suspend fun deleteTextCellsForColumn(columnKey: String)

    @Query("UPDATE text_cells SET columnKey = :newKey WHERE columnKey = :oldKey")
    suspend fun renameTextCellKey(oldKey: String, newKey: String)

    @Query("UPDATE source_cells SET columnKey = :newKey WHERE columnKey = :oldKey")
    suspend fun renameSourceCellKey(oldKey: String, newKey: String)

    @Query("DELETE FROM grades")
    suspend fun clearGrades()

    @Query("DELETE FROM templates")
    suspend fun clearTemplates()

    @Query("DELETE FROM template_grades")
    suspend fun clearTemplateGrades()

    @Query("DELETE FROM columns")
    suspend fun clearColumns()

    @Query("DELETE FROM rates")
    suspend fun clearRates()

    @Query("DELETE FROM instances")
    suspend fun clearInstances()

    @Query("DELETE FROM rows")
    suspend fun clearRows()

    @Query("DELETE FROM cells")
    suspend fun clearCells()

    @Query("DELETE FROM source_cells")
    suspend fun clearSourceCells()

    @Query("DELETE FROM text_cells")
    suspend fun clearTextCells()

    @Transaction
    suspend fun replaceAll(
        grades: List<GradeEntity>,
        templates: List<TemplateEntity>,
        templateGrades: List<TemplateGradeEntity>,
        columns: List<ColumnEntity>,
        rates: List<RateEntity>,
        instances: List<InstanceEntity>,
        rows: List<RowEntity>,
        cells: List<CellEntity>,
        sourceCells: List<SourceCellEntity> = emptyList(),
        textCells: List<TextCellEntity> = emptyList(),
    ) {
        clearTextCells()
        clearSourceCells()
        clearCells()
        clearRows()
        clearInstances()
        clearRates()
        clearColumns()
        clearTemplateGrades()
        clearTemplates()
        clearGrades()
        upsertGrades(grades)
        upsertTemplates(templates)
        upsertTemplateGrades(templateGrades)
        upsertColumns(columns)
        upsertRates(rates)
        upsertInstances(instances)
        upsertRows(rows)
        upsertCells(cells)
        upsertSourceCells(sourceCells)
        upsertTextCells(textCells)
    }
}
