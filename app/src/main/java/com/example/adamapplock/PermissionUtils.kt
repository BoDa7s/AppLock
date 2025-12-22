package com.example.adamapplock

import android.app.AppOpsManager
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri

object PermissionUtils {

    fun hasOverlayPermission(ctx: Context): Boolean =
        Settings.canDrawOverlays(ctx)

    fun hasUsageAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                ctx.applicationInfo.uid,
                ctx.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                ctx.applicationInfo.uid,
                ctx.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * "Battery unrestricted" is messy across OEMs/Android versions.
     * We treat the setting as "looks good" if:
     *  - The app is NOT background-restricted (Android 9+), OR
     *  - The app is explicitly ignoring battery optimizations (Doze exemption)
     *
     * This is advisory only — core protection uses a foreground service.
     */
    fun hasUnrestrictedBattery(ctx: Context): Boolean {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            am?.isBackgroundRestricted == true
        } else {
            false
        }

        val pm = ctx.getSystemService(PowerManager::class.java)
        val ignoringDoze = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        } else {
            true
        }

        // If the OS explicitly says we're background restricted, treat it as not unrestricted.
        // Otherwise, we're probably fine (and Doze exemption counts as a bonus).
        return !backgroundRestricted || ignoringDoze
    }

    /**
     * Opens system settings screens where the user can set Battery to "Unrestricted"
     * or allow background activity. Attempts a direct exemption request first when
     * available, then falls back to relevant settings screens.
     */
    fun openBatterySettings(ctx: Context) {
        val pkgUri = Uri.parse("package:${ctx.packageName}")
        val pm = ctx.packageManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = ctx.getSystemService(PowerManager::class.java)
            val alreadyIgnoring = powerManager?.isIgnoringBatteryOptimizations(ctx.packageName) == true
            val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
            if (!alreadyIgnoring && requestIntent.resolveActivity(pm) != null) {
                requestIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(requestIntent)
                return
            }
        }

        val intents = listOf(
            // App details (Battery settings live under this on most devices)
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri),
            // General battery optimization list
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        val chosen = intents.firstOrNull { it.resolveActivity(pm) != null }
        chosen?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (chosen != null) ctx.startActivity(chosen)
    }
}
