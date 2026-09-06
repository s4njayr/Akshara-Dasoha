package com.aksharadasoha.ledger.data

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test
    fun addsSheetNameColumn() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "legacy.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE grades (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE template_grades (templateId TEXT NOT NULL, gradeId TEXT NOT NULL, PRIMARY KEY(templateId, gradeId), FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE, FOREIGN KEY(gradeId) REFERENCES grades(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_template_grades_gradeId ON template_grades(gradeId)")
                        db.execSQL("CREATE TABLE columns (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, `key` TEXT NOT NULL, label TEXT NOT NULL, groupLabel TEXT, valueType TEXT NOT NULL, unit TEXT, role TEXT NOT NULL, formula TEXT, sortOrder INTEGER NOT NULL, visible INTEGER NOT NULL, warnNegative INTEGER NOT NULL, FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_columns_templateId ON columns(templateId)")
                        db.execSQL("CREATE UNIQUE INDEX index_columns_templateId_key ON columns(templateId, `key`)")
                        db.execSQL("CREATE TABLE rates (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, `key` TEXT NOT NULL, label TEXT NOT NULL, value REAL NOT NULL, unit TEXT, sortOrder INTEGER NOT NULL, FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_rates_templateId ON rates(templateId)")
                        db.execSQL("CREATE UNIQUE INDEX index_rates_templateId_key ON rates(templateId, `key`)")
                        db.execSQL("CREATE TABLE instances (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, year INTEGER NOT NULL, month INTEGER NOT NULL, title TEXT NOT NULL, FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_instances_templateId ON instances(templateId)")
                        db.execSQL("CREATE UNIQUE INDEX index_instances_templateId_year_month ON instances(templateId, year, month)")
                        db.execSQL("CREATE TABLE rows (id TEXT NOT NULL PRIMARY KEY, instanceId TEXT NOT NULL, date TEXT NOT NULL, sortOrder INTEGER NOT NULL, FOREIGN KEY(instanceId) REFERENCES instances(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_rows_instanceId ON rows(instanceId)")
                        db.execSQL("CREATE UNIQUE INDEX index_rows_instanceId_date ON rows(instanceId, date)")
                        db.execSQL("CREATE TABLE cells (rowId TEXT NOT NULL, columnKey TEXT NOT NULL, value REAL, PRIMARY KEY(rowId, columnKey), FOREIGN KEY(rowId) REFERENCES rows(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_cells_rowId ON cells(rowId)")
                        db.execSQL("CREATE TABLE meta (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
                        db.execSQL("INSERT INTO templates(id, name, description) VALUES('rice_8', 'Rice', 'legacy')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.close()

        val db = Room.databaseBuilder(context, LedgerDatabase::class.java, name)
            .addMigrations(LedgerDatabase.MIGRATION_1_2, LedgerDatabase.MIGRATION_2_3, LedgerDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val cursor = db.query("PRAGMA table_info(templates)", emptyArray())
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        cursor.close()
        db.close()
        assertTrue(columns.contains("sheetName"))
    }

    @Test
    fun addsSourceCellsTable() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "legacy-v2.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE grades (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL, sheetName TEXT)")
                        db.execSQL("CREATE TABLE template_grades (templateId TEXT NOT NULL, gradeId TEXT NOT NULL, PRIMARY KEY(templateId, gradeId), FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE, FOREIGN KEY(gradeId) REFERENCES grades(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_template_grades_gradeId ON template_grades(gradeId)")
                        db.execSQL("CREATE TABLE columns (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, `key` TEXT NOT NULL, label TEXT NOT NULL, groupLabel TEXT, valueType TEXT NOT NULL, unit TEXT, role TEXT NOT NULL, formula TEXT, sortOrder INTEGER NOT NULL, visible INTEGER NOT NULL, warnNegative INTEGER NOT NULL, FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_columns_templateId ON columns(templateId)")
                        db.execSQL("CREATE UNIQUE INDEX index_columns_templateId_key ON columns(templateId, `key`)")
                        db.execSQL("CREATE TABLE rates (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, `key` TEXT NOT NULL, label TEXT NOT NULL, value REAL NOT NULL, unit TEXT, sortOrder INTEGER NOT NULL, FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_rates_templateId ON rates(templateId)")
                        db.execSQL("CREATE UNIQUE INDEX index_rates_templateId_key ON rates(templateId, `key`)")
                        db.execSQL("CREATE TABLE instances (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, year INTEGER NOT NULL, month INTEGER NOT NULL, title TEXT NOT NULL, FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_instances_templateId ON instances(templateId)")
                        db.execSQL("CREATE UNIQUE INDEX index_instances_templateId_year_month ON instances(templateId, year, month)")
                        db.execSQL("CREATE TABLE rows (id TEXT NOT NULL PRIMARY KEY, instanceId TEXT NOT NULL, date TEXT NOT NULL, sortOrder INTEGER NOT NULL, FOREIGN KEY(instanceId) REFERENCES instances(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_rows_instanceId ON rows(instanceId)")
                        db.execSQL("CREATE UNIQUE INDEX index_rows_instanceId_date ON rows(instanceId, date)")
                        db.execSQL("CREATE TABLE cells (rowId TEXT NOT NULL, columnKey TEXT NOT NULL, value REAL, PRIMARY KEY(rowId, columnKey), FOREIGN KEY(rowId) REFERENCES rows(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_cells_rowId ON cells(rowId)")
                        db.execSQL("CREATE TABLE meta (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
                        db.execSQL("INSERT INTO templates(id, name, description, sheetName) VALUES('rice_8', 'Rice', 'legacy', 'Rice')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.close()

        val db = Room.databaseBuilder(context, LedgerDatabase::class.java, name)
            .addMigrations(LedgerDatabase.MIGRATION_1_2, LedgerDatabase.MIGRATION_2_3, LedgerDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='source_cells'", emptyArray())
        assertTrue(cursor.moveToFirst())
        cursor.close()
        db.close()
    }

    @Test
    fun v2CellsSurviveUpgrade() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "legacy-v2-data.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE grades (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL, sheetName TEXT)")
                        db.execSQL("CREATE TABLE template_grades (templateId TEXT NOT NULL, gradeId TEXT NOT NULL, PRIMARY KEY(templateId, gradeId), FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE, FOREIGN KEY(gradeId) REFERENCES grades(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_template_grades_gradeId ON template_grades(gradeId)")
                        db.execSQL("CREATE TABLE columns (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, `key` TEXT NOT NULL, label TEXT NOT NULL, groupLabel TEXT, valueType TEXT NOT NULL, unit TEXT, role TEXT NOT NULL, formula TEXT, sortOrder INTEGER NOT NULL, visible INTEGER NOT NULL, warnNegative INTEGER NOT NULL, FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_columns_templateId ON columns(templateId)")
                        db.execSQL("CREATE UNIQUE INDEX index_columns_templateId_key ON columns(templateId, `key`)")
                        db.execSQL("CREATE TABLE rates (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, `key` TEXT NOT NULL, label TEXT NOT NULL, value REAL NOT NULL, unit TEXT, sortOrder INTEGER NOT NULL, FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_rates_templateId ON rates(templateId)")
                        db.execSQL("CREATE UNIQUE INDEX index_rates_templateId_key ON rates(templateId, `key`)")
                        db.execSQL("CREATE TABLE instances (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, year INTEGER NOT NULL, month INTEGER NOT NULL, title TEXT NOT NULL, FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_instances_templateId ON instances(templateId)")
                        db.execSQL("CREATE UNIQUE INDEX index_instances_templateId_year_month ON instances(templateId, year, month)")
                        db.execSQL("CREATE TABLE rows (id TEXT NOT NULL PRIMARY KEY, instanceId TEXT NOT NULL, date TEXT NOT NULL, sortOrder INTEGER NOT NULL, FOREIGN KEY(instanceId) REFERENCES instances(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_rows_instanceId ON rows(instanceId)")
                        db.execSQL("CREATE UNIQUE INDEX index_rows_instanceId_date ON rows(instanceId, date)")
                        db.execSQL("CREATE TABLE cells (rowId TEXT NOT NULL, columnKey TEXT NOT NULL, value REAL, PRIMARY KEY(rowId, columnKey), FOREIGN KEY(rowId) REFERENCES rows(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_cells_rowId ON cells(rowId)")
                        db.execSQL("CREATE TABLE meta (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
                        db.execSQL("INSERT INTO templates(id, name, description, sheetName) VALUES('rice_8', 'Rice', 'legacy', 'Rice')")
                        db.execSQL("INSERT INTO instances(id, templateId, year, month, title) VALUES('rice_8_2025_05', 'rice_8', 2025, 5, 'May')")
                        db.execSQL("INSERT INTO rows(id, instanceId, date, sortOrder) VALUES('r1', 'rice_8_2025_05', '2025-05-01', 1)")
                        db.execSQL("INSERT INTO cells(rowId, columnKey, value) VALUES('r1', 'count', 20.0)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.close()
        val db = Room.databaseBuilder(context, LedgerDatabase::class.java, name)
            .addMigrations(LedgerDatabase.MIGRATION_1_2, LedgerDatabase.MIGRATION_2_3, LedgerDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val cursor = db.query("SELECT value FROM cells WHERE rowId='r1'", emptyArray())
        assertTrue(cursor.moveToFirst())
        assertEquals(20.0, cursor.getDouble(0), 0.0)
        cursor.close()
        val textTable = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='text_cells'", emptyArray())
        assertTrue(textTable.moveToFirst())
        textTable.close()
        db.close()
    }
}
