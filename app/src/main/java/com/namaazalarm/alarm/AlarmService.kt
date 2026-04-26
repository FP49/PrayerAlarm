package com.namaazalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.namaazalarm.AlarmFullScreenActivity
import com.namaazalarm.MainActivity
import com.namaazalarm.R
import com.namaazalarm.util.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer?  = null
    private var alarmJob: Job?             = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusGranted          = false

    private val audioManager get() =
        getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val CHANNEL_ID         = "namaaz_alarm_ch"
    private val NOTIF_ID           = 101
    private val TARGET_DURATION_MS = 5L * 60 * 1000   // 5 minutes

    companion object {
        const val ACTION_STOP = "com.namaazalarm.STOP_ALARM"
    }

    override fun onCreate() {
        super.onCreate()
        createAlarmChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME) ?: "Prayer"
        val day        = intent?.getIntExtra(AlarmScheduler.EXTRA_DAY,   0) ?: 0
        val month      = intent?.getIntExtra(AlarmScheduler.EXTRA_MONTH, 0) ?: 0
        val year       = intent?.getIntExtra(AlarmScheduler.EXTRA_YEAR,  0) ?: 0

        if (day > 0 && month > 0 && year > 0) {
            PrefsManager(this).markAlarmFired(year, month, day, prayerName)
        }

        startForeground(NOTIF_ID, buildNotification(prayerName))

        alarmJob = CoroutineScope(Dispatchers.Main).launch {
            runAlarmSequence(prayerName)
        }

        return START_NOT_STICKY
    }

    // ─────────────────────────────────────────────────────────────
    // Alarm sequence
    // ─────────────────────────────────────────────────────────────

    private suspend fun runAlarmSequence(prayerName: String) {

        // Do not interrupt active phone calls
        if (isCallActive()) {
            stopEverything()
            return
        }

        // Request audio focus before playing anything
        audioFocusGranted = requestAudioFocus()
        if (!audioFocusGranted) {
            // Another app holds permanent focus (e.g. GPS nav) — still show notification
            // but do not force audio
            delay(TARGET_DURATION_MS)
            stopEverything()
            return
        }

        val prefs      = PrefsManager(this)
        val isFajr     = prayerName.uppercase() == "FAJR"
        val useFajrSep = prefs.isFajrSeparateEnabled()

        val soundPath: String? = when {
            isFajr && useFajrSep -> prefs.getFajrSoundPath()
            else                 -> prefs.getAlarmSoundPath()
        }

        val startMs = System.currentTimeMillis()

        // Play custom sound if provided
        if (!soundPath.isNullOrBlank() && File(soundPath).exists()) {
            try {
                val soundDurationMs = getAudioDurationMs(soundPath)
                val playDuration    = soundDurationMs.coerceAtMost(TARGET_DURATION_MS)

                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(alarmAudioAttributes())
                    setDataSource(soundPath)
                    prepare()
                    start()
                }

                delay(playDuration)

                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null

            } catch (e: Exception) {
                // Sound file failed — fall through to silent wait
            }
        }

        // Fill remaining time with SILENCE — not white noise.
        // White noise was causing radio-static sound and overriding other audio.
        // A plain delay keeps the alarm active for the full 5 minutes
        // without producing any unwanted sound.
        val elapsed   = System.currentTimeMillis() - startMs
        val remaining = TARGET_DURATION_MS - elapsed
        if (remaining > 500L) {
            delay(remaining)
        }

        stopEverything()
    }

    // ─────────────────────────────────────────────────────────────
    // Audio focus
    // ─────────────────────────────────────────────────────────────

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(alarmAudioAttributes())
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()
                .also { audioFocusRequest = it }
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (!audioFocusGranted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioFocusGranted = false
    }

    // ─────────────────────────────────────────────────────────────
    // Call state check
    // ─────────────────────────────────────────────────────────────

    private fun isCallActive(): Boolean {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.callState != TelephonyManager.CALL_STATE_IDLE
        } catch (e: Exception) {
            false   // Cannot determine — proceed with alarm
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun alarmAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private fun getAudioDurationMs(path: String): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (e: Exception) { 0L } finally { r.release() }
    }

    private fun stopEverything() {
        alarmJob?.cancel()
        mediaPlayer?.runCatching { stop(); release() }
        mediaPlayer = null
        abandonAudioFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    // ─────────────────────────────────────────────────────────────
    // Notification — stays until Stop is tapped
    // ─────────────────────────────────────────────────────────────

    private fun buildNotification(prayerName: String): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, AlarmService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = Intent(this, AlarmFullScreenActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, prayerName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, 2, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$prayerName Time")
            .setContentText("Tap Stop to dismiss the alarm")
            .setSmallIcon(R.drawable.ic_mosque)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(R.drawable.ic_stop, "Stop", stopPi)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createAlarmChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Prayer Alarms", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prayer time alarms"
                setSound(null, null)
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { stopEverything(); super.onDestroy() }
}
