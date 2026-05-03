package com.namaazalarm.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    // Hijri date (single string fallback)
    fun saveHijriDate(date: String) = prefs.edit().putString(KEY_HIJRI_DATE, date).apply()
    fun getHijriDate(): String      = prefs.getString(KEY_HIJRI_DATE, "") ?: ""

    // Hijri date map — gregorian day Int -> hijri date string
    fun saveHijriDateMap(map: Map<Int, String>) {
        prefs.edit().putString(KEY_HIJRI_DATE_MAP, gson.toJson(map)).apply()
    }
    fun getHijriDateMap(): Map<Int, String> {
        val json = prefs.getString(KEY_HIJRI_DATE_MAP, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<Int, String>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

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
    // Step 1: battery optimisation dismissed by user this session
    fun setUserDismissedBatteryOpt(b: Boolean)   = prefs.edit().putBoolean(KEY_DISMISSED_BATTERY_OPT, b).apply()
    fun userDismissedBatteryOpt(): Boolean        = prefs.getBoolean(KEY_DISMISSED_BATTERY_OPT, false)

    // Step 2: sleeping apps — versioned so update resets the flag and re-shows the dialog
    // This ensures users who updated from an old build see Step 2 again with the corrected deep link
    fun setUserConfirmedSleepingApps(b: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CONFIRMED_SLEEPING_APPS, b)
            .putInt(KEY_SLEEPING_APPS_VERSION, SLEEPING_APPS_CURRENT_VERSION)
            .apply()
    }
    fun userConfirmedSleepingApps(): Boolean {
        val confirmedVersion = prefs.getInt(KEY_SLEEPING_APPS_VERSION, 0)
        // If confirmed on an older version of the dialog, treat as not confirmed
        // so the updated dialog with the correct deep link is shown
        if (confirmedVersion < SLEEPING_APPS_CURRENT_VERSION) return false
        return prefs.getBoolean(KEY_CONFIRMED_SLEEPING_APPS, false)
    }

    // Update settings
    fun setUpdateCheckEnabled(b: Boolean) = prefs.edit().putBoolean(KEY_UPDATE_CHECK_ENABLED, b).apply()
    fun isUpdateCheckEnabled(): Boolean   = prefs.getBoolean(KEY_UPDATE_CHECK_ENABLED, true)
    fun setAutoUpdateEnabled(b: Boolean)  = prefs.edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, b).apply()
    fun isAutoUpdateEnabled(): Boolean    = prefs.getBoolean(KEY_AUTO_UPDATE_ENABLED, false)

    // Pending update
    fun savePendingUpdate(version: String, url: String) {
        prefs.edit().putString(KEY_PENDING_UPDATE_VERSION, version).putString(KEY_PENDING_UPDATE_URL, url).apply()
    }
    fun getPendingUpdateVersion(): String = prefs.getString(KEY_PENDING_UPDATE_VERSION, "") ?: ""
    fun getPendingUpdateUrl(): String     = prefs.getString(KEY_PENDING_UPDATE_URL, "") ?: ""
    fun clearPendingUpdate() {
        prefs.edit().remove(KEY_PENDING_UPDATE_VERSION).remove(KEY_PENDING_UPDATE_URL).apply()
    }
    fun hasPendingUpdate(): Boolean = getPendingUpdateVersion().isNotBlank()

    companion object {
        // Increment this when the sleeping apps dialog changes (deep link, text, etc.)
        // Existing installs will re-show the dialog automatically on next open
        private const val SLEEPING_APPS_CURRENT_VERSION = 2

        private const val KEY_TIMETABLE               = "timetable_json"
        private const val KEY_ALARM_SOUND_PATH        = "alarm_sound_path"
        private const val KEY_ALARM_SOUND_DURATION    = "alarm_sound_duration_ms"
        private const val KEY_FAJR_SEPARATE           = "fajr_separate_enabled"
        private const val KEY_FAJR_SOUND_PATH         = "fajr_sound_path"
        private const val KEY_FAJR_SOUND_DURATION     = "fajr_sound_duration_ms"
        private const val KEY_24_HOUR                 = "use_24_hour_format"
        private const val KEY_CALC_METHOD             = "calculation_method_id"
        private const val KEY_HIJRI_DATE              = "hijri_date"
        private const val KEY_HIJRI_DATE_MAP          = "hijri_date_map"
        private const val KEY_ALARM_CODES             = "alarm_request_codes"
        private const val KEY_FIRED_ALARMS            = "fired_alarm_keys"
        private const val KEY_DISMISSED_BATTERY_OPT  = "dismissed_battery_opt"
        private const val KEY_CONFIRMED_SLEEPING_APPS = "confirmed_sleeping_apps"
        private const val KEY_SLEEPING_APPS_VERSION   = "sleeping_apps_dialog_version"
        private const val KEY_UPDATE_CHECK_ENABLED    = "update_check_enabled"
        private const val KEY_AUTO_UPDATE_ENABLED     = "auto_update_enabled"
        private const val KEY_PENDING_UPDATE_VERSION  = "pending_update_version"
        private const val KEY_PENDING_UPDATE_URL      = "pending_update_url"
    }
}
