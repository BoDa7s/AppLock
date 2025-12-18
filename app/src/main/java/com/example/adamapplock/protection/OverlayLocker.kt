package com.example.adamapplock.protection

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.example.adamapplock.Prefs
import com.example.adamapplock.lock.LockScreen
import com.example.adamapplock.ui.theme.AdamAppLockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OverlayLocker(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayView: ComposeView? = null
    private var lockedPackage: String? = null

    fun showLockedApp(
        pkg: String,
        appLabel: String?,
        useBiometric: Boolean,
        onUnlock: (String) -> Unit,
        onBiometric: () -> Unit
    ) {
        if (overlayView != null && lockedPackage == pkg) return

        scope.launch {
            dismiss()

            lockedPackage = pkg
            val view = ComposeView(appContext).apply {
                setContent {
                    AdamAppLockTheme(themeMode = Prefs.getThemeMode(appContext)) {
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
                .onSuccess { overlayView = view }
        }
    }

    fun dismiss() {
        scope.launch {
            overlayView?.let {
                runCatching { windowManager.removeViewImmediate(it) }
            }
            overlayView = null
            lockedPackage = null
        }
    }
}
