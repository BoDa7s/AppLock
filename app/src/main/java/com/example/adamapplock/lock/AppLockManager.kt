package com.example.adamapplock.lock

import android.content.Context
import com.example.adamapplock.Prefs

object AppLockManager {
    fun shouldLock(context: Context): Boolean {
        val lastUnlock = Prefs.lastAppUnlock(context)
        if (lastUnlock == 0L) return true
        val timer = Prefs.getLockTimerMillis(context)
        if (timer == Prefs.LOCK_TIMER_IMMEDIATE) return true

        val reference = Prefs.lastAppBackground(context).takeIf { it != 0L } ?: lastUnlock
        val elapsed = System.currentTimeMillis() - reference
        return elapsed >= timer
    }

    fun markUnlocked(context: Context) {
        Prefs.setAppLastUnlockNow(context)
        Prefs.clearAppLastBackground(context)
    }

    fun lockNow(context: Context) {
        Prefs.clearAppUnlock(context)
        Prefs.clearAppLastBackground(context)
    }

    fun markBackground(context: Context) {
        Prefs.setAppLastBackgroundNow(context)
    }
}
