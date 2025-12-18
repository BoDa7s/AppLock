package com.example.adamapplock.protection

import android.content.Context
import android.util.Log
import com.example.adamapplock.PermissionUtils
import com.example.adamapplock.Prefs

object ProtectionController {

    private const val TAG = "ProtectionController"

    fun canStart(ctx: Context): Boolean =
        PermissionUtils.hasOverlayPermission(ctx) && PermissionUtils.hasUsageAccess(ctx)

    fun start(ctx: Context) {
        if (!canStart(ctx)) return
        Log.i(TAG, "Request to start protection service")
        Prefs.setProtectionEnabled(ctx, true)
        ProtectionService.start(ctx)
    }

    fun stop(ctx: Context) {
        Log.i(TAG, "Request to stop protection service")
        Prefs.setProtectionEnabled(ctx, false)
        ProtectionService.stop(ctx)
    }
}
