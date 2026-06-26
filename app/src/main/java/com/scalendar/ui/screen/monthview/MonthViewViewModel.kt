package com.scalendar.ui.screen.monthview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class MonthViewUiState(
    val yearMonth       : YearMonth = YearMonth.now(),
    val entries         : Map<LocalDate, List<EntryEntity>> = emptyMap(),
    val deadlineEntries : Map<LocalDate, List<EntryEntity>> = emptyMap(),  // keyed by deadlineDate
    val isLoading       : Boolean = false,
)

@HiltViewModel
class MonthViewViewModel @Inject constructor(
    private val repo: EntryRepository
) : ViewModel() {

    private val _yearMonth = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MonthViewUiState> = _yearMonth
        .flatMapLatest { ym ->
            val start = ym.atDay(1)
            val end   = ym.atEndOfMonth()
            combine(
                repo.getByDateRange(start, end),
                repo.getByDeadlineDateRange(start, end),
            ) { entries, deadlines ->
                MonthViewUiState(
                    yearMonth       = ym,
                    entries         = entries.groupBy { it.date },
                    deadlineEntries = deadlines
                        .filter { it.deadlineDate != null }
                        .groupBy { it.deadlineDate!! },
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MonthViewUiState(isLoading = true),
        )

    fun setMonth(date: LocalDate) {
        _yearMonth.value = YearMonth.from(date)
    }

    fun previousMonth() {
        _yearMonth.value = _yearMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _yearMonth.value = _yearMonth.value.plusMonths(1)
    }

    fun goToToday() {
        _yearMonth.value = YearMonth.now()
    }
}
