package com.awi.lock.protection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.awi.lock.Prefs

/**
 * Best-effort restart when the OS kills the process/service.
 *
 * - Conservative: prevents restart loops.
 * - If user FORCE STOPS the app, Android will block alarms/restarts until user opens the app again.
 */
object ProtectionRestartScheduler {

    private const val TAG = "ProtectionRestartScheduler"

    // Don’t try restarting more frequently than this (avoids loops / Play policy headaches).
    private const val MIN_ATTEMPT_INTERVAL_MS = 30_000L

    // How long to wait before attempting restart.
    private const val RESTART_DELAY_MS = 10_000L

    fun schedule(ctx: Context, reason: String) {
        val now = System.currentTimeMillis()
        val lastAttempt = Prefs.lastServiceRestartAttempt(ctx)

        // Throttle restarts to avoid aggressive loops.
        if (lastAttempt > 0L && (now - lastAttempt) < MIN_ATTEMPT_INTERVAL_MS) {
            Log.w(TAG, "Restart throttled. last=$lastAttempt now=$now reason=$reason")
            return
        }

        // Record attempt timestamp
        Prefs.setLastServiceRestartAttemptNow(ctx)

        val intent = Intent(ctx, AutoStartReceiver::class.java).apply {
            action = AutoStartReceiver.ACTION_RESTART_PROTECTION
            putExtra(AutoStartReceiver.EXTRA_REASON, reason)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val pi = PendingIntent.getBroadcast(ctx, 9001, intent, flags)

        val am = ctx.getSystemService(AlarmManager::class.java) ?: return

        val triggerAt = now + RESTART_DELAY_MS
        Log.i(TAG, "Scheduling restart in ${RESTART_DELAY_MS}ms reason=$reason")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}
