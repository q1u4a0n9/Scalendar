package com.scalendar.di

import android.content.Context
import androidx.room.Room
import com.scalendar.data.database.ScalendarDatabase
import com.scalendar.data.database.dao.EntryDao
import com.scalendar.data.database.dao.NoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ScalendarDatabase =
        Room.databaseBuilder(ctx, ScalendarDatabase::class.java, ScalendarDatabase.NAME)
            .addMigrations(
                ScalendarDatabase.MIGRATION_1_2,
                ScalendarDatabase.MIGRATION_2_3,
                ScalendarDatabase.MIGRATION_3_4,
            )
            .build()

    @Provides fun provideEntryDao(db: ScalendarDatabase): EntryDao = db.entryDao()
    @Provides fun provideNoteDao(db: ScalendarDatabase): NoteDao = db.noteDao()
}
