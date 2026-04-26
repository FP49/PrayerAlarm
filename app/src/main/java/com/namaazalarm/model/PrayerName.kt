package com.namaazalarm.model

enum class PrayerName(val displayName: String, val arabicName: String) {
    FAJR("Fajr", "فجر"),
    ZUHR("Zuhr", "ظهر"),
    ASR("Asr", "عصر"),
    MAGHRIB("Maghrib", "مغرب"),
    ISHA("Isha", "عشاء")
}
