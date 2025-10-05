package com.example.adamapplock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

// In-process debounce to avoid double launching the overlay on event bursts
private var lastPkgLaunched: String? = null
private var lastLaunchTs: Long = 0L

class AppLockAccessibilityService : AccessibilityService() {

    private val screenOffReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                // Screen off clears the session when configured to lock immediately, otherwise records the background time
                val timer = Prefs.getLockTimerMillis(this@AppLockAccessibilityService)
                val lockOnScreenOff = Prefs.lockOnScreenOff(this@AppLockAccessibilityService)
                if (lockOnScreenOff || timer == Prefs.LOCK_TIMER_IMMEDIATE) {
                    clearUnlockedSession()
                } else if (Prefs.getSessionUnlocked(this@AppLockAccessibilityService) != null) {
                    Prefs.setLastBackgroundNow(this@AppLockAccessibilityService)
                    SessionTimeoutScheduler.ensureInitialized(applicationContext)
                    SessionTimeoutScheduler.schedule()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        SessionTimeoutScheduler.ensureInitialized(applicationContext)
        SessionTimeoutScheduler.schedule()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        SessionTimeoutScheduler.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Ignore events emitted by our own app
        if (pkg == packageName) return

        val sessionPkg = Prefs.getSessionUnlocked(this)
        val sessionUid = Prefs.getSessionUid(this)
        val timerMillis = Prefs.getLockTimerMillis(this)
        val now = System.currentTimeMillis()
        val lastBackground = Prefs.lastBackground(this)
        val sessionActive = when {
            sessionPkg == null -> false
            timerMillis == Prefs.LOCK_TIMER_IMMEDIATE -> true
            lastBackground == 0L -> true
            else -> (now - lastBackground) <= timerMillis
        }

        if (sessionPkg != null && !sessionActive) {
            clearUnlockedSession()
        }

        val activeSessionPkg = if (sessionActive) sessionPkg else null
        val activeSessionUid = if (sessionActive) sessionUid else null

        val isRealApp = hasLaunchIntent(pkg)
        val isLocked  = Prefs.isAppLocked(this, pkg)
        val newUid    = runCatching { packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull()

        // ---- 1) While in an unlocked session:
        // Allow: same package, non-launcher children (dialogs/tools), or same-UID siblings (e.g., Gallery editor)
        if (activeSessionPkg != null) {
            val samePkg = (pkg == activeSessionPkg)
            val sameUid = (activeSessionUid != null && newUid != null && activeSessionUid == newUid)
            if (samePkg) {
                Prefs.clearLastBackground(this)
                SessionTimeoutScheduler.cancel()
                return // stay unlocked within this app
            }
            if (sameUid) {
                return // stay unlocked within this app's shared UID surfaces
            }

            if (timerMillis > Prefs.LOCK_TIMER_IMMEDIATE) {
                Prefs.setLastBackgroundNow(this)
                SessionTimeoutScheduler.ensureInitialized(applicationContext)
                SessionTimeoutScheduler.schedule()
            } else {
                clearUnlockedSession()
            }

            if (!isRealApp) {
                return // treat home/recents surfaces as background without locking them
            }
        }

        // ---- 2) Not in a session now: decide whether to lock this package
        if (isLocked) {
            // Debounce duplicate window changes for the same package
            val now = SystemClock.uptimeMillis()
            if (lastPkgLaunched == pkg && (now - lastLaunchTs) < 800) return
            lastPkgLaunched = pkg
            lastLaunchTs = now

            // Start the overlay (no CLEAR_TOP so finishing reveals the target app underneath)
            val myPkg = applicationContext.packageName
            if (pkg == myPkg) return
            if (event.className == LockOverlayActivity::class.java.name) return

            // Launch overlay in its own empty task
            val i = Intent(this, LockOverlayActivity::class.java).apply {
                putExtra(EXTRA_LOCKED_PKG, pkg)  // use ONE constant everywhere
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK   // 👈 overlay becomes the only activity in its task
                )
            }
            startActivity(i)
        } else {
            // Visiting an unlocked "real" app clears stale sessions when no active unlock window remains
            if (isRealApp && activeSessionPkg == null) clearUnlockedSession()
        }
    }

    override fun onInterrupt() {
        // no-op
    }

    private fun clearUnlockedSession() {
        Prefs.setSessionUnlocked(this, null, null)
        Prefs.clearLastBackground(this)
        SessionTimeoutScheduler.ensureInitialized(applicationContext)
        SessionTimeoutScheduler.cancel()
    }

    private fun hasLaunchIntent(packageName: String): Boolean {
        return packageManager.getLaunchIntentForPackage(packageName) != null
    }
}
