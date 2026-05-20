package com.scalendar.ui.screen.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Label for a group of notes in the Notes screen. */
enum class NoteGroup {
    PINNED,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_MONTH,
    OLDER;

    fun displayName(): String = when (this) {
        PINNED      -> "Đã ghim"
        TODAY       -> "Hôm nay"
        LAST_7_DAYS  -> "7 ngày trước"
        LAST_30_DAYS -> "30 ngày trước"
        LAST_MONTH   -> "Tháng trước"
        OLDER        -> "Cũ hơn"
    }
}

data class NoteSection(
    val group : NoteGroup,
    val notes : List<NoteEntity>,
)

data class NotesUiState(
    val sections  : List<NoteSection> = emptyList(),
    val query     : String             = "",
    val isLoading : Boolean            = false,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repo: NoteRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<NotesUiState> = combine(
        repo.getAll(),
        _query,
    ) { allNotes, q ->
        val today   = LocalDate.now()
        val filtered = if (q.isBlank()) allNotes
                       else allNotes.filter { it.title.contains(q, ignoreCase = true) ||
                                              it.content.contains(q, ignoreCase = true) }

        val pinned   = filtered.filter { it.isPinned }
        val unpinned = filtered.filter { !it.isPinned }

        fun bucketOf(note: NoteEntity): NoteGroup {
            val daysAgo = ChronoUnit.DAYS.between(note.date, today)
            return when {
                daysAgo == 0L  -> NoteGroup.TODAY
                daysAgo <= 7   -> NoteGroup.LAST_7_DAYS
                daysAgo <= 30  -> NoteGroup.LAST_30_DAYS
                daysAgo <= 60  -> NoteGroup.LAST_MONTH
                else           -> NoteGroup.OLDER
            }
        }

        val sections = buildList {
            if (pinned.isNotEmpty()) add(NoteSection(NoteGroup.PINNED, pinned))
            NoteGroup.entries
                .filter { it != NoteGroup.PINNED }
                .forEach { grp ->
                    val notes = unpinned.filter { bucketOf(it) == grp }
                    if (notes.isNotEmpty()) add(NoteSection(grp, notes))
                }
        }

        NotesUiState(sections = sections, query = q)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        NotesUiState(isLoading = true),
    )

    fun setQuery(q: String) { _query.value = q }

    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            repo.insert(NoteEntity(title = title, content = content, date = LocalDate.now()))
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch { repo.setPinned(note.id, !note.isPinned) }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { repo.delete(note) }
    }
}
