package com.scalendar.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.scalendar.R
import com.scalendar.ScalendarApp

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryId    = intent.getLongExtra(EXTRA_ENTRY_ID, 0L)
        val entryTitle = intent.getStringExtra(EXTRA_ENTRY_TITLE) ?: "Sự kiện"
        val daysBefore = intent.getIntExtra(EXTRA_DAYS_BEFORE, 0)

        val message = when (daysBefore) {
            0    -> "Hôm nay: $entryTitle"
            1    -> "Ngày mai: $entryTitle"
            else -> "Còn $daysBefore ngày: $entryTitle"
        }

        val notification = NotificationCompat.Builder(context, ScalendarApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Scalendar – Nhắc nhở")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use a unique id per entry+offset so multiple reminders don't overwrite each other
        val notifId = ((entryId * 100) + daysBefore).toInt()
        nm.notify(notifId, notification)
    }

    companion object {
        const val EXTRA_ENTRY_ID    = "entry_id"
        const val EXTRA_ENTRY_TITLE = "entry_title"
        const val EXTRA_DAYS_BEFORE = "days_before"
    }
}
