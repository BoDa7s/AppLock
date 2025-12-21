package com.example.adamapplock

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.adamapplock.PermissionEscortManager.PermissionEscortType.Battery
import com.example.adamapplock.PermissionEscortManager.PermissionEscortType.Overlay
import com.example.adamapplock.PermissionEscortManager.PermissionEscortType.Usage
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds

object PermissionEscortManager {

    enum class PermissionEscortType { Overlay, Usage, Battery }

    data class PermissionEscortSession(
        val type: PermissionEscortType,
        val startedAt: Long,
        val expectingGrant: Boolean
    )

    const val EXTRA_FROM_PERMISSION_ESCORT = "extra_from_permission_escort"
    const val EXTRA_PERMISSION_ESCORT_TYPE = "extra_permission_escort_type"

    private const val ESCORT_NOTIFICATION_ID = 2002
    private const val MONITOR_NOTIFICATION_ID = 2003
    private val monitorInterval = 400.milliseconds
    private val timeout = 2.minutes

    private var handlingGrant = false

    fun beginSession(context: Context, type: PermissionEscortType) {
        Prefs.setPermissionEscortSession(
            ctx = context,
            type = type.name,
            startedAt = System.currentTimeMillis(),
            expectingGrant = true
        )
        PermissionEscortService.start(context)
    }

    fun stopSession(context: Context) {
        Prefs.setPermissionEscortSession(context, null, null, false)
        PermissionEscortService.stop(context)
    }

    fun activeSession(context: Context): PermissionEscortSession? {
        val stored = Prefs.getPermissionEscortSession(context) ?: return null
        val type = runCatching { PermissionEscortType.valueOf(stored.first) }.getOrNull()
            ?: return null
        return PermissionEscortSession(
            type = type,
            startedAt = stored.second,
            expectingGrant = stored.third
        )
    }

    fun onAppResumed(context: Context) {
        val session = activeSession(context) ?: return
        if (isPermissionGranted(context, session.type)) {
            handleGrant(context, session, fromResume = true)
        } else if (!session.expectingGrant) {
            stopSession(context)
        }
    }

    fun ensureMonitoring(context: Context) {
        if (activeSession(context) != null) {
            PermissionEscortService.start(context)
        }
    }

    fun handleMonitorTick(context: Context) {
        val session = activeSession(context) ?: return
        val elapsed = System.currentTimeMillis() - session.startedAt
        if (elapsed >= timeout.inWholeMilliseconds) {
            postTimeoutNotification(context, session.type)
            stopSession(context)
            return
        }
        if (isPermissionGranted(context, session.type)) {
            handleGrant(context, session, fromResume = false)
        }
    }

    private fun handleGrant(
        context: Context,
        session: PermissionEscortSession,
        fromResume: Boolean
    ) {
        if (handlingGrant) return
        handlingGrant = true
        try {
            stopSession(context)
            bringAppToFront(context, session.type)
            if (!isAppInForeground(context)) {
                postReturnNotification(context, session.type)
            }
        } finally {
            handlingGrant = false
        }
    }

    private fun bringAppToFront(context: Context, type: PermissionEscortType) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_FROM_PERMISSION_ESCORT, true)
            putExtra(EXTRA_PERMISSION_ESCORT_TYPE, type.name)
        }
        ContextCompat.startActivity(context, intent, null)
    }

    private fun isPermissionGranted(context: Context, type: PermissionEscortType): Boolean =
        when (type) {
            Overlay -> PermissionUtils.hasOverlayPermission(context)
            Usage -> PermissionUtils.hasUsageAccess(context)
            Battery -> PermissionUtils.hasUnrestrictedBattery(context)
        }

    private fun postReturnNotification(context: Context, type: PermissionEscortType) {
        val channelId = NotificationHelper.ensureEscortChannel(context)
        val intent = buildReturnIntent(context, type)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.permission_escort_granted_title))
            .setContentText(context.getString(R.string.permission_escort_granted_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ESCORT_NOTIFICATION_ID, notification)
    }

    private fun postTimeoutNotification(context: Context, type: PermissionEscortType) {
        val channelId = NotificationHelper.ensureEscortChannel(context)
        val intent = buildReturnIntent(context, type)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.permission_escort_timeout_title))
            .setContentText(context.getString(R.string.permission_escort_timeout_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ESCORT_NOTIFICATION_ID, notification)
    }

    fun buildMonitorNotification(context: Context): android.app.Notification {
        val channelId = NotificationHelper.ensureEscortChannel(context)
        val intent = buildReturnIntent(context, activeSession(context)?.type ?: Overlay)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.permission_escort_monitor_title))
            .setContentText(context.getString(R.string.permission_escort_monitor_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun monitorIntervalMs(): Long = monitorInterval.inWholeMilliseconds

    fun monitorNotificationId(): Int = MONITOR_NOTIFICATION_ID

    private fun buildReturnIntent(context: Context, type: PermissionEscortType): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_FROM_PERMISSION_ESCORT, true)
            putExtra(EXTRA_PERMISSION_ESCORT_TYPE, type.name)
        }

    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return appProcessInfo.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}

private object NotificationHelper {
    private const val CHANNEL_ESCORT = "permission_escort_channel"

    fun ensureEscortChannel(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ESCORT
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val existing = manager?.getNotificationChannel(CHANNEL_ESCORT)
        val desiredName = context.getString(R.string.permission_escort_channel_name)
        val desiredDescription = context.getString(R.string.permission_escort_channel_description)
        if (existing != null) {
            var needsUpdate = false
            if (existing.name != desiredName) {
                existing.name = desiredName
                needsUpdate = true
            }
            if (existing.description != desiredDescription) {
                existing.description = desiredDescription
                needsUpdate = true
            }
            if (needsUpdate) {
                manager?.createNotificationChannel(existing)
            }
            return CHANNEL_ESCORT
        }
        val channel = android.app.NotificationChannel(
            CHANNEL_ESCORT,
            desiredName,
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = desiredDescription
        }
        manager?.createNotificationChannel(channel)
        return CHANNEL_ESCORT
    }
}
