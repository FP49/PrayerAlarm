package com.namaazalarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.namaazalarm.api.CalculationMethod
import com.namaazalarm.api.PrayerApiService
import com.namaazalarm.databinding.ActivityFetchTimetableBinding
import com.namaazalarm.model.MonthlyTimetable
import com.namaazalarm.util.LocationHelper
import com.namaazalarm.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class FetchTimetableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFetchTimetableBinding
    private lateinit var prefs: PrefsManager

    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0
    private var locationReady = false
    private var selectedMethod = CalculationMethod.DEFAULT

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) fetchLocation()
        else showLocationDenied()
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFetchTimetableBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Fetch Prayer Times"

        prefs          = PrefsManager(this)
        selectedMethod = CalculationMethod.fromId(prefs.getCalculationMethodId())

        binding.tvSelectedMethod.text = selectedMethod.displayName
        binding.btnChangeMethod.setOnClickListener { showMethodPicker() }
        binding.btnFetch.setOnClickListener        { onFetchTapped() }
        binding.btnFetch.isEnabled = false

        requestLocation()
    }

    // ─────────────────────────────────────────────────────────────
    // Location
    // ─────────────────────────────────────────────────────────────

    private fun requestLocation() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val granted = perms.any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) fetchLocation() else locationPermLauncher.launch(perms)
    }

    private fun fetchLocation() {
        setStatus("Detecting your location...")
        binding.progressBar.visibility = View.VISIBLE

        LocationHelper(this).getLocationCoords { lat, lon, cityName ->
            runOnUiThread {
                if (lat == 0.0 && lon == 0.0) {
                    showLocationDenied()
                    return@runOnUiThread
                }
                currentLat     = lat
                currentLon     = lon
                locationReady  = true

                binding.progressBar.visibility = View.GONE
                binding.tvLocationName.text    = cityName
                binding.tvCoords.text          = "%.4f, %.4f".format(lat, lon)
                setStatus("Location ready. Tap 'Fetch Prayer Times' to continue.")
                binding.btnFetch.isEnabled = true
            }
        }
    }

    private fun showLocationDenied() {
        binding.progressBar.visibility = View.GONE
        setStatus("Location permission denied.\n\nPrayer times cannot be calculated without your location. Please grant location permission.")
        binding.btnFetch.isEnabled = false

        AlertDialog.Builder(this)
            .setTitle("Location Required")
            .setMessage(
                "This app needs your location to calculate accurate prayer times for your area.\n\n" +
                "Tap 'Grant Permission' to allow location access."
            )
            .setPositiveButton("Grant Permission") { _, _ -> requestLocation() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    // Calculation method picker
    // ─────────────────────────────────────────────────────────────

    private fun showMethodPicker() {
        val methods     = CalculationMethod.values()
        val names       = methods.map { it.displayName }.toTypedArray()
        val currentIdx  = methods.indexOfFirst { it.id == selectedMethod.id }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Calculation Method")
            .setSingleChoiceItems(names, currentIdx) { dialog, which ->
                selectedMethod = methods[which]
                prefs.saveCalculationMethodId(selectedMethod.id)
                binding.tvSelectedMethod.text = selectedMethod.displayName
                binding.tvMethodDesc.text     = selectedMethod.description
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    // Fetch
    // ─────────────────────────────────────────────────────────────

    private fun onFetchTapped() {
        if (!locationReady) { requestLocation(); return }

        val cal   = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year  = cal.get(Calendar.YEAR)

        setStatus("Fetching prayer times for ${monthName(month)} $year...")
        binding.progressBar.visibility = View.VISIBLE
        binding.btnFetch.isEnabled     = false
        binding.btnChangeMethod.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                PrayerApiService().fetchMonth(
                    latitude  = currentLat,
                    longitude = currentLon,
                    month     = month,
                    year      = year,
                    method    = selectedMethod
                )
            }

            binding.progressBar.visibility    = View.GONE
            binding.btnFetch.isEnabled        = true
            binding.btnChangeMethod.isEnabled = true

            if (!result.success) {
                setStatus("Error: ${result.error}")
                Toast.makeText(this@FetchTimetableActivity, result.error, Toast.LENGTH_LONG).show()
                return@launch
            }

            // Save timetable
            prefs.saveTimetable(
                MonthlyTimetable(
                    month      = month,
                    year       = year,
                    mosqueName = binding.tvLocationName.text.toString(),
                    days       = result.days
                )
            )

            setStatus("${result.days.size} days loaded for ${monthName(month)} $year.")
            Toast.makeText(
                this@FetchTimetableActivity,
                "${result.days.size} days fetched successfully.",
                Toast.LENGTH_SHORT
            ).show()

            // Go to review screen
            startActivity(
                android.content.Intent(this@FetchTimetableActivity, AlarmPreviewActivity::class.java).apply {
                    putExtra(AlarmPreviewActivity.EXTRA_FROM_UPLOAD, true)
                }
            )
            finish()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────

    private fun setStatus(msg: String) { binding.tvStatus.text = msg }

    private fun monthName(month: Int) = java.text.DateFormatSymbols().months[month - 1]
}
