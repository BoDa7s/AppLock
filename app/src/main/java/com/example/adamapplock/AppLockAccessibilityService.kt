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
                // Screen off ends any unlocked session when the timer is immediate
                if (Prefs.getLockTimerMillis(this@AppLockAccessibilityService) == Prefs.LOCK_TIMER_IMMEDIATE) {
                    Prefs.setSessionUnlocked(this@AppLockAccessibilityService, null, null)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Ignore our own app and obvious system packages
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return

        val sessionPkg = Prefs.getSessionUnlocked(this)
        val sessionUid = Prefs.getSessionUid(this)
        val timerMillis = Prefs.getLockTimerMillis(this)
        val now = System.currentTimeMillis()
        val lastUnlock = Prefs.lastUnlock(this)
        val hasWindow = timerMillis != Prefs.LOCK_TIMER_IMMEDIATE
        val withinWindow = sessionPkg != null && (!hasWindow || (lastUnlock != 0L && (now - lastUnlock) <= timerMillis))

        if (sessionPkg != null && !withinWindow) {
            Prefs.setSessionUnlocked(this, null, null)
        }

        val activeSessionPkg = if (withinWindow) sessionPkg else null
        val activeSessionUid = if (withinWindow) sessionUid else null

        val isRealApp = hasLaunchIntent(pkg)
        val isLocked  = Prefs.isAppLocked(this, pkg)
        val newUid    = runCatching { packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull()

        // ---- 1) While in an unlocked session:
        // Allow: same package, non-launcher children (dialogs/tools), or same-UID siblings (e.g., Gallery editor)
        if (activeSessionPkg != null) {
            val samePkg = (pkg == activeSessionPkg)
            val sameUid = (activeSessionUid != null && newUid != null && activeSessionUid == newUid)
            if (samePkg || !isRealApp || sameUid) {
                return // stay unlocked within this app+its utilities
            } else {
                // Immediate timer option: leaving the app clears the session right away
                if (timerMillis == Prefs.LOCK_TIMER_IMMEDIATE && isRealApp) {
                    Prefs.setSessionUnlocked(this, null, null)
                }
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
            // Visiting an unlocked "real" app ends any stale session
            if (isRealApp) Prefs.setSessionUnlocked(this, null, null)
        }
    }

    override fun onInterrupt() {
        // no-op
    }

    private fun hasLaunchIntent(packageName: String): Boolean {
        return packageManager.getLaunchIntentForPackage(packageName) != null
    }
}