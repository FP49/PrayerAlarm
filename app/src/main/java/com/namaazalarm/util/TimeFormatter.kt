package com.namaazalarm.util

/**
 * Central time formatting. All display of prayer times passes through here.
 * Internal storage is always 24-hour. This converts for display only.
 */
object TimeFormatter {

    fun format(hour: Int, minute: Int, use24Hour: Boolean): String {
        return if (use24Hour) {
            String.format("%02d:%02d", hour, minute)
        } else {
            val h    = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
            val amPm = if (hour < 12) "AM" else "PM"
            String.format("%d:%02d %s", h, minute, amPm)
        }
    }

    /** Format countdown seconds into H:MM:SS or MM:SS */
    fun formatCountdown(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else              String.format("%02d:%02d", m, s)
    }
}
