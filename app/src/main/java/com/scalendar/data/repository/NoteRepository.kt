package com.scalendar.data.repository

import com.scalendar.data.database.dao.NoteDao
import com.scalendar.data.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(private val dao: NoteDao) {
    fun getAll(): Flow<List<NoteEntity>> = dao.getAll()
    fun getPinned(): Flow<List<NoteEntity>> = dao.getPinned()
    suspend fun insert(note: NoteEntity): Long = dao.insert(note)
    suspend fun update(note: NoteEntity) = dao.update(note)
    suspend fun delete(note: NoteEntity) = dao.delete(note)
    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)
}
