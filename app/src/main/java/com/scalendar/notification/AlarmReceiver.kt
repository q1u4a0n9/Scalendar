package com.scalendar.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.scalendar.R
import com.scalendar.ScalendarApp
import com.scalendar.util.LocaleHelper

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Wrap with app locale so strings follow the user's in-app language choice,
        // not the device system locale.
        val ctx = LocaleHelper.wrap(context, LocaleHelper.getLang(context))

        val entryId       = intent.getLongExtra(EXTRA_ENTRY_ID, 0L)
        val entryTitle    = intent.getStringExtra(EXTRA_ENTRY_TITLE)
                            ?: ctx.getString(R.string.notif_default_title)
        val minutesBefore = intent.getIntExtra(EXTRA_MINUTES_BEFORE, 0)

        val message = when {
            minutesBefore <= 0    -> ctx.getString(R.string.notif_starting, entryTitle)
            minutesBefore < 60    -> ctx.getString(R.string.notif_minutes,  minutesBefore, entryTitle)
            minutesBefore < 1440  -> ctx.getString(R.string.notif_hours,    minutesBefore / 60, entryTitle)
            minutesBefore == 1440 -> ctx.getString(R.string.notif_tomorrow, entryTitle)
            else                  -> ctx.getString(R.string.notif_days,     minutesBefore / 1440, entryTitle)
        }

        val notification = NotificationCompat.Builder(context, ScalendarApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = ((entryId and 0x7FFFF) * 100 + minutesBefore).toInt()
        nm.notify(notifId, notification)
    }

    companion object {
        const val EXTRA_ENTRY_ID      = "entry_id"
        const val EXTRA_ENTRY_TITLE   = "entry_title"
        const val EXTRA_MINUTES_BEFORE = "minutes_before"
    }
}
