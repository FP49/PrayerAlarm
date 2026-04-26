package com.namaazalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.namaazalarm.alarm.AlarmScheduler
import com.namaazalarm.databinding.ActivityMainBinding
import com.namaazalarm.model.PrayerName
import com.namaazalarm.model.PrayerTime
import com.namaazalarm.util.BatteryHelper
import com.namaazalarm.util.HijriHelper
import com.namaazalarm.util.LocationHelper
import com.namaazalarm.util.PrefsManager
import com.namaazalarm.util.TimeFormatter
import com.namaazalarm.worker.KeepAliveWorker
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    private var countdownHandler: Handler?   = null
    private var countdownRunnable: Runnable? = null

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.any { it }) loadLocationDisplay() }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        binding.btnFetchTimetable.setOnClickListener {
            startActivity(Intent(this, FetchTimetableActivity::class.java))
        }
        binding.btnImportTimetable.setOnClickListener {
            startActivity(Intent(this, ImportTimetableActivity::class.java))
        }
        binding.btnViewAlarms.setOnClickListener {
            startActivity(Intent(this, AlarmPreviewActivity::class.java))
        }
        binding.btnAudioSettings.setOnClickListener {
            startActivity(Intent(this, AudioSetupActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnImportTimetable.setOnClickListener {
            startActivity(Intent(this, ImportTimetableActivity::class.java))
        }

        KeepAliveWorker.schedule(this)
        checkBatteryDialogs()

        val storedHijri = prefs.getHijriDate()
        binding.tvHijriDate.text = if (storedHijri.isNotBlank()) storedHijri
                                   else HijriHelper.getHijriDateString()
        requestLocationDisplay()
        cleanupPreviousDayAlarms()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        checkBatteryDialogs()
        cleanupPreviousDayAlarms()
        refreshStatus()
        startCountdown()
    }

    override fun onPause() {
        super.onPause()
        stopCountdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
    }

    // ─────────────────────────────────────────────────────────────
    // Countdown
    // ─────────────────────────────────────────────────────────────

    private fun startCountdown() {
        stopCountdown()
        countdownHandler  = Handler(Looper.getMainLooper())
        countdownRunnable = object : Runnable {
            override fun run() {
                tickCountdown()
                countdownHandler?.postDelayed(this, 1000)
            }
        }
        countdownHandler?.post(countdownRunnable!!)
    }

    private fun stopCountdown() {
        countdownRunnable?.let { countdownHandler?.removeCallbacks(it) }
        countdownHandler  = null
        countdownRunnable = null
    }

    private fun tickCountdown() {
        val timetable = prefs.loadTimetable() ?: run {
            binding.countdownView.update(0f, "No data", "--:--")
            return
        }
        val cal   = Calendar.getInstance()
        val today = timetable.days.find {
            it.day   == cal.get(Calendar.DAY_OF_MONTH) &&
            it.month == cal.get(Calendar.MONTH) + 1    &&
            it.year  == cal.get(Calendar.YEAR)
        } ?: run {
            binding.countdownView.update(0f, "Done", "00:00"); return
        }

        val nowMs = cal.timeInMillis
        var nextPrayer: PrayerName? = null
        var nextMs = Long.MAX_VALUE
        var prevMs = 0L

        for (prayer in PrayerName.values()) {
            val pt = today.prayers[prayer] ?: continue
            val ms = pt.toMillisForDate(today.year, today.month, today.day)
            if (ms > nowMs && ms < nextMs) { nextPrayer = prayer; nextMs = ms }
            if (ms <= nowMs && ms > prevMs)  prevMs = ms
        }

        if (nextPrayer == null) {
            binding.countdownView.update(0f, "All done", "00:00"); return
        }

        val remainingMs = nextMs - nowMs
        val totalMs     = if (prevMs > 0) nextMs - prevMs else remainingMs
        val progress    = (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)

        binding.countdownView.update(
            progress,
            nextPrayer.displayName,
            TimeFormatter.formatCountdown(remainingMs / 1000)
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Today's prayer times header
    // ─────────────────────────────────────────────────────────────

    private fun refreshTodayPrayers() {
        val timetable = prefs.loadTimetable()
        val cal       = Calendar.getInstance()
        val today     = timetable?.days?.find {
            it.day   == cal.get(Calendar.DAY_OF_MONTH) &&
            it.month == cal.get(Calendar.MONTH) + 1    &&
            it.year  == cal.get(Calendar.YEAR)
        }
        val use24 = prefs.is24HourMode()

        fun fmt(prayer: PrayerName): String {
            val pt = today?.prayers?.get(prayer) ?: return "--:--"
            return TimeFormatter.format(pt.hour, pt.minute, use24)
        }

        binding.tvTodayFajr.text    = fmt(PrayerName.FAJR)
        binding.tvTodayZuhr.text    = fmt(PrayerName.ZUHR)
        binding.tvTodayAsr.text     = fmt(PrayerName.ASR)
        binding.tvTodayMaghrib.text = fmt(PrayerName.MAGHRIB)
        binding.tvTodayIsha.text    = fmt(PrayerName.ISHA)
    }

    // ─────────────────────────────────────────────────────────────
    // Location display (dashboard only — coords handled by FetchTimetableActivity)
    // ─────────────────────────────────────────────────────────────

    private fun requestLocationDisplay() {
        val perms   = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val granted = perms.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) loadLocationDisplay() else locationPermLauncher.launch(perms)
    }

    private fun loadLocationDisplay() {
        LocationHelper(this).getLocation { city -> runOnUiThread { binding.tvLocation.text = city } }
    }

    // ─────────────────────────────────────────────────────────────
    // Battery dialogs
    // ─────────────────────────────────────────────────────────────

    private fun checkBatteryDialogs() {
        val batteryDone = BatteryHelper.isIgnoringBatteryOptimizations(this)
        if (batteryDone) prefs.setUserDismissedBatteryOpt(false)
        when {
            !batteryDone && !prefs.userDismissedBatteryOpt() -> showBatteryOptDialog()
            batteryDone && !prefs.userConfirmedSleepingApps() -> showSleepingAppsDialog()
        }
    }

    private fun showBatteryOptDialog() {
        AlertDialog.Builder(this)
            .setTitle("Step 1 of 2: Battery Optimisation")
            .setMessage(
                "Prayer alarms may not fire with battery optimisation on.\n\n" +
                "Steps:\n" +
                "1. Tap 'Go to Settings' below\n" +
                "2. Tap the dropdown at the top, change to 'All apps'\n" +
                "3. Find Namaaz Alarm in the list\n" +
                "4. Tap it and select 'Don't optimise'\n" +
                "5. Press Back to return here\n\n" +
                "This dialog will disappear once the setting is confirmed."
            )
            .setPositiveButton("Go to Settings") { _, _ ->
                BatteryHelper.requestIgnoreBatteryOptimizations(this)
            }
            .setNegativeButton("Later") { _, _ ->
                prefs.setUserDismissedBatteryOpt(true)
            }
            .setCancelable(false)
            .show()
    }

    private fun showSleepingAppsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Step 2 of 2: Never Sleeping Apps")
            .setMessage(
                "One final step to keep alarms reliable on Samsung:\n\n" +
                "1. Tap 'Open Samsung Battery Settings' below\n" +
                "2. Tap 'Never sleeping apps'\n" +
                "3. Tap the + button at the top right\n" +
                "4. Find Namaaz Alarm and tick it\n" +
                "5. Tap Add\n" +
                "6. Come back here and tap 'Done'\n\n" +
                "This prevents Samsung from blocking alarms in the background."
            )
            .setPositiveButton("Open Samsung Battery Settings") { _, _ ->
                BatteryHelper.openNeverSleepingApps(this)
            }
            .setNegativeButton("Done - I have added it") { _, _ ->
                prefs.setUserConfirmedSleepingApps(true)
            }
            .setCancelable(false)
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    // Cleanup past days
    // ─────────────────────────────────────────────────────────────

    private fun cleanupPreviousDayAlarms() {
        val timetable = prefs.loadTimetable() ?: return
        val cal       = Calendar.getInstance()
        val todayY    = cal.get(Calendar.YEAR)
        val todayM    = cal.get(Calendar.MONTH) + 1
        val todayD    = cal.get(Calendar.DAY_OF_MONTH)

        val scheduler = AlarmScheduler(this)
        val pastDays  = timetable.days.filter { d ->
            d.year < todayY ||
            (d.year == todayY && d.month < todayM) ||
            (d.year == todayY && d.month == todayM && d.day < todayD)
        }
        if (pastDays.isEmpty()) return

        pastDays.forEach { day ->
            PrayerName.values().forEach { prayer ->
                listOf(AlarmScheduler.TYPE_ALARM, AlarmScheduler.TYPE_REMINDER_30,
                       AlarmScheduler.TYPE_REMINDER_10).forEach { type ->
                    val code = AlarmScheduler.requestCode(day.year, day.month, day.day, prayer, type)
                    scheduler.cancelSingleAlarm(code)
                    prefs.removeAlarmRequestCode(code)
                }
            }
        }

        val remaining = timetable.days.filter { d ->
            d.year > todayY ||
            (d.year == todayY && d.month > todayM) ||
            (d.year == todayY && d.month == todayM && d.day >= todayD)
        }
        prefs.saveTimetable(timetable.copy(days = remaining))
    }

    // ─────────────────────────────────────────────────────────────
    // Status
    // ─────────────────────────────────────────────────────────────

    private fun refreshStatus() {
        val timetable = prefs.loadTimetable()
        val cal       = Calendar.getInstance()
        val curMonth  = cal.get(Calendar.MONTH) + 1
        val curYear   = cal.get(Calendar.YEAR)

        when {
            timetable == null -> {
                binding.tvStatus.text =
                    "No prayer times loaded. Tap 'Fetch Prayer Times' to get this month's times."
                binding.btnViewAlarms.isEnabled = false
                binding.bannerMonthExpired.visibility = View.GONE
            }
            timetable.month != curMonth || timetable.year != curYear -> {
                binding.tvStatus.text =
                    "Times loaded for ${timetable.getMonthName()} ${timetable.year}. " +
                    "Please fetch ${monthName(curMonth)} $curYear."
                binding.btnViewAlarms.isEnabled = true
                binding.bannerMonthExpired.visibility = View.VISIBLE
            }
            else -> {
                binding.tvStatus.text =
                    "${timetable.getMonthName()} ${timetable.year} active. " +
                    "${timetable.days.size} days remaining."
                binding.btnViewAlarms.isEnabled = true
                binding.bannerMonthExpired.visibility = View.GONE
            }
        }
        refreshTodayPrayers()
    }

    private fun monthName(month: Int) = java.text.DateFormatSymbols().months[month - 1]
}
