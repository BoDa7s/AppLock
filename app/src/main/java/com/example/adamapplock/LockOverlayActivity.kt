package com.example.adamapplock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.example.adamapplock.ui.theme.AdamAppLockTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.input.ImeAction


const val EXTRA_LOCKED_PKG = "locked_pkg"

class LockOverlayActivity : FragmentActivity() {

    private var lockedPkg: String? = null
    private var backCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lockedPkg = intent.getStringExtra(EXTRA_LOCKED_PKG)

        backCallback = onBackPressedDispatcher.addCallback(this, true) {
            // Intentionally consume back gestures to keep the overlay visible.
        }

        // Show over lockscreen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Make content draw edge-to-edge; we’ll pad with insets in Compose
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val ctx = LocalContext.current
            //val themeMode = remember {Prefs.getThemeMode(ctx) }



            AdamAppLockTheme(themeMode = Prefs.getThemeMode(this)) {
                val useBiometric = remember { Prefs.useBiometric(ctx) }

                // Auto-start biometric if enabled & available
                LaunchedEffect(useBiometric) {
                    if (useBiometric && canUseBiometric()) {
                        startBiometric { completeUnlock() }
                    }
                }

                val repo = com.example.adamapplock.security.PasswordRepository.get(this)

                LockScreen(
                    useBiometric = useBiometric && canUseBiometric(),
                    onBiometric = { startBiometric { completeUnlock() } },
                    onUnlock = { pass ->
                        // keep digits-only in case the field lets other chars in
                        val digitsOnly = pass.filter { it.isDigit() }

                        if (digitsOnly.length < 4) {
                            Toast.makeText(
                                this,
                                "Enter your passcode",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val chars = digitsOnly.toCharArray()

                            val ok = repo.verifyPassword(chars)

                            // wipe temp copy
                            java.util.Arrays.fill(chars, '\u0000')

                            if (ok) {
                                completeUnlock()
                            } else {
                                Toast.makeText(this, "Wrong passcode", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                )
            }
        }
    }

    // If the overlay is re-used (launchMode=singleTop), update the target package.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lockedPkg = intent.getStringExtra(EXTRA_LOCKED_PKG)
    }

    private fun canUseBiometric(): Boolean {
        val mgr = BiometricManager.from(this)
        return mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun startBiometric(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(code: Int, err: CharSequence) {
                    super.onAuthenticationError(code, err)
                    // fall back to passcode UI
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock")
            .setSubtitle("Use fingerprint to unlock")
            .setNegativeButtonText("Use passcode")
            .build()

        prompt.authenticate(info)
    }

    private fun completeUnlock() {
        val pkg = lockedPkg ?: return
        val uid = runCatching { packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull()

        // Mark this foreground session as unlocked (pkg + uid)
        Prefs.setSessionUnlocked(this, pkg, uid)
        Prefs.setLastUnlockNow(this)

        // Allow back navigation so the overlay can close normally after a successful unlock.
        backCallback?.remove()
        backCallback = null

        // Relaunch the target app so the user returns there
        packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            startActivity(launch)
        }

        // Close the overlay task
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        backCallback?.remove()
        backCallback = null
        super.onDestroy()
    }
}

@Composable
private fun LockScreen(
    useBiometric: Boolean,
    onBiometric: () -> Unit,
    onUnlock: (String) -> Unit
) {
    val ctx = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var passcode by remember { mutableStateOf("") }

    // Check if biometric is actually available
    val biometricAvailable = remember {
        BiometricManager.from(ctx).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // Only auto-focus (and show keyboard) when we’re not using biometric
    val shouldAutoFocus = !(useBiometric && biometricAvailable)
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "App Locked",
            color = cs.onBackground,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = passcode,
            onValueChange = { passcode = it.filter { ch -> ch.isDigit() }.take(8) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = { Text("Passcode") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onUnlock(passcode)
                }
            ),
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = cs.primary,
                unfocusedBorderColor = cs.outline,
                cursorColor = cs.primary,
                containerColor = cs.surface,
                focusedTextColor = cs.onSurface,
                unfocusedTextColor = cs.onSurfaceVariant
            )
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onUnlock(passcode) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Unlock") }

        if (useBiometric && biometricAvailable) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    // Keep IME hidden when choosing fingerprint
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onBiometric()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Use fingerprint") }
        }
    }
}