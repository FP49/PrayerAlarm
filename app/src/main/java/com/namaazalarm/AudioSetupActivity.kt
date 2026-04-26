package com.namaazalarm

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.namaazalarm.databinding.ActivityAudioSetupBinding
import com.namaazalarm.util.PrefsManager
import java.io.File
import java.io.FileOutputStream

class AudioSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioSetupBinding
    private lateinit var prefs: PrefsManager

    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { handleAudioPicked(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        supportActionBar?.title = "Alarm Sound Settings"

        binding.btnSelectAudio.setOnClickListener { audioPickerLauncher.launch("audio/*") }
        binding.btnClearAudio.setOnClickListener  { clearAudio() }
        binding.btnBack.setOnClickListener        { finish() }

        refreshUI()
    }

    private fun handleAudioPicked(uri: Uri) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val durationMs = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLong() ?: 0L

            val dest = File(filesDir, "alarm_sound.mp3")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }

            prefs.saveAlarmSoundPath(dest.absolutePath)
            prefs.saveAlarmSoundDurationMs(durationMs)

            Toast.makeText(this, "Alarm sound saved.", Toast.LENGTH_SHORT).show()
            refreshUI()

        } catch (e: Exception) {
            Toast.makeText(this, "Could not read audio file: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            retriever.release()
        }
    }

    private fun clearAudio() {
        prefs.saveAlarmSoundPath("")
        prefs.saveAlarmSoundDurationMs(0L)
        File(filesDir, "alarm_sound.mp3").delete()
        refreshUI()
        Toast.makeText(
            this, "Custom sound removed. Alarms will play white noise.", Toast.LENGTH_SHORT
        ).show()
    }

    private fun refreshUI() {
        val path       = prefs.getAlarmSoundPath()
        val durationMs = prefs.getAlarmSoundDurationMs()
        val fileExists = !path.isNullOrBlank() && File(path).exists()

        if (fileExists) {
            val min       = durationMs / 60_000
            val sec       = (durationMs % 60_000) / 1000
            val fiveMins  = 5L * 60 * 1000

            binding.tvAudioStatus.text = if (durationMs < fiveMins) {
                val shortage = (fiveMins - durationMs) / 1000
                "Custom sound loaded: ${min}m ${sec}s\n\n" +
                "This is ${shortage}s short of 5 minutes. " +
                "The app will automatically fill the remaining time with white noise."
            } else {
                "Custom sound loaded: ${min}m ${sec}s\n\nFull 5-minute coverage confirmed."
            }
            binding.btnClearAudio.isEnabled = true
        } else {
            binding.tvAudioStatus.text =
                "No custom sound selected.\n\n" +
                "Alarms will fire at prayer times and play white noise for 5 minutes.\n\n" +
                "Tap 'Select Audio File' to use your own adhan or nasheed."
            binding.btnClearAudio.isEnabled = false
        }
    }
}
