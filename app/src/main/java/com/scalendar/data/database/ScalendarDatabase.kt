package com.scalendar.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scalendar.data.database.converter.Converters
import com.scalendar.data.database.dao.EntryDao
import com.scalendar.data.database.dao.NoteDao
import com.scalendar.data.database.dao.UserCalendarDao
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.data.database.entity.UserCalendarEntity

@Database(
    entities = [EntryEntity::class, NoteEntity::class, UserCalendarEntity::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ScalendarDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun noteDao(): NoteDao
    abstract fun userCalendarDao(): UserCalendarDao

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

        /** v4 → v5: add deadlineDate column (nullable TEXT = ISO date string) */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN deadlineDate TEXT")
            }
        }

        /** v5 → v6: add location column */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN location TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v6 → v7: add user_calendars table */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS user_calendars (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        colorHex TEXT NOT NULL
                    )"""
                )
            }
        }

        /** v7 → v8: add recurrenceType column — stores DAILY/WEEKLY/MONTHLY/YEARLY/NONE per entry */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        /** v8 → v9: add seriesId column — UUID grouping all occurrences of a recurring series */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN seriesId TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
