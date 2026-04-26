package com.namaazalarm.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.namaazalarm.alarm.AlarmScheduler
import com.namaazalarm.util.PrefsManager
import java.util.concurrent.TimeUnit

/**
 * Runs every 15 minutes via WorkManager.
 * Reschedules any prayer alarms that Samsung may have cancelled
 * due to aggressive battery management.
 * setAlarmClock() is idempotent — safe to call multiple times.
 */
class KeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs     = PrefsManager(applicationContext)
        val timetable = prefs.loadTimetable() ?: return Result.success()

        // Reschedule all future alarms
        AlarmScheduler(applicationContext).scheduleAllPrayerAlarms(timetable.days)

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "namaaz_keepalive"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,   // Don't replace if already running
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
