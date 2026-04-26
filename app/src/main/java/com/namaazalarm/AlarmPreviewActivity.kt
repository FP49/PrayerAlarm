package com.namaazalarm

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.namaazalarm.alarm.AlarmScheduler
import com.namaazalarm.databinding.ActivityAlarmPreviewBinding
import com.namaazalarm.model.DailyPrayers
import com.namaazalarm.model.MonthlyTimetable
import com.namaazalarm.model.PrayerName
import com.namaazalarm.model.PrayerTime
import com.namaazalarm.util.PrefsManager
import com.namaazalarm.util.TimeFormatter

class AlarmPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmPreviewBinding
    private lateinit var prefs: PrefsManager
    private lateinit var adapter: AlarmPreviewAdapter
    private lateinit var timetable: MonthlyTimetable

    companion object { const val EXTRA_FROM_UPLOAD = "from_upload" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        val loaded = prefs.loadTimetable()
        if (loaded == null) { finish(); return }
        timetable = loaded

        val fromUpload = intent.getBooleanExtra(EXTRA_FROM_UPLOAD, false)
        supportActionBar?.title = "${timetable.getMonthName()} ${timetable.year}"
        binding.tvInstructions.text = if (fromUpload)
            "Review times below. Tap any time to correct it, then press Set All Alarms."
        else
            "Prayer times for ${timetable.getMonthName()} ${timetable.year}. Tap any time to edit."

        adapter = AlarmPreviewAdapter(
            days     = timetable.days.toMutableList(),
            use24Hour = prefs.is24HourMode(),
            onEdit   = { day, prayer, time -> showEditDialog(day, prayer, time) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnSetAlarms.setOnClickListener    { confirmSetAlarms() }
        binding.btnCancelAlarms.setOnClickListener { confirmCancelAlarms() }
    }

    private fun showEditDialog(day: DailyPrayers, prayer: PrayerName, current: PrayerTime) {
        val view   = layoutInflater.inflate(R.layout.dialog_edit_time, null)
        val picker = view.findViewById<android.widget.TimePicker>(R.id.timePicker)
        picker.setIs24HourView(prefs.is24HourMode())
        picker.hour = current.hour; picker.minute = current.minute

        AlertDialog.Builder(this)
            .setTitle("Edit ${prayer.displayName} - Day ${day.day}")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                commitEdit(day, prayer, PrayerTime(prayer, picker.hour, picker.minute))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun commitEdit(day: DailyPrayers, prayer: PrayerName, newTime: PrayerTime) {
        val updated = timetable.days.map { d ->
            if (d.day == day.day)
                d.copy(prayers = d.prayers.toMutableMap().apply { put(prayer, newTime) })
            else d
        }
        timetable = timetable.copy(days = updated)
        prefs.saveTimetable(timetable)
        adapter.updateDays(updated)
        val display = TimeFormatter.format(newTime.hour, newTime.minute, prefs.is24HourMode())
        Toast.makeText(this, "${prayer.displayName} day ${day.day} set to $display", Toast.LENGTH_SHORT).show()
    }

    private fun confirmSetAlarms() {
        if (prefs.getAlarmSoundPath().isNullOrBlank()) {
            AlertDialog.Builder(this)
                .setTitle("No Alarm Sound Selected")
                .setMessage("No alarm sound has been chosen. Alarms will play white noise. Proceed?")
                .setPositiveButton("Audio Settings") { _, _ ->
                    startActivity(android.content.Intent(this, AudioSetupActivity::class.java))
                }
                .setNegativeButton("Proceed") { _, _ -> doSetAlarms() }.show()
        } else { doSetAlarms() }
    }

    private fun doSetAlarms() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val am = getSystemService(android.app.AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Exact Alarm Permission Needed")
                    .setMessage("Enable 'Alarms and reminders' for Namaaz Alarm in the next screen.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Continue") { _, _ -> actuallySetAlarms() }.show()
                return
            }
        }
        actuallySetAlarms()
    }

    private fun actuallySetAlarms() {
        binding.btnSetAlarms.isEnabled = false
        val scheduler = AlarmScheduler(this)
        scheduler.cancelAllAlarms()
        val count = scheduler.scheduleAllPrayerAlarms(timetable.days)
        binding.btnSetAlarms.isEnabled = true
        Toast.makeText(this, "$count alarms set for ${timetable.getMonthName()} ${timetable.year}.", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun confirmCancelAlarms() {
        AlertDialog.Builder(this)
            .setTitle("Cancel All Alarms")
            .setMessage("Remove all ${timetable.getMonthName()} prayer alarms?")
            .setPositiveButton("Yes") { _, _ ->
                AlarmScheduler(this).cancelAllAlarms()
                Toast.makeText(this, "All alarms cancelled.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null).show()
    }
}
