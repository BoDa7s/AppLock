package com.example.adamapplock

import android.content.Context
import android.os.Handler
import android.os.Looper

object SessionTimeoutScheduler {

    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        appContext?.let { evaluateTimeout(it) }
    }

    @Volatile
    private var appContext: Context? = null

    fun ensureInitialized(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun schedule() {
        val ctx = appContext ?: return
        evaluateTimeout(ctx)
    }

    fun cancel() {
        handler.removeCallbacks(timeoutRunnable)
    }

    private fun evaluateTimeout(ctx: Context) {
        handler.removeCallbacks(timeoutRunnable)

        val sessionPkg = Prefs.getSessionUnlocked(ctx) ?: run {
            Prefs.clearLastBackground(ctx)
            return
        }

        val timerMillis = Prefs.getLockTimerMillis(ctx)
        if (timerMillis == Prefs.LOCK_TIMER_IMMEDIATE) {
            Prefs.setSessionUnlocked(ctx, null, null)
            Prefs.clearLastBackground(ctx)
            return
        }

        val lastBackground = Prefs.lastBackground(ctx)
        if (lastBackground == 0L) {
            return
        }

        val elapsed = System.currentTimeMillis() - lastBackground
        if (elapsed >= timerMillis) {
            Prefs.setSessionUnlocked(ctx, null, null)
            Prefs.clearLastBackground(ctx)
        } else {
            handler.postDelayed(timeoutRunnable, timerMillis - elapsed)
        }
    }
}
