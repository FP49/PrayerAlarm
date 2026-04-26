package com.namaazalarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.namaazalarm.alarm.AlarmScheduler
import com.namaazalarm.alarm.AlarmService
import com.namaazalarm.databinding.ActivityAlarmFullscreenBinding
import com.namaazalarm.util.PrefsManager
import com.namaazalarm.util.TimeFormatter
import java.util.Calendar

/**
 * Full-screen alarm activity shown OVER the lock screen.
 *
 * FIX v6: Removed requestDismissKeyguard() — this was the cause of azaan
 * cutting off at ~3 seconds. When keyguard dismissal is requested, Android
 * interrupts audio focus to show the password prompt.
 *
 * The activity now shows over the lock screen without dismissing it.
 * The user taps Stop to dismiss the alarm. The lock screen remains
 * and the user unlocks normally afterwards — exactly as Samsung Clock works.
 */
class AlarmFullScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmFullscreenBinding
    private val clockHandler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on WITHOUT dismissing keyguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // requestDismissKeyguard intentionally removed — it caused audio cutoff
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                // FLAG_DISMISS_KEYGUARD intentionally omitted
            )
        }

        binding = ActivityAlarmFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prayerName = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME) ?: "Prayer"
        binding.tvPrayerName.text = prayerName
        binding.tvSubtitle.text   = "It is time for $prayerName prayer"

        startClock()

        binding.btnStop.setOnClickListener {
            stopClock()
            stopAlarmService()
            finish()
        }
    }

    private fun startClock() {
        val prefs   = PrefsManager(this)
        val use24   = prefs.is24HourMode()
        clockRunnable = object : Runnable {
            override fun run() {
                val cal = Calendar.getInstance()
                binding.tvClock.text = TimeFormatter.format(
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    use24
                )
                clockHandler.postDelayed(this, 1000)
            }
        }
        clockHandler.post(clockRunnable!!)
    }

    private fun stopClock() {
        clockRunnable?.let { clockHandler.removeCallbacks(it) }
        clockRunnable = null
    }

    private fun stopAlarmService() {
        startService(Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        })
    }

    override fun onDestroy() {
        stopClock()
        // Do NOT call stopAlarmService here — only stop on explicit button tap.
        // If system destroys this activity for any other reason the alarm
        // continues playing as intended.
        super.onDestroy()
    }
}
