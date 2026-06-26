package com.scalendar.ui.screen.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.repository.EntryRepository
import com.scalendar.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** One month group displayed in the Journal timeline. */
data class JournalMonthGroup(
    val yearMonth : YearMonth,
    val entries   : List<EntryEntity>,
)

data class JournalUiState(
    val groups    : List<JournalMonthGroup> = emptyList(),
    val isLoading : Boolean = false,
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repo    : EntryRepository,
    private val noteRepo: NoteRepository,
) : ViewModel() {

    /** date → notes list — notes written on the same day auto-appear in Journal */
    val notesByDate: StateFlow<Map<LocalDate, List<NoteEntity>>> = noteRepo.getAll()
        .map { list -> list.groupBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Show a rolling window: past 6 months + next 6 months.
    // Journal shows ALL EXAM / BIRTHDAY / EVENT entries regardless of completion —
    // the user keeps them for review.
    val uiState: StateFlow<JournalUiState> = run {
        val today  = LocalDate.now()
        val start  = today.minusMonths(6).withDayOfMonth(1)
        val end    = today.plusMonths(6).let { d ->
            d.withDayOfMonth(YearMonth.from(d).lengthOfMonth())
        }
        val journalCategories = setOf(
            EntryCategory.EXAM,
            EntryCategory.BIRTHDAY,
            EntryCategory.EVENT,
        )
        repo.getByDateRange(start, end)
            .map { all ->
                val filtered = all.filter { it.category in journalCategories }
                val grouped = filtered
                    .groupBy { YearMonth.from(it.date) }
                    .entries
                    .sortedBy { it.key }
                    .map { (ym, list) ->
                        JournalMonthGroup(ym, list.sortedBy { it.date })
                    }
                JournalUiState(groups = grouped)
            }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        JournalUiState(isLoading = true),
    )
}
