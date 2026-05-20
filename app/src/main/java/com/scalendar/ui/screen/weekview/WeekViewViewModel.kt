package com.scalendar.ui.screen.weekview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.model.TimeOfDay
import com.scalendar.data.repository.EntryRepository
import com.scalendar.util.startOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

data class WeekViewUiState(
    val weekStart: LocalDate = LocalDate.now().startOfWeek(),
    val grid: Map<LocalDate, Map<TimeOfDay, List<EntryEntity>>> = emptyMap(),
    val isLoading: Boolean = false
)

@HiltViewModel
class WeekViewViewModel @Inject constructor(
    private val repo: EntryRepository
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WeekViewUiState> = _date
        .flatMapLatest { date ->
            val start = date.startOfWeek()
            val end   = start.plusDays(6)
            repo.getByDateRange(start, end).map { entries ->
                WeekViewUiState(
                    weekStart = start,
                    grid = entries.groupBy { it.date }
                        .mapValues { (_, list) -> list.groupBy { it.timeOfDay } }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekViewUiState(isLoading = true))

    fun setDate(date: LocalDate) { _date.value = date }
}
