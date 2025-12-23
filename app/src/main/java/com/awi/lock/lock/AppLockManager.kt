package com.awi.lock.lock

import android.content.Context
import com.awi.lock.Prefs
import java.util.UUID

object AppLockManager {

    private val processSessionToken: String = UUID.randomUUID().toString()

    fun shouldLock(context: Context): Boolean {
        val unlocked = Prefs.isAppSessionUnlocked(context)
        val sessionToken = Prefs.getAppSessionToken(context)
        return !unlocked || sessionToken != processSessionToken
    }

    fun markUnlocked(context: Context) {
        Prefs.setAppLastUnlockNow(context)
        Prefs.setAppSessionUnlocked(context, true)
        Prefs.setAppSessionToken(context, processSessionToken)
        Prefs.clearAppLastBackground(context)
    }

    fun lockNow(context: Context) {
        Prefs.setAppSessionUnlocked(context, false)
        Prefs.setAppSessionToken(context, null)
        Prefs.clearAppUnlock(context)
        Prefs.clearAppLastBackground(context)
    }

    fun markBackground(context: Context) {
        Prefs.setAppLastBackgroundNow(context)
    }
}
