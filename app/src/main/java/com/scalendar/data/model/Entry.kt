package com.scalendar.data.model

import java.time.LocalDate
import java.time.LocalTime

enum class EntryCategory {
    TASK, CLASS, SPORT, EXAM, BIRTHDAY, EVENT;

    fun displayName(): String = when (this) {
        TASK     -> "Việc cần làm"
        CLASS    -> "Lớp học"
        SPORT    -> "Thể thao"
        EXAM     -> "Bài kiểm tra"
        BIRTHDAY -> "Sinh nhật"
        EVENT    -> "Lịch / Sự kiện"
    }
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
