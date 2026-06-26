package com.scalendar.data.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.scalendar.R
import java.time.LocalDate
import java.time.LocalTime

enum class EntryCategory {
    TASK, CLASS, SPORT, EXAM, BIRTHDAY, EVENT;

    fun displayName(): String = when (this) {
        TASK     -> "Việc cần làm"
        CLASS    -> "Lịch học"
        SPORT    -> "Thể thao"
        EXAM     -> "Bài kiểm tra"
        BIRTHDAY -> "Sinh nhật"
        EVENT    -> "Lịch / Sự kiện"
    }
}

@Composable
fun EntryCategory.localizedName(): String = when (this) {
    EntryCategory.TASK     -> stringResource(R.string.category_task)
    EntryCategory.CLASS    -> stringResource(R.string.category_class)
    EntryCategory.SPORT    -> stringResource(R.string.category_sport)
    EntryCategory.EXAM     -> stringResource(R.string.category_exam)
    EntryCategory.BIRTHDAY -> stringResource(R.string.category_birthday)
    EntryCategory.EVENT    -> stringResource(R.string.category_event)
}

enum class TimeOfDay {
    ANYTIME, MORNING, AFTERNOON, EVENING;

    fun displayName(): String = when (this) {
        ANYTIME   -> "Bất kỳ lúc nào"
        MORNING   -> "Buổi sáng"
        AFTERNOON -> "Buổi chiều"
        EVENING   -> "Buổi tối"
    }
}

@Composable
fun TimeOfDay.localizedName(): String = when (this) {
    TimeOfDay.ANYTIME   -> stringResource(R.string.time_anytime)
    TimeOfDay.MORNING   -> stringResource(R.string.time_morning)
    TimeOfDay.AFTERNOON -> stringResource(R.string.time_afternoon)
    TimeOfDay.EVENING   -> stringResource(R.string.time_evening)
}

data class Entry(
    val id: Long = 0,
    val title: String,
    val category: EntryCategory = EntryCategory.TASK,
    val timeOfDay: TimeOfDay = TimeOfDay.ANYTIME,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val date: LocalDate = LocalDate.now(),
    val isCompleted: Boolean = false,
    val isImportant: Boolean = false
)
