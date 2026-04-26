package com.namaazalarm.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Custom circular countdown view.
 * Draws a gold arc on a dark ring.
 * Arc shrinks as time to next prayer reduces.
 * Displays prayer name and countdown time in the centre.
 */
class PrayerCountdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // dp to px helper
    private val dp: Float = context.resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style      = Paint.Style.STROKE
        strokeWidth = 14f * dp
        color      = Color.parseColor("#1E2A3B")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 14f * dp
        strokeCap   = Paint.Cap.ROUND
        color       = Color.parseColor("#C9A84C")
    }

    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#C9A84C")
        textAlign = Paint.Align.CENTER
        textSize  = 18f * dp
        typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize  = 22f * dp
        typeface  = Typeface.MONOSPACE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
        textSize  = 11f * dp
    }

    var progress: Float   = 1f      // 1.0 = full ring, 0.0 = empty
    var prayerName: String = "--"
    var countdownText: String = "--:--"

    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx     = width  / 2f
        val cy     = height / 2f
        val radius = minOf(cx, cy) - trackPaint.strokeWidth / 2f

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Background track
        canvas.drawArc(oval, -90f, 360f, false, trackPaint)

        // Progress arc (shrinks as countdown reduces)
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0f) canvas.drawArc(oval, -90f, sweep, false, progressPaint)

        // Prayer name
        canvas.drawText(prayerName, cx, cy - 8f * dp, namePaint)

        // Countdown time
        canvas.drawText(countdownText, cx, cy + 20f * dp, timePaint)

        // "until" label
        canvas.drawText("until", cx, cy + 36f * dp, labelPaint)
    }

    fun update(progress: Float, prayerName: String, countdown: String) {
        this.progress     = progress
        this.prayerName   = prayerName
        this.countdownText = countdown
        invalidate()
    }
}
