package com.namaazalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.namaazalarm.MainActivity
import com.namaazalarm.R
import com.namaazalarm.model.PrayerName
import com.namaazalarm.util.PrefsManager

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val REMINDER_CHANNEL_ID = "namaaz_reminder_ch"
        const val ACTION_CLEANUP      = "com.namaazalarm.DAILY_CLEANUP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val timetable = PrefsManager(context).loadTimetable()
                if (timetable != null) {
                    AlarmScheduler(context).scheduleAllPrayerAlarms(timetable.days)
                }
            }
            ACTION_CLEANUP -> {
                performDailyCleanup(context)
            }
            else -> {
                val prayerName = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME) ?: return
                val alarmType  = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_TYPE, AlarmScheduler.TYPE_ALARM)
                when (alarmType) {
                    AlarmScheduler.TYPE_REMINDER_30 -> showReminderNotification(context, prayerName, 30)
                    AlarmScheduler.TYPE_REMINDER_10 -> showReminderNotification(context, prayerName, 10)
                    else -> startFullAlarm(context, intent, prayerName)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Daily cleanup — remove previous day's alarms
    // ─────────────────────────────────────────────────────────────

    private fun performDailyCleanup(context: Context) {
        val prefs    = PrefsManager(context)
        val cal      = java.util.Calendar.getInstance()
        val today    = Triple(cal.get(java.util.Calendar.YEAR),
                              cal.get(java.util.Calendar.MONTH) + 1,
                              cal.get(java.util.Calendar.DAY_OF_MONTH))

        val timetable = prefs.loadTimetable() ?: return
        val scheduler = AlarmScheduler(context)

        // Cancel AlarmManager entries for any day strictly before today
        timetable.days
            .filter { day ->
                day.year < today.first ||
                (day.year == today.first && day.month < today.second) ||
                (day.year == today.first && day.month == today.second && day.day < today.third)
            }
            .forEach { day ->
                PrayerName.values().forEach { prayer ->
                    listOf(
                        AlarmScheduler.TYPE_ALARM,
                        AlarmScheduler.TYPE_REMINDER_30,
                        AlarmScheduler.TYPE_REMINDER_10
                    ).forEach { type ->
                        val code = AlarmScheduler.requestCode(day.year, day.month, day.day, prayer, type)
                        scheduler.cancelSingleAlarm(code)
                        prefs.removeAlarmRequestCode(code)
                    }
                }
            }
    }

    // ─────────────────────────────────────────────────────────────
    // Reminder notification
    // ─────────────────────────────────────────────────────────────

    private fun showReminderNotification(context: Context, prayerName: String, minutesBefore: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureReminderChannel(nm)

        val tapPi = PendingIntent.getActivity(
            context, minutesBefore * 100,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = try { PrayerName.valueOf(prayerName).displayName } catch (e: Exception) { prayerName }

        nm.notify(
            PrayerName.values().indexOfFirst { it.name == prayerName } * 10 + (minutesBefore / 10),
            NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setContentTitle("$minutesBefore minutes until $displayName")
                .setContentText("$displayName prayer begins in $minutesBefore minutes")
                .setSmallIcon(R.drawable.ic_mosque)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tapPi)
                .setAutoCancel(true)   // Reminders CAN be dismissed by swipe
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        )
    }

    private fun ensureReminderChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
            val soundUri   = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttr  = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            nm.createNotificationChannel(
                NotificationChannel(REMINDER_CHANNEL_ID, "Prayer Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "30 and 10 minute prayer reminders"
                    setSound(soundUri, audioAttr)
                    enableVibration(true)
                }
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Full alarm
    // ─────────────────────────────────────────────────────────────

    private fun startFullAlarm(context: Context, intent: Intent, prayerName: String) {
        val si = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, prayerName)
            putExtra(AlarmScheduler.EXTRA_DAY,   intent.getIntExtra(AlarmScheduler.EXTRA_DAY, 0))
            putExtra(AlarmScheduler.EXTRA_MONTH, intent.getIntExtra(AlarmScheduler.EXTRA_MONTH, 0))
            putExtra(AlarmScheduler.EXTRA_YEAR,  intent.getIntExtra(AlarmScheduler.EXTRA_YEAR, 0))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(si)
        else
            context.startService(si)
    }
}
