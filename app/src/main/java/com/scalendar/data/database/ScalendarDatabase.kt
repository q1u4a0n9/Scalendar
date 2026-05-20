package com.scalendar.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scalendar.data.database.converter.Converters
import com.scalendar.data.database.dao.EntryDao
import com.scalendar.data.database.dao.NoteDao
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.database.entity.NoteEntity

@Database(
    entities = [EntryEntity::class, NoteEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ScalendarDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun noteDao(): NoteDao

    companion object {
        const val NAME = "scalendar_db"

        /** v1 → v2: add description column */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2 → v3: add linkedNoteId column (nullable INTEGER = Long?) */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN linkedNoteId INTEGER")
            }
        }

        /** v3 → v4: add color, reminderOffsets, isRecurring columns */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN color TEXT NOT NULL DEFAULT 'DEFAULT'")
                db.execSQL("ALTER TABLE entries ADD COLUMN reminderOffsets TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE entries ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
