package com.scalendar.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.scalendar.data.database.entity.EntryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule all reminders defined in [entry.reminderOffsets].
     * Values are now minute offsets before the event start time.
     * e.g. "30" = fire 30 minutes before the event.
     */
    fun schedule(entry: EntryEntity) {
        val offsets = parseOffsets(entry.reminderOffsets)
        // Event base time: use startTime if available, otherwise 09:00
        val eventTime = entry.date.atTime(entry.startTime ?: LocalTime.of(9, 0))

        offsets.forEach { minutesBefore ->
            val triggerMillis = eventTime
                .minusMinutes(minutesBefore.toLong())
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            if (triggerMillis <= System.currentTimeMillis()) return@forEach  // already past

            val pi = buildPendingIntent(entry.id, entry.title, minutesBefore)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                // Exact alarm not granted — open system settings so user can allow it.
                // This only triggers once; after grant, all future schedules use exact alarms.
                val settingsIntent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settingsIntent)
                // Still schedule as inexact in case user dismisses the dialog.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
        }
    }

    /** Cancel all pending reminders for this entry. */
    fun cancel(entry: EntryEntity) {
        val offsets = parseOffsets(entry.reminderOffsets)
        offsets.forEach { minutesBefore ->
            alarmManager.cancel(buildPendingIntent(entry.id, entry.title, minutesBefore))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun parseOffsets(raw: String): List<Int> =
        if (raw.isBlank()) emptyList()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull() }

    private fun buildPendingIntent(
        entryId       : Long,
        entryTitle    : String,
        minutesBefore : Int,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ENTRY_ID,       entryId)
            putExtra(AlarmReceiver.EXTRA_ENTRY_TITLE,    entryTitle)
            putExtra(AlarmReceiver.EXTRA_MINUTES_BEFORE, minutesBefore)
        }
        // Mask entryId to lower 19 bits to prevent Int overflow (supports ~500k unique entries)
        val requestCode = ((entryId and 0x7FFFF) * 100 + minutesBefore).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
