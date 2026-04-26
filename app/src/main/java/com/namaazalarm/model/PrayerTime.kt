package com.namaazalarm.model

import java.io.Serializable

data class PrayerTime(
    val prayer: PrayerName,
    val hour: Int,   // 24-hour format
    val minute: Int
) : Serializable {
    // Display formatting is handled by TimeFormatter.format() which respects the 12/24h setting.
    // Do not add a toDisplayString() here — it would bypass the user preference.

    fun toMillisForDate(year: Int, month: Int, day: Int): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month - 1)
            set(java.util.Calendar.DAY_OF_MONTH, day)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
