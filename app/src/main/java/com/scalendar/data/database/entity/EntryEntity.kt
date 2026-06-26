package com.scalendar.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.model.TimeOfDay
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: EntryCategory,
    val timeOfDay: TimeOfDay,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val date: LocalDate,
    val isCompleted     : Boolean = false,
    val isImportant     : Boolean = false,
    val description     : String  = "",       // short detail / room / time note
    val linkedNoteId    : Long?   = null,     // id of NoteEntity attached from NotesScreen
    val color           : String  = "DEFAULT",// "DEFAULT"|"BLUE"|"PURPLE"|"RED"|"ORANGE"|"PINK"|"BROWN"|"TEAL"|"GREEN"
    val reminderOffsets : String     = "",       // comma-separated day offsets e.g. "0,1,7"
    val isRecurring     : Boolean    = false,    // annual repeat (BIRTHDAY/EVENT)
    val recurrenceType  : String     = "NONE",   // NONE|DAILY|WEEKLY|MONTHLY|YEARLY — stored per occurrence
    val seriesId        : String     = "",        // UUID shared by all occurrences of a recurring series; "" = standalone
    val deadlineDate    : LocalDate? = null,     // TASK: optional deadline shown on month view
    val location        : String     = "",       // optional venue/room (e.g. "Phòng A101", "Sân Mỹ Đình")
)
