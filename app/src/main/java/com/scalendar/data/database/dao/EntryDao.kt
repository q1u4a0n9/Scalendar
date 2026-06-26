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

    /** TASK entries whose deadlineDate falls in [start, end] */
    @Query("SELECT * FROM entries WHERE deadlineDate IS NOT NULL AND deadlineDate >= :start AND deadlineDate <= :end ORDER BY deadlineDate ASC")
    fun getByDeadlineDateRange(start: LocalDate, end: LocalDate): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EntryEntity?

    /** Live flow of ALL entries — used for in-memory search. */
    @Query("SELECT * FROM entries ORDER BY date DESC")
    fun getAll(): Flow<List<EntryEntity>>

    /** One-shot read of ALL entries — used for initial Firestore sync on account creation. */
    @Query("SELECT * FROM entries ORDER BY date ASC")
    suspend fun getAllOnce(): List<EntryEntity>

    /** Delete all entries — called on sign-out to prevent data leakage between accounts. */
    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    /** All entries sharing the same seriesId (non-empty). Used to delete/query an entire series. */
    @Query("SELECT * FROM entries WHERE seriesId = :seriesId AND seriesId != ''")
    suspend fun getSeriesEntries(seriesId: String): List<EntryEntity>

    @Query("DELETE FROM entries WHERE seriesId = :seriesId AND seriesId != ''")
    suspend fun deleteSeriesById(seriesId: String)

    /** Fallback for old entries (seriesId = ''): match by title + category + recurrenceType. */
    @Query("SELECT * FROM entries WHERE title = :title AND category = :category AND recurrenceType = :recType AND recurrenceType != 'NONE'")
    suspend fun getSeriesByFields(title: String, category: String, recType: String): List<EntryEntity>

    @Query("DELETE FROM entries WHERE title = :title AND category = :category AND recurrenceType = :recType AND recurrenceType != 'NONE'")
    suspend fun deleteSeriesByFields(title: String, category: String, recType: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("UPDATE entries SET isCompleted = :done WHERE id = :id")
    suspend fun setCompleted(id: Long, done: Boolean)
}
