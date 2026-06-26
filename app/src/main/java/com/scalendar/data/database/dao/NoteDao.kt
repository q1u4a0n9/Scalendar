package com.scalendar.data.database.dao

import androidx.room.*
import com.scalendar.data.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, date DESC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isPinned = 1 ORDER BY date DESC")
    fun getPinned(): Flow<List<NoteEntity>>

    /** One-shot read — used for initial Firestore sync on account creation. */
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, date DESC")
    suspend fun getAllOnce(): List<NoteEntity>

    /** Delete all notes — called on sign-out to prevent data leakage between accounts. */
    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)
}
