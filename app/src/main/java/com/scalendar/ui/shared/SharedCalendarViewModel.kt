package com.scalendar.ui.shared

import androidx.lifecycle.ViewModel
import com.scalendar.data.model.EntryCategory
import com.scalendar.navigation.CalendarView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SharedCalendarViewModel @Inject constructor() : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _calendarView = MutableStateFlow(CalendarView.DAY)
    val calendarView: StateFlow<CalendarView> = _calendarView.asStateFlow()

    // All categories enabled by default
    private val _categoryFilters = MutableStateFlow(EntryCategory.entries.toSet())
    val categoryFilters: StateFlow<Set<EntryCategory>> = _categoryFilters.asStateFlow()

    fun selectDate(date: LocalDate) { _selectedDate.value = date }

    fun setCalendarView(view: CalendarView) { _calendarView.value = view }

    fun toggleCategoryFilter(cat: EntryCategory) {
        val current = _categoryFilters.value
        _categoryFilters.value = if (cat in current) current - cat else current + cat
    }

    fun nextDay()     { _selectedDate.value = _selectedDate.value.plusDays(1) }
    fun previousDay() { _selectedDate.value = _selectedDate.value.minusDays(1) }
    fun nextWeek()     { _selectedDate.value = _selectedDate.value.plusWeeks(1) }
    fun previousWeek() { _selectedDate.value = _selectedDate.value.minusWeeks(1) }
    fun nextMonth()     { _selectedDate.value = _selectedDate.value.plusMonths(1) }
    fun previousMonth() { _selectedDate.value = _selectedDate.value.minusMonths(1) }
}
