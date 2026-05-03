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
     * Opens Samsung Never Auto Sleeping Apps screen.
     * Tries multiple known deep-link variants across One UI versions.
     * Samsung changed the Activity class name across One UI versions so
     * we try each known variant in order, falling back to generic settings.
     *
     * One UI 3.x / 4.x: com.samsung.android.sm.battery.ui.BatteryActivity
     * One UI 5.x / 6.x: com.samsung.android.sm.battery.ui.usage.SleepingAppsActivity
     * One UI fallback:   com.samsung.android.lool/.settings.SABatteryActivity
     */
    fun openNeverSleepingApps(context: Context) {
        val attempts = listOf(
            // One UI 5 / 6 direct Never Auto Sleeping screen
            Intent().apply {
                setClassName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.usage.SleepingAppsActivity"
                )
            },
            // One UI 3 / 4 battery main
            Intent().apply {
                setClassName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            },
            // Alternative Samsung package
            Intent().apply {
                setClassName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            },
            // Generic Android battery saver settings
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            // Last resort: app detail page where user can manage battery manually
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )

        for (intent in attempts) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next fallback
            }
        }
    }
}
