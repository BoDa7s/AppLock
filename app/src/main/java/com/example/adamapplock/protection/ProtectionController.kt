package com.example.adamapplock.protection

import android.content.Context
import com.example.adamapplock.PermissionUtils
import com.example.adamapplock.Prefs

object ProtectionController {

    fun canStart(ctx: Context): Boolean =
        PermissionUtils.hasOverlayPermission(ctx) && PermissionUtils.hasUsageAccess(ctx)

    fun start(ctx: Context) {
        if (!canStart(ctx)) return
        Prefs.setProtectionEnabled(ctx, true)
        ProtectionService.start(ctx)
    }

    fun stop(ctx: Context) {
        Prefs.setProtectionEnabled(ctx, false)
        ProtectionService.stop(ctx)
    }
}
