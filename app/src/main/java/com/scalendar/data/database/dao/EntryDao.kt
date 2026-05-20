package com.scalendar.data.database.dao

import androidx.room.*
import com.scalendar.data.database.entity.EntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE date = :date ORDER BY timeOfDay ASC, startTime ASC")
    fun getByDate(date: LocalDate): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE date >= :start AND date <= :end ORDER BY date ASC, timeOfDay ASC, startTime ASC")
    fun getByDateRange(start: LocalDate, end: LocalDate): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE isImportant = 1 ORDER BY date DESC")
    fun getImportant(): Flow<List<EntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("UPDATE entries SET isCompleted = :done WHERE id = :id")
    suspend fun setCompleted(id: Long, done: Boolean)
}
