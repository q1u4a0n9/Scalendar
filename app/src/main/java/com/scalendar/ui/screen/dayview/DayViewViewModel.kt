package com.scalendar.ui.screen.dayview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.data.model.TimeOfDay
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.repository.AuthRepository
import com.scalendar.data.repository.EntryRepository
import com.scalendar.data.repository.FirestoreRepository
import com.scalendar.data.repository.NoteRepository
import com.scalendar.data.repository.SettingsRepository
import com.scalendar.notification.ReminderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DayViewUiState(
    val entriesBySlot: Map<TimeOfDay, List<EntryEntity>> = emptyMap(),
    val isLoading    : Boolean                           = false,
)

@HiltViewModel
class DayViewViewModel @Inject constructor(
    private val repo           : EntryRepository,
    private val noteRepo       : NoteRepository,
    private val reminderManager: ReminderManager,
    private val settingsRepo   : SettingsRepository,
    private val authRepo       : AuthRepository,
    private val firestoreRepo  : FirestoreRepository,
) : ViewModel() {

    private val uid get() = authRepo.currentUser?.uid ?: ""

    private val _date = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DayViewUiState> = _date
        .flatMapLatest { date ->
            repo.getByDate(date).map { entries ->
                DayViewUiState(entriesBySlot = entries.groupBy { it.timeOfDay })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayViewUiState(isLoading = true))

    val notes: StateFlow<List<NoteEntity>> = noteRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Default reminder minutes per category, driven by SettingsRepository → DataStore. */
    val defaultNotifs: StateFlow<Map<EntryCategory, Int?>> =
        settingsRepo.allDefaultNotifs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setDate(date: LocalDate) { _date.value = date }

    fun toggleComplete(entry: EntryEntity) {
        val done = !entry.isCompleted
        viewModelScope.launch {
            repo.setCompleted(entry.id, done)
            firestoreRepo.setEntryCompleted(uid, entry.id, done)
        }
    }

    fun saveEntry(
        entity            : EntryEntity,
        recurrenceType    : String     = "NONE",
        recurrenceEndDate : LocalDate? = null,
    ) {
        viewModelScope.launch {
            // Cancel existing alarms before replacing (edit case)
            if (entity.id != 0L) {
                repo.getById(entity.id)?.let { old -> reminderManager.cancel(old) }
            }
            val entries = if (entity.id != 0L || recurrenceType == "NONE" || recurrenceEndDate == null) {
                // Edit existing entry (id != 0) → always update in place, never re-expand
                // New entry without recurrence → single insert
                listOf(entity)
            } else {
                // New entry with recurrence → expand into multiple occurrences
                expandRecurrence(entity, recurrenceType, recurrenceEndDate)
            }
            entries.forEach { e ->
                val newId = repo.insert(e)
                val saved = e.copy(id = newId)
                reminderManager.schedule(saved)
                firestoreRepo.upsertEntry(uid, saved)
            }
        }
    }

    fun deleteEntry(entry: EntryEntity) {
        viewModelScope.launch {
            reminderManager.cancel(entry)
            repo.delete(entry)
            firestoreRepo.deleteEntry(uid, entry.id)
        }
    }

    /** Delete every occurrence in a recurring series. Falls back to title+category+recType for
     *  old entries that were created before the seriesId field was introduced. */
    fun deleteEntrySeries(entry: EntryEntity) {
        viewModelScope.launch {
            val all = if (entry.seriesId.isNotBlank()) {
                repo.getSeriesEntries(entry.seriesId)
            } else {
                repo.getSeriesByFields(entry.title, entry.category.name, entry.recurrenceType)
            }
            all.forEach { reminderManager.cancel(it) }
            if (entry.seriesId.isNotBlank()) {
                repo.deleteSeriesById(entry.seriesId)
            } else {
                repo.deleteSeriesByFields(entry.title, entry.category.name, entry.recurrenceType)
            }
            firestoreRepo.deleteEntries(uid, all.map { it.id })
        }
    }

    private fun expandRecurrence(
        template: EntryEntity,
        type    : String,
        endDate : LocalDate,
    ): List<EntryEntity> = buildList {
        val seriesId = java.util.UUID.randomUUID().toString()
        var current = template.date
        while (!current.isAfter(endDate)) {
            add(template.copy(id = 0, date = current, seriesId = seriesId))
            val next = when (type) {
                "DAILY"   -> current.plusDays(1)
                "WEEKLY"  -> current.plusWeeks(1)
                "MONTHLY" -> current.plusMonths(1)
                "YEARLY"  -> current.plusYears(1)
                else      -> null
            }
            if (next == null) break
            current = next
        }
    }
}
