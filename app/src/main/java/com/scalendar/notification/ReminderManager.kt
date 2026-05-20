package com.scalendar.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.scalendar.data.database.entity.EntryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Schedule all reminders defined in entry.reminderOffsets. */
    fun schedule(entry: EntryEntity) {
        val offsets = parseOffsets(entry.reminderOffsets)
        offsets.forEach { daysBefore ->
            val triggerMillis = entry.date
                .minusDays(daysBefore.toLong())
                .atTime(9, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            if (triggerMillis <= System.currentTimeMillis()) return@forEach  // already past

            val pi = buildPendingIntent(entry.id, entry.title, daysBefore)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                // Fallback: approximate alarm (no special permission needed)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerMillis, pi
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerMillis, pi
                )
            }
        }
    }

    /** Cancel all pending reminders for this entry. */
    fun cancel(entry: EntryEntity) {
        val offsets = parseOffsets(entry.reminderOffsets)
        offsets.forEach { daysBefore ->
            alarmManager.cancel(buildPendingIntent(entry.id, entry.title, daysBefore))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun parseOffsets(raw: String): List<Int> =
        if (raw.isBlank()) emptyList()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull() }

    private fun buildPendingIntent(
        entryId    : Long,
        entryTitle : String,
        daysBefore : Int,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ENTRY_ID,    entryId)
            putExtra(AlarmReceiver.EXTRA_ENTRY_TITLE, entryTitle)
            putExtra(AlarmReceiver.EXTRA_DAYS_BEFORE, daysBefore)
        }
        val requestCode = ((entryId * 100) + daysBefore).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
