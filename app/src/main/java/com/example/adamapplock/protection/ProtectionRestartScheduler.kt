package com.example.adamapplock.protection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.adamapplock.Prefs

/**
 * Best-effort restart when the OS kills the process/service.
 *
 * This is intentionally conservative to avoid aggressive restart loops.
 * If the user force-stops the app, Android will block all restarts until
 * the user manually opens the app again.
 */
object ProtectionRestartScheduler {

    private const val TAG = "ProtectionRestart"

    fun schedule(context: Context, reason: String) {
        val ctx = context.applicationContext
        if (!Prefs.isProtectionEnabled(ctx)) return

        val now = System.currentTimeMillis()
        val last = Prefs.lastServiceRestartAttempt(ctx)
        val delayMs = if (now - last < 15_000) 60_000L else 5_000L
        Prefs.setLastServiceRestartAttemptNow(ctx)

        val intent = Intent(ctx, AutoStartReceiver::class.java).apply {
            action = AutoStartReceiver.ACTION_RESTART_PROTECTION
            putExtra(AutoStartReceiver.EXTRA_REASON, reason)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getBroadcast(ctx, 9001, intent, flags)

        val am = ctx.getSystemService(AlarmManager::class.java)
        if (am == null) return

        val triggerAt = now + delayMs
        Log.i(TAG, "Scheduling restart in ${delayMs}ms reason=$reason")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}
