package com.namaazalarm.api

import com.namaazalarm.model.DailyPrayers
import com.namaazalarm.model.PrayerName
import com.namaazalarm.model.PrayerTime
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a full month of prayer times from the Aladhan API.
 * Extracts the Hijri date for EVERY day of the month.
 * The Hijri day begins at Maghrib so MainActivity uses the
 * hijriDateMap + today's Maghrib time to decide which date to show.
 *
 * Endpoint: GET https://api.aladhan.com/v1/calendar/{year}/{month}
 * Must be called from Dispatchers.IO only.
 */
class PrayerApiService {

    companion object {
        private const val BASE_URL   = "https://api.aladhan.com/v1"
        private const val TIMEOUT_MS = 15_000
    }

    data class ApiResult(
        val days: List<DailyPrayers>,
        val hijriDate: String = "",
        val hijriDateMap: Map<Int, String> = emptyMap(),
        val error: String? = null
    ) {
        val success get() = error == null
    }

    fun fetchMonth(
        latitude: Double,
        longitude: Double,
        month: Int,
        year: Int,
        method: CalculationMethod = CalculationMethod.DEFAULT
    ): ApiResult {
        return try {
            val url  = buildUrl(latitude, longitude, month, year, method.id)
            val json = httpGet(url) ?: return ApiResult(emptyList(), "", emptyMap(), "No response from server")
            parseResponse(json, month, year)
        } catch (e: java.net.UnknownHostException) {
            ApiResult(emptyList(), "", emptyMap(), "No internet connection. Connect to WiFi or mobile data and try again.")
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult(emptyList(), "", emptyMap(), "Connection timed out. Check your internet and try again.")
        } catch (e: Exception) {
            ApiResult(emptyList(), "", emptyMap(), "Error fetching prayer times: ${e.message}")
        }
    }

    private fun buildUrl(lat: Double, lon: Double, month: Int, year: Int, methodId: Int): String =
        "$BASE_URL/calendar/$year/$month" +
        "?latitude=$lat&longitude=$lon" +
        "&method=$methodId" +
        "&school=1"

    private fun httpGet(urlString: String): String? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        return try {
            conn.apply {
                requestMethod  = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout    = TIMEOUT_MS
                setRequestProperty("Accept",     "application/json")
                setRequestProperty("User-Agent", "NamaazAlarm/1.0 Android")
            }
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseResponse(json: String, month: Int, year: Int): ApiResult {
        val root = JSONObject(json)
        val code = root.optInt("code", 0)
        if (code != 200) {
            return ApiResult(emptyList(), "", emptyMap(), "API error: ${root.optString("status", "Unknown error")}")
        }

        val dataArray    = root.getJSONArray("data")
        val days         = mutableListOf<DailyPrayers>()
        val hijriDateMap = mutableMapOf<Int, String>()
        var firstHijri   = ""

        for (i in 0 until dataArray.length()) {
            val entry   = dataArray.getJSONObject(i)
            val timings = entry.getJSONObject("timings")
            val date    = entry.getJSONObject("date")
            val greg    = date.getJSONObject("gregorian")
            val day     = greg.optString("day", "0").trim().toIntOrNull() ?: continue

            try {
                val hijri      = date.getJSONObject("hijri")
                val hijriDay   = hijri.optString("day",  "").trim()
                val hijriMonth = hijri.getJSONObject("month").optString("en", "").trim()
                val hijriYear  = hijri.optString("year", "").trim()
                if (hijriDay.isNotBlank() && hijriMonth.isNotBlank() && hijriYear.isNotBlank()) {
                    val hijriString    = "$hijriDay $hijriMonth $hijriYear AH"
                    hijriDateMap[day]  = hijriString
                    if (i == 0) firstHijri = hijriString
                }
            } catch (e: Exception) { /* Hijri block absent */ }

            val prayers = mutableMapOf<PrayerName, PrayerTime>()
            parseTime(timings.optString("Fajr",    ""))?.let { prayers[PrayerName.FAJR]    = PrayerTime(PrayerName.FAJR,    it.first, it.second) }
            parseTime(timings.optString("Dhuhr",   ""))?.let { prayers[PrayerName.ZUHR]    = PrayerTime(PrayerName.ZUHR,    it.first, it.second) }
            parseTime(timings.optString("Asr",     ""))?.let { prayers[PrayerName.ASR]     = PrayerTime(PrayerName.ASR,     it.first, it.second) }
            parseTime(timings.optString("Maghrib", ""))?.let { prayers[PrayerName.MAGHRIB] = PrayerTime(PrayerName.MAGHRIB, it.first, it.second) }
            parseTime(timings.optString("Isha",    ""))?.let { prayers[PrayerName.ISHA]    = PrayerTime(PrayerName.ISHA,    it.first, it.second) }

            if (prayers.size == 5) {
                days.add(DailyPrayers(day = day, month = month, year = year, prayers = prayers))
            }
        }

        return if (days.isEmpty())
            ApiResult(emptyList(), "", emptyMap(), "No prayer times found in the response.")
        else
            ApiResult(days, firstHijri, hijriDateMap)
    }

    private fun parseTime(raw: String): Pair<Int, Int>? {
        val clean = raw.trim().split(" ").firstOrNull() ?: return null
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h to m
    }
}
