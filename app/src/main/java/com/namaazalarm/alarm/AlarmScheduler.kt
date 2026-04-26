package com.namaazalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.namaazalarm.MainActivity
import com.namaazalarm.model.DailyPrayers
import com.namaazalarm.model.PrayerName
import com.namaazalarm.util.PrefsManager
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs        = PrefsManager(context)

    companion object {
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_ALARM_TYPE  = "extra_alarm_type"
        const val EXTRA_DAY         = "extra_day"
        const val EXTRA_MONTH       = "extra_month"
        const val EXTRA_YEAR        = "extra_year"

        const val TYPE_ALARM       = 0
        const val TYPE_REMINDER_30 = 1
        const val TYPE_REMINDER_10 = 2

        val REMINDER_PRAYERS = setOf(
            PrayerName.ZUHR, PrayerName.ASR, PrayerName.MAGHRIB, PrayerName.ISHA
        )

        fun requestCode(year: Int, month: Int, day: Int, prayer: PrayerName, type: Int): Int {
            val y = year % 100
            val p = PrayerName.values().indexOf(prayer)
            return y * 1_000_000 + month * 10_000 + day * 100 + p * 3 + type
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Schedule all
    // ─────────────────────────────────────────────────────────────

    fun scheduleAllPrayerAlarms(days: List<DailyPrayers>): Int {
        var count = 0
        val now   = System.currentTimeMillis()

        for (day in days) {
            for ((prayer, time) in day.prayers) {
                val triggerMs = time.toMillisForDate(day.year, day.month, day.day)
                if (triggerMs <= now) continue

                // Full prayer alarm — setAlarmClock fires in Ultra Power Saving mode
                val code = requestCode(day.year, day.month, day.day, prayer, TYPE_ALARM)
                val pi   = buildPi(code, prayer, day, TYPE_ALARM)
                scheduleAlarmClock(triggerMs, pi, code)
                prefs.addAlarmRequestCode(code)
                count++

                // Reminders for Zuhr-Isha
                if (prayer in REMINDER_PRAYERS) {
                    listOf(TYPE_REMINDER_30 to 30L, TYPE_REMINDER_10 to 10L).forEach { (type, mins) ->
                        val remMs = triggerMs - mins * 60_000
                        if (remMs > now) {
                            val remCode = requestCode(day.year, day.month, day.day, prayer, type)
                            scheduleExact(remMs, buildPi(remCode, prayer, day, type))
                            prefs.addAlarmRequestCode(remCode)
                            count++
                        }
                    }
                }
            }
        }

        // Schedule daily midnight cleanup alarm
        scheduleDailyCleanup()

        return count
    }

    // ─────────────────────────────────────────────────────────────
    // Cancel
    // ─────────────────────────────────────────────────────────────

    fun cancelAllAlarms() {
        prefs.getAllAlarmCodes().forEach { cancelSingleAlarm(it) }
        prefs.clearAlarmCodes()
        cancelDailyCleanup()
    }

    fun cancelSingleAlarm(code: Int) {
        val intent  = Intent(context, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pending?.let { alarmManager.cancel(it) }
    }

    // ─────────────────────────────────────────────────────────────
    // Daily midnight cleanup alarm
    // ─────────────────────────────────────────────────────────────

    private fun scheduleDailyCleanup() {
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val pi = PendingIntent.getBroadcast(
            context, 9999999,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_CLEANUP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use repeating so it fires every midnight
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            midnight,
            AlarmManager.INTERVAL_DAY,
            pi
        )
    }

    private fun cancelDailyCleanup() {
        val pi = PendingIntent.getBroadcast(
            context, 9999999,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_CLEANUP
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { alarmManager.cancel(it) }
    }

    // ─────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────

    private fun buildPi(code: Int, prayer: PrayerName, day: DailyPrayers, type: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, AlarmReceiver::class.java).apply {
                putExtra(EXTRA_PRAYER_NAME, prayer.name)
                putExtra(EXTRA_ALARM_TYPE,  type)
                putExtra(EXTRA_DAY,         day.day)
                putExtra(EXTRA_MONTH,       day.month)
                putExtra(EXTRA_YEAR,        day.year)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun scheduleAlarmClock(triggerMs: Long, pi: PendingIntent, code: Int) {
        val showPi = PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, showPi), pi)
    }

    private fun scheduleExact(triggerMs: Long, pi: PendingIntent) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
    }
}
