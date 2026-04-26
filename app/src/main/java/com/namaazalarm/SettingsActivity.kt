package com.namaazalarm

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.namaazalarm.databinding.ActivitySettingsBinding
import com.namaazalarm.util.PrefsManager
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    private val fajrAudioLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { handleFajrAudioPicked(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Settings"

        prefs = PrefsManager(this)

        // 24-hour toggle
        binding.switch24Hour.isChecked = prefs.is24HourMode()
        binding.switch24Hour.setOnCheckedChangeListener { _, checked ->
            prefs.set24HourMode(checked)
        }

        // Fajr separate toggle
        binding.switchFajrSeparate.isChecked = prefs.isFajrSeparateEnabled()
        updateFajrVisibility(prefs.isFajrSeparateEnabled())

        binding.switchFajrSeparate.setOnCheckedChangeListener { _, checked ->
            prefs.setFajrSeparateEnabled(checked)
            updateFajrVisibility(checked)
            if (!checked) {
                prefs.saveFajrSoundPath("")
                prefs.saveFajrSoundDurationMs(0L)
                File(filesDir, "alarm_sound_fajr.mp3").delete()
                refreshFajrStatus()
            }
        }

        binding.btnSelectFajrAudio.setOnClickListener { fajrAudioLauncher.launch("audio/*") }
        binding.btnClearFajrAudio.setOnClickListener  { clearFajrAudio() }
        binding.btnBack.setOnClickListener            { finish() }

        refreshFajrStatus()
    }

    private fun updateFajrVisibility(show: Boolean) {
        binding.layoutFajrSound.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun handleFajrAudioPicked(uri: Uri) {
        val ret = android.media.MediaMetadataRetriever()
        try {
            ret.setDataSource(this, uri)
            val durMs = ret.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLong() ?: 0L

            val dest = File(filesDir, "alarm_sound_fajr.mp3")
            contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(dest).use { out -> inp.copyTo(out) }
            }

            prefs.saveFajrSoundPath(dest.absolutePath)
            prefs.saveFajrSoundDurationMs(durMs)

            Toast.makeText(this, "Fajr alarm sound saved.", Toast.LENGTH_SHORT).show()
            refreshFajrStatus()

        } catch (e: Exception) {
            Toast.makeText(this, "Could not read file: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            ret.release()
        }
    }

    private fun clearFajrAudio() {
        prefs.saveFajrSoundPath("")
        prefs.saveFajrSoundDurationMs(0L)
        File(filesDir, "alarm_sound_fajr.mp3").delete()
        refreshFajrStatus()
        Toast.makeText(this, "Fajr sound removed.", Toast.LENGTH_SHORT).show()
    }

    private fun refreshFajrStatus() {
        val path   = prefs.getFajrSoundPath()
        val durMs  = prefs.getFajrSoundDurationMs()
        val exists = !path.isNullOrBlank() && File(path).exists()

        if (exists) {
            val min     = durMs / 60_000
            val sec     = (durMs % 60_000) / 1000
            val five    = 5L * 60_000
            binding.tvFajrSoundStatus.text = if (durMs < five) {
                "Fajr sound: ${min}m ${sec}s\n" +
                "${(five - durMs) / 1000}s short of 5 minutes - white noise fills the rest."
            } else {
                "Fajr sound: ${min}m ${sec}s. Full 5-minute coverage confirmed."
            }
            binding.btnClearFajrAudio.isEnabled = true
        } else {
            binding.tvFajrSoundStatus.text =
                "No Fajr sound selected. Main alarm sound will be used for Fajr."
            binding.btnClearFajrAudio.isEnabled = false
        }
    }
}
