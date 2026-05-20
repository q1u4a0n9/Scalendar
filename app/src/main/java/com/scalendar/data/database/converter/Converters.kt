package com.scalendar.data.database.converter

import androidx.room.TypeConverter
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.model.TimeOfDay
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter fun fromLocalDate(v: LocalDate): String = v.toString()
    @TypeConverter fun toLocalDate(v: String): LocalDate = LocalDate.parse(v)

    @TypeConverter fun fromLocalTime(v: LocalTime?): String? = v?.toString()
    @TypeConverter fun toLocalTime(v: String?): LocalTime? = v?.let { LocalTime.parse(it) }

    @TypeConverter fun fromCategory(v: EntryCategory): String = v.name
    @TypeConverter fun toCategory(v: String): EntryCategory = EntryCategory.valueOf(v)

    @TypeConverter fun fromTimeOfDay(v: TimeOfDay): String = v.name
    @TypeConverter fun toTimeOfDay(v: String): TimeOfDay = TimeOfDay.valueOf(v)
}
