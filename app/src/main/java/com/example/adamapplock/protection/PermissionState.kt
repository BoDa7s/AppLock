package com.example.adamapplock.protection

import android.content.Context
import com.example.adamapplock.PermissionUtils

data class PermissionSnapshot(
    val overlayGranted: Boolean,
    val usageGranted: Boolean
) {
    val ready: Boolean
        get() = overlayGranted && usageGranted

    val missingOverlayOnly: Boolean
        get() = !overlayGranted && usageGranted

    val missingUsageOnly: Boolean
        get() = overlayGranted && !usageGranted

    val missingBoth: Boolean
        get() = !overlayGranted && !usageGranted

    companion object {
        fun capture(context: Context): PermissionSnapshot = PermissionSnapshot(
            overlayGranted = PermissionUtils.hasOverlayPermission(context),
            usageGranted = PermissionUtils.hasUsageAccess(context)
        )
    }
}
