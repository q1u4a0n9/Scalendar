package com.scalendar.ui.screen.addentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.data.repository.EntryRepository
import com.scalendar.data.repository.NoteRepository
import com.scalendar.notification.ReminderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AddEntryViewModel @Inject constructor(
    private val repo           : EntryRepository,
    private val noteRepo       : NoteRepository,
    private val reminderManager: ReminderManager,
) : ViewModel() {

    val notes: StateFlow<List<NoteEntity>> = noteRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveEntry(
        entity            : EntryEntity,
        recurrenceType    : String     = "NONE",
        recurrenceEndDate : LocalDate? = null,
    ) {
        viewModelScope.launch {
            val entries = if (recurrenceType == "NONE" || recurrenceEndDate == null) {
                listOf(entity)
            } else {
                expandRecurrence(entity, recurrenceType, recurrenceEndDate)
            }
            entries.forEach { e ->
                repo.insert(e)
                reminderManager.schedule(e)
            }
        }
    }

    private fun expandRecurrence(
        template: EntryEntity,
        type    : String,
        endDate : LocalDate,
    ): List<EntryEntity> = buildList {
        var current = template.date
        while (!current.isAfter(endDate)) {
            add(template.copy(id = 0, date = current))
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
