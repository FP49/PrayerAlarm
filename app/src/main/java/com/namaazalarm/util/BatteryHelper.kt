package com.namaazalarm.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the standard Android battery optimization exemption screen */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        }
    }

    /**
     * Opens Samsung's "Never sleeping apps" screen directly.
     * Tries three increasingly generic fallbacks if the specific screen is unavailable.
     */
    fun openNeverSleepingApps(context: Context) {
        // Attempt 1: Samsung Device Care > Battery > Never sleeping apps (direct)
        val samsungDirect = Intent().apply {
            setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
            putExtra("extra_show_never_sleeping", true)
        }
        // Attempt 2: Samsung Device Care battery main screen
        val samsungBattery = Intent().apply {
            setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
        }
        // Attempt 3: Generic battery saver settings
        val genericBattery = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        // Attempt 4: App's own battery detail page
        val appBatteryDetail = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

        listOf(samsungDirect, samsungBattery, genericBattery, appBatteryDetail).forEach { intent ->
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next fallback
            }
        }
    }
}
