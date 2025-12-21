package com.example.adamapplock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PermissionEscortService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = PermissionEscortManager.activeSession(this)
        if (session == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            PermissionEscortManager.monitorNotificationId(),
            PermissionEscortManager.buildMonitorNotification(this)
        )

        serviceScope.launch {
            while (isActive && PermissionEscortManager.activeSession(this@PermissionEscortService) != null) {
                PermissionEscortManager.handleMonitorTick(this@PermissionEscortService)
                delay(PermissionEscortManager.monitorIntervalMs())
            }
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PermissionEscortService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PermissionEscortService::class.java))
        }
    }
}
