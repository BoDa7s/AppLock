package com.awi.lock.protection

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.awi.lock.Prefs
import com.awi.lock.PermissionUtils
import com.awi.lock.lock.LockScreen
import com.awi.lock.ui.theme.AWILockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OverlayLocker(context: Context) {

    private companion object {
        private const val TAG = "OverlayLocker"
        private const val MIN_OVERLAY_INTERVAL_MS = 250L
    }

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayView: ComposeView? = null
    private var lockedPackage: String? = null
    private var lastOverlayActionAt = 0L
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    fun showLockedApp(
        pkg: String,
        appLabel: String?,
        useBiometric: Boolean,
        onUnlock: (String) -> Unit,
        onBiometric: () -> Unit
    ): Boolean {
        if (overlayView != null && lockedPackage == pkg) return true
        if (!PermissionUtils.hasOverlayPermission(appContext)) {
            Log.w(TAG, "showLockedApp skipped; overlay permission missing for $pkg")
            return false
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastOverlayActionAt < MIN_OVERLAY_INTERVAL_MS) {
            Log.d(TAG, "showLockedApp debounced for $pkg")
            return false
        }
        lastOverlayActionAt = now

        Log.i(TAG, "showLockedApp request pkg=$pkg label=$appLabel")

        scope.launch {
            dismiss()

            lockedPackage = pkg
            val lifecycleOwner = OverlayLifecycleOwner().apply { markResumed() }
            val view = ComposeView(appContext).apply {
                // Attach the lifecycle and saved state owners to the view
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent {
                    AWILockTheme(themeMode = Prefs.getThemeMode(appContext)) {
                        LockScreen(
                            lockedAppLabel = appLabel,
                            useBiometric = useBiometric,
                            onBiometric = onBiometric,
                            onUnlock = onUnlock
                        )
                    }
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            runCatching { windowManager.addView(view, params) }
                .onSuccess {
                    overlayView = view
                    overlayLifecycleOwner = lifecycleOwner
                    Log.i(TAG, "overlay_added pkg=$pkg")
                }
                .onFailure { err ->
                    Log.e(TAG, "overlay_add_failed pkg=$pkg", err)
                    lockedPackage = null
                    lifecycleOwner.destroy()
                }
        }
        return true
    }

    fun dismiss(reason: String? = null) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOverlayActionAt < MIN_OVERLAY_INTERVAL_MS && overlayView == null) return
        lastOverlayActionAt = now

        scope.launch {
            overlayView?.let {
                runCatching { windowManager.removeViewImmediate(it) }
                    .onFailure { err -> Log.e(TAG, "overlay_remove_failed", err) }
            }
            overlayLifecycleOwner?.destroy()
            overlayView = null
            lockedPackage = null
            overlayLifecycleOwner = null
            reason?.let { Log.i(TAG, "overlay_dismissed reason=$it") }
        }
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this).apply {
        performRestore(null)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun markResumed() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
