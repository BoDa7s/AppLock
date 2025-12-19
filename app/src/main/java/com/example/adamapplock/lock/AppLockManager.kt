package com.example.adamapplock.lock

import android.content.Context
import com.example.adamapplock.Prefs

object AppLockManager {
    fun shouldLock(context: Context): Boolean {
        val lastUnlock = Prefs.lastAppUnlock(context)
        if (lastUnlock == 0L) return true
        val timer = Prefs.getLockTimerMillis(context)
        val elapsed = System.currentTimeMillis() - lastUnlock
        return timer == Prefs.LOCK_TIMER_IMMEDIATE || elapsed >= timer
    }

    fun markUnlocked(context: Context) {
        Prefs.setAppLastUnlockNow(context)
        Prefs.clearLastBackground(context)
    }

    fun lockNow(context: Context) {
        Prefs.clearAppUnlock(context)
    }
}
