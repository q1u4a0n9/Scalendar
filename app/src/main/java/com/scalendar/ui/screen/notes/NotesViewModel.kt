package com.scalendar.ui.screen.notes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scalendar.R
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.data.repository.AuthRepository
import com.scalendar.data.repository.FirestoreRepository
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

@Composable
fun NoteGroup.localizedName(): String = when (this) {
    NoteGroup.PINNED       -> stringResource(R.string.note_group_pinned)
    NoteGroup.TODAY        -> stringResource(R.string.note_group_today)
    NoteGroup.LAST_7_DAYS  -> stringResource(R.string.note_group_7days)
    NoteGroup.LAST_30_DAYS -> stringResource(R.string.note_group_30days)
    NoteGroup.LAST_MONTH   -> stringResource(R.string.note_group_last_month)
    NoteGroup.OLDER        -> stringResource(R.string.note_group_older)
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
    private val repo         : NoteRepository,
    private val authRepo     : AuthRepository,
    private val firestoreRepo: FirestoreRepository,
) : ViewModel() {

    private val uid get() = authRepo.currentUser?.uid ?: ""

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

    /**
     * Save after closing the full-screen editor.
     * - id == 0L → new note (insert)
     * - id != 0L → existing note (update; preserves isPinned / date)
     * If both title and content are blank, the note is discarded.
     * If title is blank but content is not, the first line of content becomes the title.
     */
    fun saveNote(original: NoteEntity, title: String, content: String) {
        val trimTitle   = title.trim()
        val trimContent = content.trim()
        if (trimTitle.isBlank() && trimContent.isBlank()) return   // discard truly empty note
        val finalTitle  = trimTitle.ifBlank {
            trimContent.lines().firstOrNull { it.isNotBlank() }?.take(50) ?: return
        }
        viewModelScope.launch {
            if (original.id == 0L) {
                val entity = NoteEntity(
                    title   = finalTitle,
                    content = trimContent,
                    date    = LocalDate.now(),
                )
                val newId = repo.insert(entity)
                firestoreRepo.upsertNote(uid, entity.copy(id = newId))
            } else {
                val updated = original.copy(title = finalTitle, content = trimContent)
                repo.update(updated)
                firestoreRepo.upsertNote(uid, updated)
            }
        }
    }

    fun togglePin(note: NoteEntity) {
        val pinned = !note.isPinned
        viewModelScope.launch {
            repo.setPinned(note.id, pinned)
            firestoreRepo.setNotePinned(uid, note.id, pinned)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repo.delete(note)
            firestoreRepo.deleteNote(uid, note.id)
        }
    }
}
