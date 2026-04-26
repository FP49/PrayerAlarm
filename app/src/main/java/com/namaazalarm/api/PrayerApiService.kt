package com.namaazalarm.api

import com.namaazalarm.model.DailyPrayers
import com.namaazalarm.model.PrayerName
import com.namaazalarm.model.PrayerTime
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a full month of prayer times from the Aladhan API.
 *
 * API: https://aladhan.com/prayer-times-api
 * Endpoint used: GET /v1/calendar/{year}/{month}
 *
 * Parameters:
 *   latitude, longitude — device GPS (more accurate than city name)
 *   method              — calculation method (see CalculationMethod enum)
 *   school=1            — Hanafi Asr calculation (sent for all methods)
 *   tune               — optional manual offset in seconds per prayer
 *
 * All prayer times returned by Aladhan are in 24-hour format,
 * in the device's local timezone. No conversion needed.
 *
 * Called from a coroutine on Dispatchers.IO — this is a blocking call.
 */
class PrayerApiService {

    companion object {
        private const val BASE_URL = "https://api.aladhan.com/v1"
        private const val TIMEOUT_MS = 15_000
    }

    data class ApiResult(
        val days: List<DailyPrayers>,
        val error: String? = null
    ) {
        val success get() = error == null
    }

    /**
     * Fetches prayer times for a full month.
     * @param latitude  Device latitude
     * @param longitude Device longitude
     * @param month     1-based month (1=January)
     * @param year      Full year (e.g. 2026)
     * @param method    Aladhan calculation method ID
     */
    fun fetchMonth(
        latitude: Double,
        longitude: Double,
        month: Int,
        year: Int,
        method: CalculationMethod = CalculationMethod.DEFAULT
    ): ApiResult {
        return try {
            val url = buildUrl(latitude, longitude, month, year, method.id)
            val json = httpGet(url) ?: return ApiResult(emptyList(), "No response from server")
            parseResponse(json, month, year)
        } catch (e: java.net.UnknownHostException) {
            ApiResult(emptyList(), "No internet connection. Connect to WiFi or mobile data and try again.")
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult(emptyList(), "Connection timed out. Check your internet and try again.")
        } catch (e: Exception) {
            ApiResult(emptyList(), "Error fetching prayer times: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // URL builder
    // ─────────────────────────────────────────────────────────────

    private fun buildUrl(
        lat: Double, lon: Double,
        month: Int, year: Int, methodId: Int
    ): String {
        return "$BASE_URL/calendar/$year/$month" +
               "?latitude=$lat&longitude=$lon" +
               "&method=$methodId" +
               "&school=1"     // school=1 = Hanafi Asr for all methods
    }

    // ─────────────────────────────────────────────────────────────
    // HTTP GET
    // ─────────────────────────────────────────────────────────────

    private fun httpGet(urlString: String): String? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        return try {
            conn.apply {
                requestMethod      = "GET"
                connectTimeout     = TIMEOUT_MS
                readTimeout        = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NamaazAlarm/1.0 Android")
            }

            val code = conn.responseCode
            if (code != 200) {
                return null
            }

            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // JSON parser
    // ─────────────────────────────────────────────────────────────

    private fun parseResponse(json: String, month: Int, year: Int): ApiResult {
        val root   = JSONObject(json)
        val code   = root.optInt("code", 0)

        if (code != 200) {
            val status = root.optString("status", "Unknown error")
            return ApiResult(emptyList(), "API error: $status")
        }

        val dataArray = root.getJSONArray("data")
        val days      = mutableListOf<DailyPrayers>()

        for (i in 0 until dataArray.length()) {
            val entry   = dataArray.getJSONObject(i)
            val timings = entry.getJSONObject("timings")
            val date    = entry.getJSONObject("date")
            val greg    = date.getJSONObject("gregorian")
            val day     = greg.optString("day", "0").trim().toIntOrNull() ?: continue

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
            ApiResult(emptyList(), "No prayer times found in the response.")
        else
            ApiResult(days)
    }

    /**
     * Aladhan returns times as "HH:MM (TZ)" e.g. "04:08 (BST)" or just "04:08".
     * Strip anything after a space and parse the HH:MM portion.
     */
    private fun parseTime(raw: String): Pair<Int, Int>? {
        val clean = raw.trim().split(" ").firstOrNull() ?: return null
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h to m
    }
}
