package com.scalendar.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.data.database.entity.UserCalendarEntity
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.model.TimeOfDay
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs local Room data to/from Firestore under `users/{uid}/`.
 *
 * Document paths:
 *   users/{uid}/entries/{entryId}
 *   users/{uid}/notes/{noteId}
 *   users/{uid}/user_calendars/{calId}
 *
 * All writes are fire-and-forget (best-effort) — UI does not wait for cloud confirmation.
 * Call [fetchAndMergeAll] once after login to pull server data into Room.
 */
@Singleton
class FirestoreRepository @Inject constructor() {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // ── Paths ─────────────────────────────────────────────────────────

    private fun entriesCol(uid: String)   = db.collection("users/$uid/entries")
    private fun notesCol(uid: String)     = db.collection("users/$uid/notes")
    private fun calCol(uid: String)       = db.collection("users/$uid/user_calendars")
    /** Single document that holds all user preferences (color overrides, etc.). */
    private fun settingsDoc(uid: String)  = db.document("users/$uid/settings/prefs")

    // ═══════════════════════════════════════════════════════════════════
    // Entries
    // ═══════════════════════════════════════════════════════════════════

    /** Upsert a single entry. Silent no-op if uid is blank. */
    suspend fun upsertEntry(uid: String, entry: EntryEntity) {
        if (uid.isBlank()) return
        runCatching {
            entriesCol(uid).document(entry.id.toString())
                .set(entry.toMap(), SetOptions.merge())
                .await()
        }
    }

    /** Delete multiple entries by their IDs (e.g. entire recurring series). Fire-and-forget. */
    suspend fun deleteEntries(uid: String, ids: List<Long>) {
        ids.forEach { deleteEntry(uid, it) }
    }

    /** Delete a single entry by ID. Silent no-op if uid is blank. */
    suspend fun deleteEntry(uid: String, entryId: Long) {
        if (uid.isBlank()) return
        runCatching {
            entriesCol(uid).document(entryId.toString()).delete().await()
        }
    }

    /** Partially update the `isCompleted` field without fetching the full entry. */
    suspend fun setEntryCompleted(uid: String, entryId: Long, done: Boolean) {
        if (uid.isBlank()) return
        runCatching {
            entriesCol(uid).document(entryId.toString())
                .update("isCompleted", done)
                .await()
        }
    }

    /** Fetch all entries for this user from Firestore. Returns empty list on error. */
    suspend fun fetchEntries(uid: String): List<EntryEntity> {
        if (uid.isBlank()) return emptyList()
        return runCatching {
            entriesCol(uid).get().await().documents
                .mapNotNull { it.data?.toEntryEntity() }
        }.getOrDefault(emptyList())
    }

    // ═══════════════════════════════════════════════════════════════════
    // Notes
    // ═══════════════════════════════════════════════════════════════════

    suspend fun upsertNote(uid: String, note: NoteEntity) {
        if (uid.isBlank()) return
        runCatching {
            notesCol(uid).document(note.id.toString())
                .set(note.toMap(), SetOptions.merge())
                .await()
        }
    }

    suspend fun deleteNote(uid: String, noteId: Long) {
        if (uid.isBlank()) return
        runCatching {
            notesCol(uid).document(noteId.toString()).delete().await()
        }
    }

    suspend fun setNotePinned(uid: String, noteId: Long, pinned: Boolean) {
        if (uid.isBlank()) return
        runCatching {
            notesCol(uid).document(noteId.toString())
                .update("isPinned", pinned)
                .await()
        }
    }

    suspend fun fetchNotes(uid: String): List<NoteEntity> {
        if (uid.isBlank()) return emptyList()
        return runCatching {
            notesCol(uid).get().await().documents
                .mapNotNull { it.data?.toNoteEntity() }
        }.getOrDefault(emptyList())
    }

    // ═══════════════════════════════════════════════════════════════════
    // User Calendars
    // ═══════════════════════════════════════════════════════════════════

    suspend fun upsertUserCalendar(uid: String, cal: UserCalendarEntity) {
        if (uid.isBlank()) return
        runCatching {
            calCol(uid).document(cal.id)
                .set(mapOf("id" to cal.id, "name" to cal.name, "colorHex" to cal.colorHex),
                    SetOptions.merge())
                .await()
        }
    }

    suspend fun deleteUserCalendar(uid: String, calId: String) {
        if (uid.isBlank()) return
        runCatching {
            calCol(uid).document(calId).delete().await()
        }
    }

    suspend fun fetchUserCalendars(uid: String): List<UserCalendarEntity> {
        if (uid.isBlank()) return emptyList()
        return runCatching {
            calCol(uid).get().await().documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                UserCalendarEntity(
                    id       = data["id"] as? String ?: return@mapNotNull null,
                    name     = data["name"] as? String ?: "",
                    colorHex = data["colorHex"] as? String ?: "1E88E5",
                )
            }
        }.getOrDefault(emptyList())
    }

    // ═══════════════════════════════════════════════════════════════════
    // User Settings (color overrides, etc.)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Merge one color override into the settings document.
     * Key stored as "cat_color_<id>" so it's namespaced from other prefs.
     */
    suspend fun upsertColorOverride(uid: String, id: String, hex: String) {
        if (uid.isBlank()) return
        runCatching {
            settingsDoc(uid)
                .set(mapOf("cat_color_$id" to hex), SetOptions.merge())
                .await()
        }
    }

    /**
     * Push all color overrides at once (used during sign-up/push-local flow).
     * Existing unrelated settings fields are preserved via merge.
     */
    suspend fun upsertAllColorOverrides(uid: String, overrides: Map<String, String>) {
        if (uid.isBlank() || overrides.isEmpty()) return
        runCatching {
            val map = overrides.mapKeys { (k, _) -> "cat_color_$k" }
            settingsDoc(uid).set(map, SetOptions.merge()).await()
        }
    }

    /**
     * Fetch stored color overrides for this user.
     * Returns Map<categoryId, hex> e.g. ("TASK" → "F4511E").
     */
    suspend fun fetchColorOverrides(uid: String): Map<String, String> {
        if (uid.isBlank()) return emptyMap()
        return runCatching {
            val data = settingsDoc(uid).get().await().data ?: return emptyMap()
            data.entries
                .filter { (k, _) -> k.startsWith("cat_color_") }
                .mapNotNull { (k, v) ->
                    val hex = v as? String ?: return@mapNotNull null
                    if (hex.isBlank()) null else k.removePrefix("cat_color_") to hex
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    // ═══════════════════════════════════════════════════════════════════
    // Converters
    // ═══════════════════════════════════════════════════════════════════

    private fun EntryEntity.toMap(): Map<String, Any?> = mapOf(
        "id"             to id,
        "title"          to title,
        "category"       to category.name,
        "timeOfDay"      to timeOfDay.name,
        "startTime"      to startTime?.toString(),
        "endTime"        to endTime?.toString(),
        "date"           to date.toString(),
        "isCompleted"    to isCompleted,
        "isImportant"    to isImportant,
        "description"    to description,
        "linkedNoteId"   to linkedNoteId,
        "color"          to color,
        "reminderOffsets" to reminderOffsets,
        "isRecurring"    to isRecurring,
        "recurrenceType" to recurrenceType,
        "seriesId"       to seriesId,
        "deadlineDate"   to deadlineDate?.toString(),
        "location"       to location,
    )

    private fun Map<String, Any?>.toEntryEntity(): EntryEntity? = runCatching {
        EntryEntity(
            id             = (this["id"] as? Long) ?: 0L,
            title          = this["title"] as? String ?: "",
            category       = EntryCategory.valueOf(this["category"] as? String ?: "TASK"),
            timeOfDay      = TimeOfDay.valueOf(this["timeOfDay"] as? String ?: "ANYTIME"),
            startTime      = (this["startTime"] as? String)?.let { LocalTime.parse(it) },
            endTime        = (this["endTime"] as? String)?.let { LocalTime.parse(it) },
            date           = LocalDate.parse(this["date"] as? String ?: return null),
            isCompleted    = this["isCompleted"] as? Boolean ?: false,
            isImportant    = this["isImportant"] as? Boolean ?: false,
            description    = this["description"] as? String ?: "",
            linkedNoteId   = this["linkedNoteId"] as? Long,
            color          = this["color"] as? String ?: "DEFAULT",
            reminderOffsets = this["reminderOffsets"] as? String ?: "",
            isRecurring    = this["isRecurring"] as? Boolean ?: false,
            recurrenceType = this["recurrenceType"] as? String ?: "NONE",
            seriesId       = this["seriesId"] as? String ?: "",
            deadlineDate   = (this["deadlineDate"] as? String)?.let { LocalDate.parse(it) },
            location       = this["location"] as? String ?: "",
        )
    }.getOrNull()

    private fun NoteEntity.toMap(): Map<String, Any?> = mapOf(
        "id"       to id,
        "title"    to title,
        "content"  to content,
        "date"     to date.toString(),
        "isPinned" to isPinned,
    )

    private fun Map<String, Any?>.toNoteEntity(): NoteEntity? = runCatching {
        NoteEntity(
            id       = (this["id"] as? Long) ?: 0L,
            title    = this["title"] as? String ?: "",
            content  = this["content"] as? String ?: "",
            date     = LocalDate.parse(this["date"] as? String ?: return null),
            isPinned = this["isPinned"] as? Boolean ?: false,
        )
    }.getOrNull()
}
