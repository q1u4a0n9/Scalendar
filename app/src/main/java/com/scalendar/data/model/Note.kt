package com.scalendar.data.model

import java.time.LocalDate

data class Note(
    val id: Long = 0,
    val title: String,
    val content: String,
    val date: LocalDate,
    val isPinned: Boolean = false
)
