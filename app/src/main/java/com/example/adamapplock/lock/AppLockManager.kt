package com.example.adamapplock.lock

import android.content.Context
import com.example.adamapplock.Prefs

object AppLockManager {
    fun shouldLock(context: Context): Boolean {
        return !Prefs.isAppSessionUnlocked(context)
    }

    fun markUnlocked(context: Context) {
        Prefs.setAppLastUnlockNow(context)
        Prefs.setAppSessionUnlocked(context, true)
        Prefs.clearAppLastBackground(context)
    }

    fun lockNow(context: Context) {
        Prefs.setAppSessionUnlocked(context, false)
        Prefs.clearAppUnlock(context)
        Prefs.clearAppLastBackground(context)
    }

    fun markBackground(context: Context) {
        Prefs.setAppSessionUnlocked(context, false)
        Prefs.setAppLastBackgroundNow(context)
    }
}
