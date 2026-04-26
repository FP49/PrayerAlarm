package com.namaazalarm.util

import android.os.Build

/**
 * Returns the current Hijri (Islamic) date as a formatted string.
 * Uses Android's built-in ICU IslamicCalendar on API 24+ (covers SM-T290 running API 28).
 * Falls back to a Julian Day Number calculation on older devices.
 */
object HijriHelper {

    private val MONTH_NAMES = arrayOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
        "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhul Qadah", "Dhul Hijjah"
    )

    fun getHijriDateString(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val cal   = android.icu.util.IslamicCalendar()
                val day   = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
                val month = cal.get(android.icu.util.Calendar.MONTH)
                val year  = cal.get(android.icu.util.Calendar.YEAR)
                "$day ${MONTH_NAMES.getOrElse(month) { "?" }} $year AH"
            } catch (e: Exception) {
                fallbackHijri()
            }
        } else {
            fallbackHijri()
        }
    }

    /**
     * Arithmetic Hijri approximation (Umm al-Qura style).
     * Accurate to within 1-2 days without an internet lookup.
     */
    private fun fallbackHijri(): String {
        val today = java.util.Calendar.getInstance()
        val y = today.get(java.util.Calendar.YEAR)
        val m = today.get(java.util.Calendar.MONTH) + 1
        val d = today.get(java.util.Calendar.DAY_OF_MONTH)

        // Julian Day Number for Gregorian date
        val a = (14 - m) / 12
        val yr = y + 4800 - a
        val mo = m + 12 * a - 3
        val jdn = d + (153 * mo + 2) / 5 + 365 * yr + yr / 4 - yr / 100 + yr / 400 - 32045

        // Convert JDN to Hijri
        val l  = jdn - 1948440 + 10632
        val n  = (l - 1) / 10631
        val l2 = l - 10631 * n + 354
        val j  = ((10985 - l2) / 5316) * ((50 * l2) / 17719) +
                 (l2 / 5670) * ((43 * l2) / 15238)
        val l3 = l2 - ((30 - j) / 15) * ((17719 * j) / 50) -
                 (j / 16) * ((15238 * j) / 43) + 29
        val hMonth = (24 * l3) / 709
        val hDay   = l3 - (709 * hMonth) / 24
        val hYear  = 30 * n + j - 30

        return "$hDay ${MONTH_NAMES.getOrElse(hMonth - 1) { "?" }} $hYear AH"
    }
}
