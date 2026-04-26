package com.namaazalarm

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.namaazalarm.databinding.ActivityImportTimetableBinding
import com.namaazalarm.excel.TimetableSheetParser
import com.namaazalarm.model.MonthlyTimetable
import com.namaazalarm.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ImportTimetableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportTimetableBinding
    private lateinit var prefs: PrefsManager

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { processFile(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportTimetableBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Import Timetable"

        prefs = PrefsManager(this)

        binding.btnSelectFile.setOnClickListener {
            // Accept xlsx and csv
            fileLauncher.launch("*/*")
        }
        binding.btnBack.setOnClickListener { finish() }

        showInstructions()
    }

    private fun showInstructions() {
        binding.tvStatus.text =
            "How to create your spreadsheet:\n\n" +
            "1. Open Excel or Google Sheets\n" +
            "2. Create columns with these exact headings in row 1:\n" +
            "   Date | Fajr | Zuhr | Asr | Maghrib | Isha\n" +
            "3. Enter the prayer times from your mosque timetable\n" +
            "   (times can be 5:25, 05:25, 5:25 AM, or 17:25)\n" +
            "4. Save as .xlsx or .csv\n" +
            "5. Tap 'Select File' below"
    }

    private fun processFile(uri: Uri) {
        val cal   = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year  = cal.get(Calendar.YEAR)

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSelectFile.isEnabled = false
        binding.tvStatus.text = "Reading file..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TimetableSheetParser(this@ImportTimetableActivity).parse(uri, month, year)
            }

            binding.progressBar.visibility  = View.GONE
            binding.btnSelectFile.isEnabled = true

            if (!result.success) {
                binding.tvStatus.text = "Error:\n\n${result.error}"
                Toast.makeText(this@ImportTimetableActivity, result.error, Toast.LENGTH_LONG).show()
                return@launch
            }

            prefs.saveTimetable(
                MonthlyTimetable(
                    month      = month,
                    year       = year,
                    mosqueName = "Imported Timetable",
                    days       = result.days
                )
            )

            binding.tvStatus.text =
                "${result.days.size} days imported successfully for " +
                "${java.text.DateFormatSymbols().months[month - 1]} $year."

            Toast.makeText(
                this@ImportTimetableActivity,
                "${result.days.size} days imported.",
                Toast.LENGTH_SHORT
            ).show()

            startActivity(
                android.content.Intent(this@ImportTimetableActivity, AlarmPreviewActivity::class.java).apply {
                    putExtra(AlarmPreviewActivity.EXTRA_FROM_UPLOAD, true)
                }
            )
            finish()
        }
    }
}
