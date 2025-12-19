package com.example.adamapplock.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.adamapplock.Prefs
import com.example.adamapplock.R
import com.example.adamapplock.SessionTimeoutScheduler
import com.example.adamapplock.security.PasswordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext

class ProtectionService : Service() {


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var lastLockedPkg: String? = null
    private var lastPermissions: PermissionSnapshot? = null

    private val overlayLocker by lazy { OverlayLocker(this) }
    private val usageManager by lazy { getSystemService(UsageStatsManager::class.java) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val timer = Prefs.getLockTimerMillis(this@ProtectionService)
            val lockOnScreenOff = Prefs.lockOnScreenOff(this@ProtectionService)
            if (lockOnScreenOff || timer == Prefs.LOCK_TIMER_IMMEDIATE) {
                clearUnlockedSession()
            } else if (Prefs.getSessionUnlocked(this@ProtectionService) != null) {
                Prefs.setLastBackgroundNow(this@ProtectionService)
                SessionTimeoutScheduler.ensureInitialized(applicationContext)
                SessionTimeoutScheduler.schedule()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        SessionTimeoutScheduler.ensureInitialized(applicationContext)
        SessionTimeoutScheduler.schedule()
    }

    override fun onDestroy() {
        stopMonitoring()
        runCatching { unregisterReceiver(screenOffReceiver) }
        overlayLocker.dismiss("service_destroy")
        SessionTimeoutScheduler.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startMonitoring()
        Prefs.setProtectionEnabled(this, true)
        Log.i(TAG, "Protection service started")
        return START_STICKY
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch { monitorLoop() }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private suspend fun monitorLoop() {
        val passwordRepo = PasswordRepository.get(this)

        while (currentCoroutineContext().isActive) {
            val permissions = PermissionSnapshot.capture(this)
            if (permissions != lastPermissions) {
                lastPermissions = permissions
                Log.i(TAG, "permissions overlay=${permissions.overlayGranted} usage=${permissions.usageGranted}")
            }

            val hasOverlay = permissions.overlayGranted
            val hasUsage = permissions.usageGranted
            val interactive = powerManager?.isInteractive == true
            val delayMs = if (interactive) 650L else 1800L

            if (!hasOverlay || !hasUsage) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    overlayLocker.dismiss("permissions_missing")
                }
                if (lastLockedPkg != null) {
                    Log.w(TAG, "Stopping overlay due to missing permission")
                }
                lastLockedPkg = null
                delay(delayMs)
                continue
            }

            val topPkg = resolveTopPackage()
            if (topPkg.isNullOrBlank() || topPkg == packageName) {
                delay(delayMs)
                continue
            }

            if (lastLockedPkg != null && lastLockedPkg != topPkg) {
                lastLockedPkg = null
            }

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

            val appInfo = runCatching { packageManager.getApplicationInfo(topPkg, 0) }.getOrNull()
            val newUid = appInfo?.uid
            val isRealApp = packageManager.getLaunchIntentForPackage(topPkg) != null
            val isLocked = Prefs.isAppLocked(this, topPkg)

            if (activeSessionPkg != null) {
                val samePkg = (topPkg == activeSessionPkg)
                val sameUid = (activeSessionUid != null && newUid != null && activeSessionUid == newUid)

                if (samePkg) {
                    Prefs.clearLastBackground(this)
                    SessionTimeoutScheduler.cancel()
                    delay(delayMs)
                    continue
                }
                if (sameUid) {
                    delay(delayMs)
                    continue
                }

                if (timerMillis > Prefs.LOCK_TIMER_IMMEDIATE) {
                    Prefs.setLastBackgroundNow(this)
                    SessionTimeoutScheduler.ensureInitialized(applicationContext)
                    SessionTimeoutScheduler.schedule()
                } else {
                    clearUnlockedSession()
                }

                if (!isRealApp) {
                    delay(delayMs)
                    continue
                }
            }

            if (isLocked && isRealApp) {
                // debounce duplicate overlay launches
                if (lastLockedPkg == topPkg) {
                    delay(delayMs)
                    continue
                }

                val label = appInfo?.loadLabel(packageManager)?.toString()
                val allowBiometric = Prefs.useBiometric(this)

                // FIX: Switch to Main thread to show UI
                val requested = kotlinx.coroutines.withContext(Dispatchers.Main) {
                    overlayLocker.showLockedApp(
                        pkg = topPkg,
                        appLabel = label,
                        useBiometric = allowBiometric,
                        onUnlock = { passcode ->
                            // This callback usually runs on Main, but let's be safe with logic
                            val digitsOnly = passcode.filter { it.isDigit() }
                            if (digitsOnly.isEmpty()) {
                                Toast.makeText(this@ProtectionService, getString(R.string.passcode_empty_error), Toast.LENGTH_SHORT).show()
                                return@showLockedApp
                            }

                            val chars = digitsOnly.toCharArray()
                            // verifyPassword might be slow, consider putting it back on Default if it uses heavy hashing
                            // For now, it's likely fine here for simple PINs.
                            val ok = passwordRepo.verifyPassword(chars)
                            java.util.Arrays.fill(chars, '\u0000')
                            if (ok) {
                                completeUnlock(topPkg, newUid)
                            } else {
                                Toast.makeText(this@ProtectionService, getString(R.string.passcode_wrong_error), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBiometric = {
                            BiometricUnlockActivity.launch(this@ProtectionService) { success ->
                                if (success) completeUnlock(topPkg, newUid)
                            }
                        }
                    )
                }

                if (requested) {
                    lastLockedPkg = topPkg
                    Log.i(TAG, "overlay_requested pkg=$topPkg label=$label")
                }
            } else {
                if (isRealApp && activeSessionPkg == null) clearUnlockedSession()

                // FIX: Switch to Main thread to hide UI
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    overlayLocker.dismiss("not_locked")
                }
                lastLockedPkg = null
            }

            delay(delayMs)
        }
    }

    private fun resolveTopPackage(): String? {
        val usage = usageManager ?: return null
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 5_000, now)
        val event = UsageEvents.Event()
        var lastPkg: String? = null
        var lastTs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                if (event.timeStamp > lastTs) {
                    lastTs = event.timeStamp
                    lastPkg = event.packageName
                }
            }
        }
        return lastPkg
    }

    private fun completeUnlock(pkg: String, uid: Int?) {
        Prefs.setSessionUnlocked(this, pkg, uid)
        SessionTimeoutScheduler.ensureInitialized(applicationContext)
        SessionTimeoutScheduler.cancel()
        Prefs.clearLastBackground(this)
        Prefs.setLastUnlockNow(this)
        lastLockedPkg = null
        overlayLocker.dismiss("unlocked")

        packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            startActivity(launch)
        }
    }

    private fun clearUnlockedSession() {
        Prefs.setSessionUnlocked(this, null, null)
        Prefs.clearLastBackground(this)
        SessionTimeoutScheduler.ensureInitialized(applicationContext)
        SessionTimeoutScheduler.cancel()
        lastLockedPkg = null
    }

    private fun buildNotification(): Notification {
        val channelId = ensureNotificationChannel()

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.protection_notification_title))
            .setContentText(getString(R.string.protection_notification_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel(): String {
        val channelId = NOTIFICATION_CHANNEL
        val mgr = getSystemService(NotificationManager::class.java)
        val desiredName = getString(R.string.protection_notification_channel)
        val desiredDescription = getString(R.string.protection_notification_channel_description)

        val existing = mgr?.getNotificationChannel(channelId)
        if (existing != null) {
            val needsRecreate = existing.name != desiredName
            if (needsRecreate) {
                mgr.deleteNotificationChannel(channelId)
            } else {
                if (existing.description != desiredDescription) {
                    existing.description = desiredDescription
                    mgr.createNotificationChannel(existing)
                }
                return channelId
            }
        }

        val channel = NotificationChannel(
            channelId,
            desiredName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = desiredDescription
        }
        mgr?.createNotificationChannel(channel)
        return channelId
    }

    companion object {
        private const val TAG = "ProtectionService"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL = "app_lock_protection"

        fun start(context: Context) {
            val intent = Intent(context, ProtectionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProtectionService::class.java))
        }
    }
}
