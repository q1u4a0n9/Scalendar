package com.scalendar.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_calendars")
data class UserCalendarEntity(
    @PrimaryKey
    val id: String,          // "CUSTOM_<timestamp>" generated in SettingsScreen
    val name: String,
    val colorHex: String,    // 6-char hex without '#', e.g. "1E88E5"
)
