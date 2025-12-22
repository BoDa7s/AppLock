package com.example.adamapplock.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.adamapplock.PermissionUtils
import com.example.adamapplock.Prefs

/**
 * Restarts the foreground protection service after reboot / app update, and
 * also acts as a safe entry point for alarm-based restarts.
 */
class AutoStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: action.orEmpty()
        Log.i(TAG, "AutoStartReceiver action=$action reason=$reason")

        if (!Prefs.isProtectionEnabled(context)) return
        if (!PermissionUtils.hasOverlayPermission(context) || !PermissionUtils.hasUsageAccess(context)) {
            Log.w(TAG, "Not starting protection: core permissions missing")
            return
        }

        // Start or restart the service.
        ProtectionService.start(context)

        // Keep the permission-escort monitor alive if there is an active session.
        com.example.adamapplock.PermissionEscortManager.ensureMonitoring(context)
    }

    companion object {
        private const val TAG = "AutoStartReceiver"
        const val ACTION_RESTART_PROTECTION = "com.example.adamapplock.action.RESTART_PROTECTION"
        const val EXTRA_REASON = "extra_restart_reason"
    }
}
