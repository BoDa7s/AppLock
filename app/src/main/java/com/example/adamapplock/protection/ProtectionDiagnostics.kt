package com.example.adamapplock.protection

import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class HealthCheckResult(
    val overlayGranted: Boolean,
    val usageGranted: Boolean,
    val serviceRunning: Boolean,
    val overlayTestPassed: Boolean,
    val batteryUnrestricted: Boolean,
    val timestampMillis: Long,
    val notes: String
) {
    val ready: Boolean get() = overlayGranted && usageGranted && batteryUnrestricted
}

object ProtectionDiagnostics {

    private const val TAG = "ProtectionDiagnostics"

    fun isServiceRunning(context: Context): Boolean {
        val mgr = context.getSystemService(ActivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return mgr.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == ProtectionService::class.java.name }
    }

    suspend fun runHealthCheck(context: Context): HealthCheckResult {
        val permissions = PermissionSnapshot.capture(context)
        val serviceRunning = isServiceRunning(context)
        val overlayTestPassed = if (permissions.overlayGranted) {
            probeOverlayWindow(context)
        } else {
            false
        }

        val noteBuilder = buildString {
            append("overlayGranted=").append(permissions.overlayGranted)
            append(", usageGranted=").append(permissions.usageGranted)
            append(", serviceRunning=").append(serviceRunning)
            append(", overlayTestPassed=").append(overlayTestPassed)
            append(", batteryUnrestricted=").append(permissions.batteryUnrestricted)
        }

        return HealthCheckResult(
            overlayGranted = permissions.overlayGranted,
            usageGranted = permissions.usageGranted,
            serviceRunning = serviceRunning,
            overlayTestPassed = overlayTestPassed,
            batteryUnrestricted = permissions.batteryUnrestricted,
            timestampMillis = System.currentTimeMillis(),
            notes = noteBuilder
        )
    }

    private suspend fun probeOverlayWindow(context: Context): Boolean {
        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val view = View(appContext)

        if (windowManager == null) {
            Log.w(TAG, "WindowManager null during overlay probe")
            return false
        }

        val params = WindowManager.LayoutParams(
            1,
            1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        return withContext(Dispatchers.Main.immediate) {
            val added = runCatching { windowManager.addView(view, params) }
                .onFailure { Log.e(TAG, "Overlay probe add failed", it) }
                .isSuccess

            if (!added) return@withContext false

            delay(50)

            runCatching { windowManager.removeViewImmediate(view) }
                .onFailure { Log.e(TAG, "Overlay probe remove failed", it) }

            true
        }
    }
}
