package com.example.adamapplock.protection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.adamapplock.PermissionUtils
import com.example.adamapplock.Prefs
import com.example.adamapplock.R
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ProtectionController {

    private const val TAG = "ProtectionController"

    fun canStart(ctx: Context): Boolean =
        PermissionUtils.hasOverlayPermission(ctx) &&
            PermissionUtils.hasUsageAccess(ctx)

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

    fun postBatteryRestrictedNotification(ctx: Context) {
        val channelId = ensureBatteryChannel(ctx)
        val builder = NotificationCompat.Builder(ctx, channelId)
            .setContentTitle(ctx.getString(R.string.battery_restricted_title))
            .setContentText(ctx.getString(R.string.battery_restricted_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID_BATTERY, builder.build())
    }

    private fun ensureBatteryChannel(ctx: Context): String {
        val id = "battery_protection_guardrails"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return id
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        val existing = mgr?.getNotificationChannel(id)
        val name = ctx.getString(R.string.protection_notification_channel)
        val description = ctx.getString(R.string.protection_notification_channel_description)
        if (existing != null) {
            if (existing.name != name || existing.description != description) {
                existing.name = name
                existing.description = description
                mgr?.createNotificationChannel(existing)
            }
            return id
        }
        val channel = NotificationChannel(
            id,
            name,
            NotificationManager.IMPORTANCE_HIGH
        ).apply { this.description = description }
        mgr?.createNotificationChannel(channel)
        return id
    }

    private const val NOTIFICATION_ID_BATTERY = 5011
}
