package com.aksharadasoha.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GradeEntity::class,
        TemplateEntity::class,
        TemplateGradeEntity::class,
        ColumnEntity::class,
        RateEntity::class,
        InstanceEntity::class,
        RowEntity::class,
        CellEntity::class,
        SourceCellEntity::class,
        TextCellEntity::class,
        MetaEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE templates ADD COLUMN sheetName TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS source_cells (
                        rowId TEXT NOT NULL,
                        columnKey TEXT NOT NULL,
                        value REAL,
                        PRIMARY KEY(rowId, columnKey),
                        FOREIGN KEY(rowId) REFERENCES rows(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_source_cells_rowId ON source_cells(rowId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS text_cells (
                        rowId TEXT NOT NULL,
                        columnKey TEXT NOT NULL,
                        value TEXT NOT NULL,
                        PRIMARY KEY(rowId, columnKey),
                        FOREIGN KEY(rowId) REFERENCES rows(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_text_cells_rowId ON text_cells(rowId)")
            }
        }

        fun build(context: Context, name: String = "akshara_ledger.db"): LedgerDatabase {
            return Room.databaseBuilder(context, LedgerDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }

        fun inMemory(context: Context): LedgerDatabase {
            return Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
