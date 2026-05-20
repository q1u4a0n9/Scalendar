package com.scalendar.ui.screen.dayview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.data.model.TimeOfDay
import com.scalendar.data.repository.EntryRepository
import com.scalendar.data.repository.NoteRepository
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
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DayViewUiState> = _date
        .flatMapLatest { date ->
            repo.getByDate(date).map { entries ->
                DayViewUiState(entriesBySlot = entries.groupBy { it.timeOfDay })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayViewUiState(isLoading = true))

    /** All notes — used by AddEntrySheet for the note picker */
    val notes: StateFlow<List<NoteEntity>> = noteRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDate(date: LocalDate) { _date.value = date }

    fun toggleComplete(entry: EntryEntity) {
        viewModelScope.launch { repo.setCompleted(entry.id, !entry.isCompleted) }
    }

    fun addEntry(entity: EntryEntity) {
        viewModelScope.launch {
            repo.insert(entity)
            reminderManager.schedule(entity)
        }
    }

    fun deleteEntry(entry: EntryEntity) {
        viewModelScope.launch {
            reminderManager.cancel(entry)
            repo.delete(entry)
        }
    }
}
