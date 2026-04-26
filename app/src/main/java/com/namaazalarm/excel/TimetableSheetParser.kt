package com.namaazalarm.excel

import android.content.Context
import android.net.Uri
import com.namaazalarm.model.DailyPrayers
import com.namaazalarm.model.PrayerName
import com.namaazalarm.model.PrayerTime
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Parses a user-created Excel (.xlsx) or CSV timetable into DailyPrayers.
 *
 * Expected column layout (any order, case-insensitive):
 *   Date/Day  -- day number of month (1..31)
 *   Fajr      -- prayer start time
 *   Zuhr      -- prayer start time (also accepts Dhuhr)
 *   Asr       -- prayer start time
 *   Maghrib   -- prayer start time
 *   Isha      -- prayer start time
 *
 * Times may be formatted as:
 *   5:25 / 05:25 / 5:25 AM / 5:25 PM / 17:25
 *   OR as Excel decimal fractions (e.g. 0.22569 = 05:25)
 *
 * For XLSX: parsed without any third-party library using ZipInputStream.
 * For CSV: standard comma or tab delimited, first row is headers.
 */
class TimetableSheetParser(private val context: Context) {

    data class ParseResult(
        val days: List<DailyPrayers>,
        val error: String? = null
    ) {
        val success get() = error == null
    }

    fun parse(uri: Uri, month: Int, year: Int): ParseResult {
        val fileName = getFileName(uri).lowercase()
        return when {
            fileName.endsWith(".xlsx") -> parseXlsx(uri, month, year)
            fileName.endsWith(".xls")  -> ParseResult(emptyList(),
                "Old .xls format is not supported. Please save as .xlsx or .csv in Excel.")
            fileName.endsWith(".csv")  -> parseCsv(uri, month, year)
            else                       -> ParseResult(emptyList(),
                "Unsupported file type. Please use .xlsx or .csv")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // XLSX parser
    // ─────────────────────────────────────────────────────────────

    private fun parseXlsx(uri: Uri, month: Int, year: Int): ParseResult {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return ParseResult(emptyList(), "Could not open file.")

            val zipBytes = stream.use { it.readBytes() }
            val sharedStrings = extractSharedStrings(zipBytes)
            val rows = extractSheetRows(zipBytes, sharedStrings)

            if (rows.isEmpty()) {
                return ParseResult(emptyList(), "No data found in sheet. Ensure Sheet 1 has data.")
            }

            buildDailyPrayers(rows, month, year)

        } catch (e: Exception) {
            ParseResult(emptyList(), "Error reading Excel file: ${e.message}")
        }
    }

    private fun extractSharedStrings(zipBytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        try {
            ZipInputStream(zipBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/sharedStrings.xml") {
                        val parser = newParser(zip)
                        var inT = false
                        var current = ""
                        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                            when (parser.eventType) {
                                XmlPullParser.START_TAG -> if (parser.name == "t") { inT = true; current = "" }
                                XmlPullParser.TEXT      -> if (inT) current += parser.text
                                XmlPullParser.END_TAG   -> if (parser.name == "t") { strings.add(current); inT = false }
                            }
                            parser.next()
                        }
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) { /* No shared strings -- inline strings only */ }
        return strings
    }

    private fun extractSheetRows(zipBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    val parser     = newParser(zip)
                    var inC        = false
                    var inV        = false
                    var cellType   = ""
                    var cellVal    = ""
                    var currentRow = mutableListOf<String>()

                    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                        when (parser.eventType) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "row" -> { currentRow = mutableListOf() }
                                "c"   -> { inC = true; cellType = parser.getAttributeValue(null, "t") ?: ""; cellVal = "" }
                                "v", "t" -> if (inC) inV = true
                            }
                            XmlPullParser.TEXT -> if (inV) cellVal += parser.text
                            XmlPullParser.END_TAG -> when (parser.name) {
                                "v", "t" -> inV = false
                                "c" -> {
                                    val resolved = when (cellType) {
                                        "s"          -> sharedStrings.getOrElse(cellVal.toIntOrNull() ?: -1) { cellVal }
                                        "str"        -> cellVal
                                        "inlineStr"  -> cellVal
                                        else         -> cellVal
                                    }
                                    currentRow.add(resolved.trim())
                                    inC = false
                                }
                                "row" -> { if (currentRow.isNotEmpty()) rows.add(currentRow.toList()) }
                            }
                        }
                        parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }
        return rows
    }

    // ─────────────────────────────────────────────────────────────
    // CSV parser
    // ─────────────────────────────────────────────────────────────

    private fun parseCsv(uri: Uri, month: Int, year: Int): ParseResult {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return ParseResult(emptyList(), "Could not open file.")

            val rows = stream.use { s ->
                InputStreamReader(s).readLines()
                    .filter { it.isNotBlank() }
                    .map { line -> parseCsvLine(line) }
            }

            if (rows.isEmpty()) return ParseResult(emptyList(), "CSV file is empty.")

            buildDailyPrayers(rows, month, year)

        } catch (e: Exception) {
            ParseResult(emptyList(), "Error reading CSV file: ${e.message}")
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result  = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        for (ch in line) {
            when {
                ch == '"'                             -> inQuote = !inQuote
                (ch == ',' || ch == '\t') && !inQuote -> { result.add(current.toString().trim()); current.clear() }
                else                                  -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    // ─────────────────────────────────────────────────────────────
    // Row to DailyPrayers mapping
    // ─────────────────────────────────────────────────────────────

    private fun buildDailyPrayers(rows: List<List<String>>, month: Int, year: Int): ParseResult {
        // Find header row -- first row containing at least 2 prayer names
        val headerIdx = rows.indexOfFirst { row ->
            val joined = row.joinToString(" ").uppercase()
            listOf("FAJR", "ZUHR", "ASR", "MAGHRIB", "ISHA", "DHUHR").count { joined.contains(it) } >= 2
        }

        if (headerIdx < 0) {
            return ParseResult(emptyList(),
                "Could not find header row.\n\nEnsure your spreadsheet has a row with column " +
                "headings containing: Date, Fajr, Zuhr, Asr, Maghrib, Isha")
        }

        val headers = rows[headerIdx].map { it.uppercase().trim() }

        fun colFor(vararg keywords: String): Int =
            headers.indexOfFirst { h -> keywords.any { h.contains(it) } }

        val dateCol    = colFor("DATE", "DAY", "NO")
        val fajrCol    = colFor("FAJR")
        val zuhrCol    = colFor("ZUHR", "DHUHR", "DUHR")
        val asrCol     = colFor("ASR")
        val maghribCol = colFor("MAGHRIB", "MAGRIB")
        val ishaCol    = colFor("ISHA")

        val missingCols = mutableListOf<String>()
        if (fajrCol    < 0) missingCols.add("Fajr")
        if (zuhrCol    < 0) missingCols.add("Zuhr")
        if (asrCol     < 0) missingCols.add("Asr")
        if (maghribCol < 0) missingCols.add("Maghrib")
        if (ishaCol    < 0) missingCols.add("Isha")

        if (missingCols.isNotEmpty()) {
            return ParseResult(emptyList(),
                "Missing columns: ${missingCols.joinToString(", ")}.\n\n" +
                "Ensure your spreadsheet has columns named: Date, Fajr, Zuhr, Asr, Maghrib, Isha")
        }

        val days = mutableListOf<DailyPrayers>()

        for (i in (headerIdx + 1) until rows.size) {
            val row = rows[i]
            if (row.all { it.isBlank() }) continue

            val dayNum = if (dateCol >= 0 && dateCol < row.size) {
                val raw = row[dateCol].trim()
                // Handle Excel numeric date serials (e.g. 46923.0 = a date) -- extract just the day
                val dbl = raw.toDoubleOrNull()
                if (dbl != null && dbl >= 1.0) {
                    // Could be a day integer (1-31) stored as double, or a full Excel date serial
                    val intVal = dbl.toInt()
                    if (intVal in 1..31) intVal else null
                } else {
                    raw.toIntOrNull()
                }
            } else null

            if (dayNum == null || dayNum !in 1..31) continue

            fun extractTime(col: Int, prayer: PrayerName): PrayerTime? {
                if (col < 0 || col >= row.size) return null
                val raw = row[col].trim()
                if (raw.isBlank()) return null
                val (h, m) = parseTimeString(raw, prayer) ?: return null
                return PrayerTime(prayer, h, m)
            }

            val prayers = mutableMapOf<PrayerName, PrayerTime>()
            extractTime(fajrCol,    PrayerName.FAJR)?.let    { prayers[PrayerName.FAJR]    = it }
            extractTime(zuhrCol,    PrayerName.ZUHR)?.let    { prayers[PrayerName.ZUHR]    = it }
            extractTime(asrCol,     PrayerName.ASR)?.let     { prayers[PrayerName.ASR]     = it }
            extractTime(maghribCol, PrayerName.MAGHRIB)?.let { prayers[PrayerName.MAGHRIB] = it }
            extractTime(ishaCol,    PrayerName.ISHA)?.let    { prayers[PrayerName.ISHA]    = it }

            if (prayers.size == 5) {
                days.add(DailyPrayers(day = dayNum, month = month, year = year, prayers = prayers))
            }
        }

        return if (days.isEmpty())
            ParseResult(emptyList(), "No valid prayer time rows found. Check date column has numbers 1-31.")
        else
            ParseResult(days.sortedBy { it.day })
    }

    // ─────────────────────────────────────────────────────────────
    // Time string parsing
    // ─────────────────────────────────────────────────────────────

    /**
     * Handles all time formats:
     *   Excel decimal fraction : 0.22569 = 05:25
     *   Standard string        : 5:25 / 05:25 / 5:25 AM / 5:25 PM / 17:25
     */
    private fun parseTimeString(raw: String, prayer: PrayerName): Pair<Int, Int>? {
        val cleaned = raw.trim()

        // Excel stores times as a decimal fraction of a day (0.0 to 0.9999)
        // e.g. 0.22569 = 05:25,  0.05486 = 01:19
        val decimal = cleaned.toDoubleOrNull()
        if (decimal != null && decimal >= 0.0 && decimal < 1.0) {
            val totalMinutes = (decimal * 24.0 * 60.0).toInt()
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return applyPrayerHeuristic(h, m, prayer)
        }

        // String time: 5:25 / 05:25 / 5:25 AM / 17:25
        val upper  = cleaned.uppercase()
        val isAm   = upper.contains("AM")
        val isPm   = upper.contains("PM")
        val digits = upper.replace("AM", "").replace("PM", "").trim()
        val sep    = when {
            digits.contains(":") -> ":"
            digits.contains(".") -> "."
            else                 -> return null
        }
        val parts = digits.split(sep)
        if (parts.size < 2) return null

        var h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null

        when {
            isPm && h < 12  -> h += 12
            isAm && h == 12 -> h = 0
            !isAm && !isPm  -> return applyPrayerHeuristic(h, m, prayer)
        }

        return if (h in 0..23 && m in 0..59) h to m else null
    }

    /**
     * Infers AM/PM from the prayer's known time-of-day range when no AM/PM label is present.
     * Mosque timetables print times without AM/PM — this disambiguates them.
     */
    private fun applyPrayerHeuristic(h: Int, m: Int, prayer: PrayerName): Pair<Int, Int>? {
        if (h !in 0..23 || m !in 0..59) return null
        val adjusted = when (prayer) {
            PrayerName.FAJR    -> h
            PrayerName.ZUHR    -> if (h <= 2) h + 12 else h
            PrayerName.ASR     -> if (h <= 6) h + 12 else h
            PrayerName.MAGHRIB -> if (h < 12) h + 12 else h
            PrayerName.ISHA    -> if (h < 12) h + 12 else h
        }
        return adjusted to m
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun newParser(stream: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newPullParser().apply {
            setInput(stream, "UTF-8")
            next()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx) ?: ""
        }
        return name.ifBlank { uri.lastPathSegment ?: "" }
    }
}