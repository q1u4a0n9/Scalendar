package com.scalendar.data.repository

import com.scalendar.data.database.dao.EntryDao
import com.scalendar.data.database.entity.EntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(private val dao: EntryDao) {
    fun getAll(): Flow<List<EntryEntity>>                   = dao.getAll()
    fun getByDate(date: LocalDate): Flow<List<EntryEntity>> = dao.getByDate(date)
    fun getByDateRange(start: LocalDate, end: LocalDate): Flow<List<EntryEntity>> = dao.getByDateRange(start, end)
    fun getImportant(): Flow<List<EntryEntity>> = dao.getImportant()
    fun getByDeadlineDateRange(start: LocalDate, end: LocalDate): Flow<List<EntryEntity>> = dao.getByDeadlineDateRange(start, end)
    suspend fun getById(id: Long): EntryEntity?    = dao.getById(id)
    suspend fun getAllOnce(): List<EntryEntity>    = dao.getAllOnce()
    suspend fun deleteAll()                       = dao.deleteAll()
    suspend fun getSeriesEntries(seriesId: String): List<EntryEntity>   = dao.getSeriesEntries(seriesId)
    suspend fun deleteSeriesById(seriesId: String)                      = dao.deleteSeriesById(seriesId)
    suspend fun getSeriesByFields(title: String, category: String, recType: String): List<EntryEntity> =
        dao.getSeriesByFields(title, category, recType)
    suspend fun deleteSeriesByFields(title: String, category: String, recType: String) =
        dao.deleteSeriesByFields(title, category, recType)
    suspend fun insert(entry: EntryEntity): Long  = dao.insert(entry)
    suspend fun update(entry: EntryEntity) = dao.update(entry)
    suspend fun delete(entry: EntryEntity) = dao.delete(entry)
    suspend fun setCompleted(id: Long, done: Boolean) = dao.setCompleted(id, done)
}
