package com.scalendar.data.repository

import com.scalendar.data.database.dao.UserCalendarDao
import com.scalendar.data.database.entity.UserCalendarEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserCalendarRepository @Inject constructor(
    private val dao: UserCalendarDao,
) {
    fun getAll(): Flow<List<UserCalendarEntity>>        = dao.getAll()
    suspend fun getAllOnce(): List<UserCalendarEntity>  = dao.getAllOnce()
    suspend fun deleteAll()                             = dao.deleteAll()
    suspend fun insert(cal: UserCalendarEntity)         = dao.insert(cal)
    suspend fun delete(cal: UserCalendarEntity)  = dao.delete(cal)
}
