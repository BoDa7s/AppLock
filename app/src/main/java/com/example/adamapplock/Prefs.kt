package com.example.adamapplock

import android.content.Context
import androidx.core.content.edit
import com.example.adamapplock.ui.theme.ThemeMode


object Prefs {



    // Single shared prefs file for everything
    private const val FILE = "lock_prefs"

    // Keys
    private const val KEY_PASSCODE          = "passcode"
    private const val KEY_LOCKED            = "locked_apps"
    private const val KEY_USE_BIOMETRIC     = "use_biometric"
    private const val KEY_LAST_UNLOCK       = "last_unlock"
    private const val KEY_SESSION_UNLOCKED  = "session_unlocked_pkg"
    private const val KEY_SESSION_UID       = "session_unlocked_uid"
    private const val KEY_LOCK_TIMER_MS     = "lock_timer_ms"

    private const val KEY_THEME_MODE = "theme_mode"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)

    fun getThemeMode(ctx: Context): ThemeMode =
        when (prefs(ctx).getString(KEY_THEME_MODE, "system")) {
            "light" -> ThemeMode.LIGHT
            "dark"  -> ThemeMode.DARK
            else    -> ThemeMode.SYSTEM
        }

    fun setThemeMode(ctx: Context, mode: ThemeMode) {
        val value = when (mode) {
            ThemeMode.SYSTEM -> "system"
            ThemeMode.LIGHT  -> "light"
            ThemeMode.DARK   -> "dark"
        }
        prefs(ctx).edit().putString(KEY_THEME_MODE, value).apply()
    }

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // -------- Passcode --------
    fun getPasscode(ctx: Context): String =
        sp(ctx).getString(KEY_PASSCODE, "") ?: ""

    fun setPasscode(ctx: Context, value: String) {
        sp(ctx).edit { putString(KEY_PASSCODE, value) }
    }

    // -------- Biometric toggle --------
    fun useBiometric(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_USE_BIOMETRIC, false)

    fun setUseBiometric(ctx: Context, enabled: Boolean) {
        sp(ctx).edit { putBoolean(KEY_USE_BIOMETRIC, enabled) }
    }

    // -------- Locked apps --------
    fun getLockedApps(ctx: Context): MutableSet<String> =
        sp(ctx).getStringSet(KEY_LOCKED, emptySet())?.toMutableSet() ?: mutableSetOf()

    fun saveLockedApps(ctx: Context, set: Set<String>) {
        // store a copy to avoid sharing the mutable ref
        sp(ctx).edit { putStringSet(KEY_LOCKED, set.toMutableSet()) }
    }

    fun toggleLocked(ctx: Context, pkg: String) {
        val set = getLockedApps(ctx)
        if (!set.add(pkg)) set.remove(pkg)
        saveLockedApps(ctx, set)
    }

    fun isAppLocked(ctx: Context, pkg: String): Boolean =
        sp(ctx).getStringSet(KEY_LOCKED, emptySet())?.contains(pkg) == true

    // -------- Last unlock (optional analytics/timer) --------
    fun setLastUnlockNow(ctx: Context) {
        sp(ctx).edit { putLong(KEY_LAST_UNLOCK, System.currentTimeMillis()) }
    }

    fun lastUnlock(ctx: Context): Long =
        sp(ctx).getLong(KEY_LAST_UNLOCK, 0L)

    // -------- Foreground session (package + UID) --------
    // Store: current unlocked app package and its UID.
    // When pkg == null we clear the session.
    fun setSessionUnlocked(ctx: Context, pkg: String?, uid: Int? = null) {
        sp(ctx).edit {
            if (pkg == null) {
                remove(KEY_SESSION_UNLOCKED)
                remove(KEY_SESSION_UID)
            } else {
                putString(KEY_SESSION_UNLOCKED, pkg)
                if (uid != null) {
                    putInt(KEY_SESSION_UID, uid)
                } else {
                    remove(KEY_SESSION_UID)
                }
            }
        }
    }

    fun getSessionUnlocked(ctx: Context): String? =
        sp(ctx).getString(KEY_SESSION_UNLOCKED, null)

    fun getSessionUid(ctx: Context): Int? =
        sp(ctx).let { p ->
            if (p.contains(KEY_SESSION_UID)) {
                p.getInt(KEY_SESSION_UID, -1).takeIf { it != -1 }
            } else null
        }

    // -------- Session timer --------
    const val LOCK_TIMER_IMMEDIATE: Long = 0L

    fun getLockTimerMillis(ctx: Context): Long =
        sp(ctx).getLong(KEY_LOCK_TIMER_MS, LOCK_TIMER_IMMEDIATE)

    fun setLockTimerMillis(ctx: Context, durationMillis: Long) {
        sp(ctx).edit { putLong(KEY_LOCK_TIMER_MS, durationMillis) }
    }


}
