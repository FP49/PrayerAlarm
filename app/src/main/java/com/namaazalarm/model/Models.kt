package com.namaazalarm.model

import java.io.Serializable

data class DailyPrayers(
    val day: Int,
    val month: Int,
    val year: Int,
    val prayers: Map<PrayerName, PrayerTime>
) : Serializable {
    fun getDayOfWeekName(): String {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month - 1)
            set(java.util.Calendar.DAY_OF_MONTH, day)
        }
        return java.text.DateFormatSymbols().shortWeekdays[cal.get(java.util.Calendar.DAY_OF_WEEK)]
    }
}

data class MonthlyTimetable(
    val month: Int,
    val year: Int,
    val mosqueName: String,
    val days: List<DailyPrayers>
) : Serializable {
    fun getMonthName(): String = java.text.DateFormatSymbols().months[month - 1]
}
