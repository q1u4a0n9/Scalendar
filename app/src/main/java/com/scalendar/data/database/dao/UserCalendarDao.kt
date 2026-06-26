package com.scalendar.data.database.dao

import androidx.room.*
import com.scalendar.data.database.entity.UserCalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCalendarDao {
    @Query("SELECT * FROM user_calendars ORDER BY rowid ASC")
    fun getAll(): Flow<List<UserCalendarEntity>>

    /** One-shot read — used for initial Firestore sync on account creation. */
    @Query("SELECT * FROM user_calendars ORDER BY rowid ASC")
    suspend fun getAllOnce(): List<UserCalendarEntity>

    /** Delete all user calendars — called on sign-out to prevent data leakage between accounts. */
    @Query("DELETE FROM user_calendars")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cal: UserCalendarEntity)

    @Delete
    suspend fun delete(cal: UserCalendarEntity)
}
