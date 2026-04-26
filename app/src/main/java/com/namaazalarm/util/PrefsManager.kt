package com.namaazalarm.util

import android.content.Context
import com.google.gson.Gson
import com.namaazalarm.api.CalculationMethod
import com.namaazalarm.model.MonthlyTimetable

class PrefsManager(context: Context) {

    private val prefs = context.getSharedPreferences("namaaz_prefs", Context.MODE_PRIVATE)
    private val gson  = Gson()

    // Timetable
    fun saveTimetable(t: MonthlyTimetable) = prefs.edit().putString(KEY_TIMETABLE, gson.toJson(t)).apply()
    fun loadTimetable(): MonthlyTimetable? = prefs.getString(KEY_TIMETABLE, null)?.let {
        try { gson.fromJson(it, MonthlyTimetable::class.java) } catch (e: Exception) { null }
    }
    fun clearTimetable() = prefs.edit().remove(KEY_TIMETABLE).apply()

    // Main alarm sound
    fun saveAlarmSoundPath(p: String)      = prefs.edit().putString(KEY_ALARM_SOUND_PATH, p).apply()
    fun getAlarmSoundPath(): String?       = prefs.getString(KEY_ALARM_SOUND_PATH, null)
    fun saveAlarmSoundDurationMs(ms: Long) = prefs.edit().putLong(KEY_ALARM_SOUND_DURATION, ms).apply()
    fun getAlarmSoundDurationMs(): Long    = prefs.getLong(KEY_ALARM_SOUND_DURATION, 0L)

    // Fajr separate sound
    fun setFajrSeparateEnabled(b: Boolean) = prefs.edit().putBoolean(KEY_FAJR_SEPARATE, b).apply()
    fun isFajrSeparateEnabled(): Boolean   = prefs.getBoolean(KEY_FAJR_SEPARATE, false)
    fun saveFajrSoundPath(p: String)       = prefs.edit().putString(KEY_FAJR_SOUND_PATH, p).apply()
    fun getFajrSoundPath(): String?        = prefs.getString(KEY_FAJR_SOUND_PATH, null)
    fun saveFajrSoundDurationMs(ms: Long)  = prefs.edit().putLong(KEY_FAJR_SOUND_DURATION, ms).apply()
    fun getFajrSoundDurationMs(): Long     = prefs.getLong(KEY_FAJR_SOUND_DURATION, 0L)

    // Time format
    fun set24HourMode(b: Boolean) = prefs.edit().putBoolean(KEY_24_HOUR, b).apply()
    fun is24HourMode(): Boolean   = prefs.getBoolean(KEY_24_HOUR, false)

    // Calculation method
    fun saveCalculationMethodId(id: Int) = prefs.edit().putInt(KEY_CALC_METHOD, id).apply()
    fun getCalculationMethodId(): Int    = prefs.getInt(KEY_CALC_METHOD, CalculationMethod.DEFAULT.id)

    // Alarm codes
    fun addAlarmRequestCode(code: Int) {
        val s = getAllAlarmCodes().toMutableSet().also { it.add(code) }
        prefs.edit().putStringSet(KEY_ALARM_CODES, s.map { it.toString() }.toSet()).apply()
    }
    fun getAllAlarmCodes(): Set<Int> =
        (prefs.getStringSet(KEY_ALARM_CODES, emptySet()) ?: emptySet())
            .mapNotNull { it.toIntOrNull() }.toSet()
    fun removeAlarmRequestCode(code: Int) {
        val s = getAllAlarmCodes().toMutableSet().also { it.remove(code) }
        prefs.edit().putStringSet(KEY_ALARM_CODES, s.map { it.toString() }.toSet()).apply()
    }
    fun clearAlarmCodes() = prefs.edit().remove(KEY_ALARM_CODES).apply()

    // Fired alarms
    fun markAlarmFired(year: Int, month: Int, day: Int, prayerName: String) {
        val s = getFiredAlarmKeys().toMutableSet().also { it.add("$year-$month-$day-$prayerName") }
        prefs.edit().putStringSet(KEY_FIRED_ALARMS, s).apply()
    }
    fun getFiredAlarmKeys(): Set<String> =
        prefs.getStringSet(KEY_FIRED_ALARMS, emptySet()) ?: emptySet()

    // Battery dialogs
    fun setUserDismissedBatteryOpt(b: Boolean)    = prefs.edit().putBoolean(KEY_DISMISSED_BATTERY_OPT, b).apply()
    fun userDismissedBatteryOpt(): Boolean         = prefs.getBoolean(KEY_DISMISSED_BATTERY_OPT, false)
    fun setUserConfirmedSleepingApps(b: Boolean)  = prefs.edit().putBoolean(KEY_CONFIRMED_SLEEPING_APPS, b).apply()
    fun userConfirmedSleepingApps(): Boolean       = prefs.getBoolean(KEY_CONFIRMED_SLEEPING_APPS, false)

    companion object {
        private const val KEY_TIMETABLE              = "timetable_json"
        private const val KEY_ALARM_SOUND_PATH       = "alarm_sound_path"
        private const val KEY_ALARM_SOUND_DURATION   = "alarm_sound_duration_ms"
        private const val KEY_FAJR_SEPARATE          = "fajr_separate_enabled"
        private const val KEY_FAJR_SOUND_PATH        = "fajr_sound_path"
        private const val KEY_FAJR_SOUND_DURATION    = "fajr_sound_duration_ms"
        private const val KEY_24_HOUR                = "use_24_hour_format"
        private const val KEY_CALC_METHOD            = "calculation_method_id"
        private const val KEY_ALARM_CODES            = "alarm_request_codes"
        private const val KEY_FIRED_ALARMS           = "fired_alarm_keys"
        private const val KEY_DISMISSED_BATTERY_OPT  = "dismissed_battery_opt"
        private const val KEY_CONFIRMED_SLEEPING_APPS= "confirmed_sleeping_apps"
    }
}
