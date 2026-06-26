package com.scalendar.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scalendar.data.repository.EntryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Re-schedule all upcoming reminders after device reboot.
 * AlarmManager clears all alarms on reboot; this receiver restores them.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repo           : EntryRepository
    @Inject lateinit var reminderManager: ReminderManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today  = LocalDate.now()
                val future = today.plusMonths(3)   // reschedule the next 3 months of entries
                val entries = repo.getByDateRange(today, future).first()
                entries
                    .filter { it.reminderOffsets.isNotBlank() }
                    .forEach { reminderManager.schedule(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
