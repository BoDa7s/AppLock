package com.example.adamapplock

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.adamapplock.ui.theme.AdamAppLockTheme
import com.example.adamapplock.ui.theme.ThemeMode
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable


import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.semantics.semantics
import com.example.adamapplock.security.PasswordRepository
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ScreenLockRotation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.StringRes
import androidx.core.graphics.createBitmap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.adamapplock.protection.HealthCheckResult
import com.example.adamapplock.protection.PermissionSnapshot
import com.example.adamapplock.protection.ProtectionUiState
import com.example.adamapplock.protection.ProtectionViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.WindowManager
import androidx.compose.foundation.border
import com.example.adamapplock.lock.AppLockManager
import com.example.adamapplock.lock.LockScreen
import com.example.adamapplock.protection.BiometricUnlockActivity


private enum class AppLockGateState { Loading, Locked, Unlocked }


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleManager.applyStoredLocale(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val activity = this@MainActivity
        setContent {
            val ctx = LocalContext.current
            val repo = remember { PasswordRepository.get(ctx) }

            // null = loading; true = needs setup; false = ready
            var needsSetup by remember { mutableStateOf<Boolean?>(null) }
            var lockState by remember { mutableStateOf(AppLockGateState.Loading) }

            LaunchedEffect(Unit) {
                val missingPasscode = !repo.isPasswordSet()
                needsSetup = missingPasscode
                if (!missingPasscode) {
                    lockState = if (AppLockManager.shouldLock(ctx)) AppLockGateState.Locked else AppLockGateState.Unlocked
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, needsSetup) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (needsSetup == false) {
                        when (event) {
                            androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                val lockRequired = AppLockManager.shouldLock(ctx)
                                lockState = if (lockRequired) AppLockGateState.Locked else AppLockGateState.Unlocked
                                if (!lockRequired) {
                                    AppLockManager.markUnlocked(ctx)
                                }
                            }

                            else -> {}
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // App-wide theme state
            var themeMode by remember { mutableStateOf(Prefs.getThemeMode(ctx)) }
            var useBiometric by remember { mutableStateOf(Prefs.useBiometric(ctx)) }

            AdamAppLockTheme(themeMode = themeMode) {

                when (needsSetup) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    true -> PasscodeSetupScreen(
                        onDone = {
                            AppLockManager.markUnlocked(ctx)
                            lockState = AppLockGateState.Unlocked
                            needsSetup = false // setup screen already saved to repo  // flip immediately after save
                        }
                    )

                    false -> {
                        when (lockState) {
                            AppLockGateState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }

                            AppLockGateState.Locked -> {
                                DisposableEffect(Unit) {
                                    activity.window.setFlags(
                                        WindowManager.LayoutParams.FLAG_SECURE,
                                        WindowManager.LayoutParams.FLAG_SECURE
                                    )
                                    onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                                }

                                LockScreen(
                                    lockedAppLabel = ctx.getString(R.string.app_name),
                                    useBiometric = useBiometric,
                                    onBiometric = {
                                        BiometricUnlockActivity.launch(ctx) { success ->
                                            if (success) {
                                                AppLockManager.markUnlocked(ctx)
                                                lockState = AppLockGateState.Unlocked
                                            }
                                        }
                                    },
                                    onUnlock = { entered ->
                                        val digits = entered.filter { it.isDigit() }
                                        if (digits.isEmpty()) {
                                            Toast.makeText(ctx, R.string.passcode_empty_error, Toast.LENGTH_SHORT).show()
                                            return@LockScreen
                                        }

                                        val chars = digits.toCharArray()
                                        val ok = repo.verifyPassword(chars)
                                        chars.fill('\u0000')
                                        if (ok) {
                                            AppLockManager.markUnlocked(ctx)
                                            lockState = AppLockGateState.Unlocked
                                        } else {
                                            Toast.makeText(ctx, R.string.passcode_wrong_error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                return@AdamAppLockTheme
                            }

                            AppLockGateState.Unlocked -> Unit
                        }

                        var selectedTab by remember { mutableIntStateOf(0) } // 0 = Main, 1 = Settings
                        val tabs = listOf(R.string.tab_main, R.string.tab_settings)
                        val cs = MaterialTheme.colorScheme
                        val appListViewModel: AppListViewModel = viewModel()
                        val protectionViewModel: ProtectionViewModel = viewModel()
                        val protectionState by protectionViewModel.uiState.collectAsState()
                        val permissions = protectionState.permissions
                        val overlaySettingsIntent = remember(ctx.packageName) {
                            {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${ctx.packageName}")
                                )
                                ctx.startActivity(intent)
                            }
                        }
                        val usageSettingsIntent = remember {
                            { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        }

                        if (!permissions.ready) {
                            BackHandler(enabled = true) {}
                            PermissionGate(
                                permissions = permissions,
                                onRequestOverlay = overlaySettingsIntent,
                                onRequestUsage = usageSettingsIntent
                            )
                            return@AdamAppLockTheme
                        }

                        Column(
                            Modifier.fillMaxSize()
                                .background(cs.background)
                                .statusBarsPadding()
                                .imePadding()
                                .windowInsetsPadding(WindowInsets.displayCutout)
                                .navigationBarsPadding()
                        ) {
                            TabRow(selectedTabIndex = selectedTab) {
                                tabs.forEachIndexed { i, t ->
                                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }) {
                                        Text(stringResource(t), modifier = Modifier.padding(12.dp))
                                    }
                                }
                            }

                            when (selectedTab) {
                                0 -> AppSelectionScreen(
                                    appListViewModel = appListViewModel,
                                    onOpenSettings = { selectedTab = 1 }
                                )
                                1 -> SettingsScreen(
                                    onBack = { selectedTab = 0 },
                                    themeMode = themeMode,
                                    onThemeChange = { mode ->
                                        themeMode = mode
                                        Prefs.setThemeMode(ctx, mode) // keep your theme persistence
                                    },
                                    protectionViewModel = protectionViewModel,
                                    useBiometric = useBiometric,
                                    onBiometricToggle = { enabled ->
                                        useBiometric = enabled
                                        Prefs.setUseBiometric(ctx, enabled)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

    }
}

/** -------- Screens (About section) -------- */

@Composable
private fun LabeledValue(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = cs.onSurfaceVariant)
        Text(value, color = cs.onSurface)
    }
}

fun getAppVersion(ctx: Context): String {
    return try {
        val pm = ctx.packageManager
        val pi = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toString()
        else @Suppress("DEPRECATION") pi.versionCode.toString()
        pi.versionName ?: ctx.getString(R.string.version_unknown) //+ " ($code)"
    } catch (_: Exception) {
        ctx.getString(R.string.version_unknown)
    }
}


/** -------- Screens (top-level composable) -------- */


@Composable
private fun PasscodeSetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val repo = remember { PasswordRepository.get(ctx) }

    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }

    val minDigits = 4
    val maxDigits = 8

    //val focusManager = LocalFocusManager.current
    //val keyboardController = LocalSoftwareKeyboardController.current

    // Use a separate FocusRequester for each field
    val firstFR  = remember { FocusRequester() }
    val secondFR = remember { FocusRequester() }

    // Focus the first field exactly once
    LaunchedEffect(Unit) { firstFR.requestFocus() }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .navigationBarsPadding()
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.passcode_setup_title), color = cs.onBackground, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = p1,
            onValueChange = { raw -> p1 = raw.filter { it.isDigit() }.take(maxDigits) }, // digits only, max 8
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(firstFR),
            label = { Text(stringResource(R.string.passcode_enter_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number, // NumberPassword if your version has it
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { secondFR.requestFocus() }  // move only when user taps Next
            ),
            singleLine = true,
            //modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = p2,
            onValueChange = { raw -> p2 = raw.filter { it.isDigit() }.take(maxDigits) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(secondFR),
            label = { Text(stringResource(R.string.passcode_confirm_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val okLen = p1.length in minDigits..maxDigits
                    if (okLen && p1 == p2) {
                        repo.setNewPassword(p1.toCharArray()) // make setNewPassword use .commit()
                        p1 = ""; p2 = ""
                        onDone()
                    } else {
                        Toast.makeText(
                            ctx,
                            if (!okLen) ctx.getString(R.string.passcode_length_error_range, minDigits, maxDigits) else ctx.getString(R.string.passcode_mismatch_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ),
            singleLine = true,
            //modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val okLen = p1.length in minDigits..maxDigits
                if (okLen && p1 == p2) {
                    repo.setNewPassword(p1.toCharArray())
                    onDone() // tell parent to switch UI (see section B)
                } else {
                    Toast.makeText(
                        ctx,
                        if (!okLen) ctx.getString(R.string.passcode_length_error_range, minDigits, maxDigits) else ctx.getString(R.string.passcode_mismatch_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.action_save)) }
    }
}


private data class LockTimerOption(
    val durationMillis: Long,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
)

private val lockTimerOptions = listOf(
    LockTimerOption(
        durationMillis = Prefs.LOCK_TIMER_IMMEDIATE,
        titleRes = R.string.lock_timer_immediate_title,
        descriptionRes = R.string.lock_timer_immediate_description
    ),
    LockTimerOption(
        durationMillis = 30_000L,
        titleRes = R.string.lock_timer_30_seconds_title,
        descriptionRes = R.string.lock_timer_30_seconds_description
    ),
    LockTimerOption(
        durationMillis = 60_000L,
        titleRes = R.string.lock_timer_1_minute_title,
        descriptionRes = R.string.lock_timer_1_minute_description
    ),
    LockTimerOption(
        durationMillis = 120_000L,
        titleRes = R.string.lock_timer_2_minutes_title,
        descriptionRes = R.string.lock_timer_2_minutes_description
    ),
    LockTimerOption(
        durationMillis = 300_000L,
        titleRes = R.string.lock_timer_5_minutes_title,
        descriptionRes = R.string.lock_timer_5_minutes_description
    ),
    LockTimerOption(
        durationMillis = 600_000L,
        titleRes = R.string.lock_timer_10_minutes_title,
        descriptionRes = R.string.lock_timer_10_minutes_description
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    protectionViewModel: ProtectionViewModel,
    useBiometric: Boolean,
    onBiometricToggle: (Boolean) -> Unit
) {
    BackHandler { onBack() }

    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val protectionUiState by protectionViewModel.uiState.collectAsState()
    val permissions = protectionUiState.permissions
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                protectionViewModel.refreshPermissions(log = true)
                protectionViewModel.refreshServiceState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var lockOnScreenOff by remember { mutableStateOf(Prefs.lockOnScreenOff(ctx)) }
    var timerExpanded by remember { mutableStateOf(false) }
    var selectedTimerMillis by remember { mutableLongStateOf(Prefs.getLockTimerMillis(ctx)) }
    val timerOptions = remember { lockTimerOptions }
    val selectedTimerOption = remember(selectedTimerMillis) {
        timerOptions.firstOrNull { it.durationMillis == selectedTimerMillis } ?: timerOptions.first()
    }
    var languageExpanded by remember { mutableStateOf(false) }
    var selectedLanguageTag by remember { mutableStateOf(Prefs.getLanguageCode(ctx)) }
    val languageOptions = remember { LocaleManager.supportedLanguages }
    val selectedLanguageOption = remember(selectedLanguageTag) {
        LocaleManager.findOption(selectedLanguageTag)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp) // spacing between items
    ) {
        // --- Protection
        item { Text(stringResource(R.string.settings_section_protection), style = MaterialTheme.typography.titleMedium, color = cs.primary) }
        item {
            ProtectionSettingsCard(
                state = protectionUiState,
                permissions = permissions,
                onToggle = { enabled ->
                    if (enabled && !permissions.ready) {
                        Toast.makeText(ctx, R.string.permission_missing_toast, Toast.LENGTH_SHORT).show()
                    }
                    protectionViewModel.setProtectionEnabled(enabled)
                },
                onRequestOverlay = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    )
                    ctx.startActivity(intent)
                },
                onRequestUsage = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                onRunHealthCheck = { protectionViewModel.runHealthCheck() }
            )
        }
        item { HorizontalDivider(color = cs.outlineVariant) }

        // --- Security
        item { Text(stringResource(R.string.settings_section_security), style = MaterialTheme.typography.titleMedium, color = cs.primary) }
        item { ChangePasswordRow() }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Fingerprint, null, tint = cs.onBackground) },
                headlineContent = { Text(stringResource(R.string.use_fingerprint), color = cs.onBackground) },
                trailingContent = {
                    Switch(
                        checked = useBiometric,
                        onCheckedChange = onBiometricToggle,
                        colors = SwitchDefaults.colors(
                            checkedBorderColor = Color.Transparent,
                            checkedThumbColor = cs.onPrimary,
                            checkedTrackColor = cs.primary,
                            uncheckedThumbColor = cs.onSurfaceVariant,
                            uncheckedTrackColor = cs.surfaceVariant,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { HorizontalDivider(color = cs.outlineVariant) }

        // --- Lock timers
        item { Text(stringResource(R.string.settings_section_lock_timers), style = MaterialTheme.typography.titleMedium, color = cs.primary) }
        item {
            ExposedDropdownMenuBox(expanded = timerExpanded, onExpandedChange = { timerExpanded = it }) {
                OutlinedTextField(
                    value = stringResource(selectedTimerOption.titleRes),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timerExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cs.surface,
                        unfocusedContainerColor = cs.surface,
                        disabledContainerColor = cs.surface,
                        focusedIndicatorColor = cs.primary,
                        unfocusedIndicatorColor = cs.outline,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_select_timer)) }
                )
                ExposedDropdownMenu(expanded = timerExpanded, onDismissRequest = { timerExpanded = false }) {
                    timerOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.titleRes)) },
                            onClick = {
                                timerExpanded = false
                                selectedTimerMillis = option.durationMillis
                                Prefs.setLockTimerMillis(ctx, option.durationMillis)
                                Prefs.setSessionUnlocked(ctx, null, null)
                            }
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = stringResource(selectedTimerOption.descriptionRes),
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.ScreenLockRotation, null, tint = cs.onBackground) },
                headlineContent = { Text(stringResource(R.string.lock_screen_off_title), color = cs.onBackground) },
                supportingContent = {
                    Text(stringResource(R.string.lock_screen_off_description), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                },
                trailingContent = {
                    Switch(
                        checked = lockOnScreenOff,
                        onCheckedChange = { enabled ->
                            lockOnScreenOff = enabled
                            Prefs.setLockOnScreenOff(ctx, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedBorderColor = Color.Transparent,
                            checkedThumbColor = cs.onPrimary,
                            checkedTrackColor = cs.primary,
                            uncheckedThumbColor = cs.onSurfaceVariant,
                            uncheckedTrackColor = cs.surfaceVariant,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { HorizontalDivider(color = cs.outlineVariant) }

        // --- Language
        item { Text(stringResource(R.string.settings_section_language), style = MaterialTheme.typography.titleMedium, color = cs.primary) }
        item {
            val languageLabel = stringResource(selectedLanguageOption.labelRes)
            ExposedDropdownMenuBox(expanded = languageExpanded, onExpandedChange = { languageExpanded = it }) {
                OutlinedTextField(
                    value = languageLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cs.surface,
                        unfocusedContainerColor = cs.surface,
                        disabledContainerColor = cs.surface,
                        focusedIndicatorColor = cs.primary,
                        unfocusedIndicatorColor = cs.outline,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    label = { Text(stringResource(R.string.app_language_label)) }
                )
                ExposedDropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                    languageOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes)) },
                            onClick = {
                                languageExpanded = false
                                selectedLanguageTag = option.tag
                                LocaleManager.setAppLanguage(ctx, option.tag)
                            }
                        )
                    }
                }
            }
        }
        item { HorizontalDivider(color = cs.outlineVariant) }

        // --- Theme
        item { Text(stringResource(R.string.settings_section_theme), style = MaterialTheme.typography.titleMedium, color = cs.primary) }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = themeMode == ThemeMode.SYSTEM, onClick = { onThemeChange(ThemeMode.SYSTEM) })
                    Text(stringResource(R.string.theme_system), color = cs.onBackground)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = themeMode == ThemeMode.LIGHT, onClick = { onThemeChange(ThemeMode.LIGHT) })
                    Text(stringResource(R.string.theme_light), color = cs.onBackground)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = themeMode == ThemeMode.DARK, onClick = { onThemeChange(ThemeMode.DARK) })
                    Text(stringResource(R.string.theme_dark), color = cs.onBackground)
                }
            }
        }
        item { HorizontalDivider(color = cs.outlineVariant) }

        // --- About
        item { Text(stringResource(R.string.settings_section_about), style = MaterialTheme.typography.titleMedium, color = cs.primary) }
        item {
            ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = stringResource(R.string.about_app_version_label), value = getAppVersion(ctx))
                    HorizontalDivider(color = cs.outlineVariant)
                    LabeledValue(label = stringResource(R.string.about_developer_label), value = stringResource(R.string.developer_name))
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) } // small bottom buffer
    }
}

data class AppEntry(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap?
)

data class AppListUiState(
    val isLoading: Boolean = true,
    val apps: List<AppEntry> = emptyList()
)

private enum class AppSelectionSegment(@StringRes val labelRes: Int) {
    Unlocked(R.string.segment_unlocked),
    Locked(R.string.segment_locked)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSelectionScreen(
    appListViewModel: AppListViewModel,
    onOpenSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val uiState by appListViewModel.uiState.collectAsState()

    var locked by remember { mutableStateOf(Prefs.getLockedApps(ctx).toSet()) }
    var hapticsEnabled by remember { mutableStateOf(Prefs.hapticsEnabled(ctx)) }
    var selectedSegment by rememberSaveable { mutableStateOf(AppSelectionSegment.Unlocked) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val segments = remember { AppSelectionSegment.entries.toTypedArray() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Use Context to get the system AccessibilityManager
    val am = ctx.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
    val reduceMotion = am?.isTouchExplorationEnabled == true
    val lockedContainerColor = cs.primaryContainer
    val unlockedContainerColor = cs.surfaceContainerHigh
    val elevatedCardElevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
    val cardBorder = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f))

    val lockedApps = remember(uiState.apps, locked) {
        uiState.apps.filter { locked.contains(it.pkg) }
    }
    val unlockedApps = remember(uiState.apps, locked) {
        uiState.apps.filter { !locked.contains(it.pkg) }
    }

    val visibleApps = remember(selectedSegment, searchQuery, lockedApps, unlockedApps) {
        val base = when (selectedSegment) {
            AppSelectionSegment.Unlocked -> unlockedApps
            AppSelectionSegment.Locked -> lockedApps
        }
        if (searchQuery.isBlank()) base
        else base.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    val hasAnyApps = uiState.apps.isNotEmpty()
    val emptyStateMessage = remember(selectedSegment, searchQuery, hasAnyApps) {
        when {
            !hasAnyApps -> ctx.getString(R.string.no_launchable_apps_found)
            searchQuery.isBlank() && selectedSegment == AppSelectionSegment.Locked -> ctx.getString(R.string.no_locked_apps_yet)
            searchQuery.isBlank() -> ctx.getString(R.string.all_apps_locked)
            else -> ctx.getString(R.string.no_apps_match_search)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                locked = Prefs.getLockedApps(ctx).toSet()
                hapticsEnabled = Prefs.hapticsEnabled(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            )
        },
        containerColor = cs.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SingleChoiceSegmentedButtonRow {
                        segments.forEachIndexed { index, segment ->
                            SegmentedButton(
                                selected = selectedSegment == segment,
                                onClick = { selectedSegment = segment },
                                shape = SegmentedButtonDefaults.itemShape(index, segments.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = cs.primaryContainer,
                                    activeContentColor = cs.onPrimaryContainer,
                                    inactiveContainerColor = cs.surface,
                                    inactiveContentColor = cs.onSurface
                                ),
                                icon = {}
                            ) {
                                Text(stringResource(segment.labelRes))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cs.surface,
                        unfocusedContainerColor = cs.surface,
                        disabledContainerColor = cs.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            visibleApps.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emptyStateMessage, color = cs.onSurfaceVariant)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cs.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterEnd)) {
                                Text(stringResource(R.string.protection_manage_in_settings))
                            }
                        }
                    }

                    items(
                        items = visibleApps,
                        key = { it.pkg }
                    ) { app ->
                        val checked = locked.contains(app.pkg)
                        val targetColor by animateColorAsState(
                            targetValue = if (checked) lockedContainerColor else unlockedContainerColor,
                            animationSpec = if (reduceMotion) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy),
                            label = "cardColor"
                        )
                        val targetScale by animateFloatAsState(
                            targetValue = if (checked) 0.98f else 1f,
                            animationSpec = if (reduceMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "cardScale"
                        )

                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(containerColor = targetColor),
                            elevation = elevatedCardElevation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(scaleX = targetScale, scaleY = targetScale)
                                // Removed .border(...)
                                .animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = if (reduceMotion) snap() else spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                )
                        )
                        {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Android,
                                        contentDescription = null,
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        color = cs.onSurface,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (checked) stringResource(R.string.segment_locked) else stringResource(R.string.segment_unlocked),
                                        color = cs.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Switch(
                                    checked = checked,
                                    onCheckedChange = { nowLocked ->
                                        val next = locked.toMutableSet().apply {
                                            if (nowLocked) add(app.pkg) else remove(app.pkg)
                                        }
                                        Prefs.saveLockedApps(ctx, next)
                                        locked = next

                                        if (hapticsEnabled) {
                                            haptics.performHapticFeedback(
                                                if (nowLocked) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                                            )
                                        }

                                        coroutineScope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar(
                                                ctx.getString(
                                                    if (nowLocked) R.string.snackbar_moved_locked else R.string.snackbar_moved_unlocked,
                                                    app.label
                                                )
                                            )
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedBorderColor = Color.Transparent,
                                        checkedThumbColor = cs.onPrimary,
                                        checkedTrackColor = cs.primary,
                                        uncheckedThumbColor = cs.onSurfaceVariant,
                                        uncheckedTrackColor = cs.surfaceVariant,
                                        uncheckedBorderColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestList(
    permissions: PermissionSnapshot,
    onRequestOverlay: () -> Unit,
    onRequestUsage: () -> Unit,
    modifier: Modifier = Modifier,
    showGranted: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PermissionRequestCard(
            title = stringResource(R.string.permission_overlay_title),
            description = stringResource(R.string.permission_overlay_description),
            granted = permissions.overlayGranted,
            onClick = onRequestOverlay,
            showWhenGranted = showGranted
        )
        PermissionRequestCard(
            title = stringResource(R.string.permission_usage_title),
            description = stringResource(R.string.permission_usage_description),
            granted = permissions.usageGranted,
            onClick = onRequestUsage,
            showWhenGranted = showGranted
        )
    }
}

@Composable
private fun PermissionRequestCard(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
    showWhenGranted: Boolean = false
) {
    if (granted && !showWhenGranted) return

    val cs = MaterialTheme.colorScheme
    val statusText = if (granted) {
        stringResource(R.string.permission_granted_label)
    } else {
        stringResource(R.string.permission_required_label)
    }
    val statusColor = if (granted) cs.tertiary else cs.error
    val statusIcon = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Text(description, color = cs.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor
                )
                Text(text = statusText, color = statusColor)
            }
            if (!granted) {
                Button(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.permission_button_open))
                }
            }
        }
    }
}

@Composable
private fun ProtectionStatusPanel(
    state: ProtectionUiState,
    onOpenSettings: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val statusText = when {
        state.protectionEnabled && state.permissions.ready -> stringResource(R.string.protection_status_running)
        state.pendingEnable -> stringResource(R.string.protection_status_pending_permissions)
        !state.permissions.ready -> stringResource(R.string.protection_status_permissions_missing)
        else -> stringResource(R.string.protection_status_stopped)
    }
    val runningDetail = if (state.serviceRunning) {
        stringResource(R.string.protection_service_running)
    } else {
        stringResource(R.string.protection_service_not_running)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(statusText, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Text(
                text = stringResource(R.string.protection_status_summary),
                color = cs.onSurfaceVariant
            )
            Text(runningDetail, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.protection_manage_in_settings))
            }
        }
    }
}

@Composable
private fun PermissionGate(
    permissions: PermissionSnapshot,
    onRequestOverlay: () -> Unit,
    onRequestUsage: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.permission_gate_title),
            style = MaterialTheme.typography.titleLarge,
            color = cs.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.permission_gate_description),
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        PermissionRequestList(
            permissions = permissions,
            onRequestOverlay = onRequestOverlay,
            onRequestUsage = onRequestUsage,
            showGranted = true
        )
    }
}

@Composable
private fun ProtectionSettingsCard(
    state: ProtectionUiState,
    permissions: PermissionSnapshot,
    onToggle: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestUsage: () -> Unit,
    onRunHealthCheck: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val toggleChecked = state.protectionEnabled || state.pendingEnable
    val ready = permissions.ready
    val statusText = when {
        state.protectionEnabled && ready -> stringResource(R.string.protection_status_running)
        state.pendingEnable -> stringResource(R.string.protection_status_pending_permissions)
        !ready -> stringResource(R.string.protection_status_permissions_missing)
        else -> stringResource(R.string.protection_status_stopped)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.protection_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                    Text(statusText, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = toggleChecked,
                    onCheckedChange = onToggle,
                    enabled = ready || toggleChecked,
                    colors = SwitchDefaults.colors(
                        checkedBorderColor = Color.Transparent,
                        checkedThumbColor = cs.onPrimary,
                        checkedTrackColor = cs.primary,
                        uncheckedThumbColor = cs.onSurfaceVariant,
                        uncheckedTrackColor = cs.surfaceVariant,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }

            Text(
                text = stringResource(R.string.protection_settings_description),
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            if (!ready) {
                PermissionRequestList(
                    permissions = permissions,
                    onRequestOverlay = onRequestOverlay,
                    onRequestUsage = onRequestUsage
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRunHealthCheck,
                    enabled = !state.healthCheckInProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val label = if (state.healthCheckInProgress) {
                        stringResource(R.string.health_check_running)
                    } else {
                        stringResource(R.string.health_check_button)
                    }
                    Text(label)
                }

                state.healthCheckResult?.let { result ->
                    HealthCheckSummary(result = result)
                }
            }
        }
    }
}

@Composable
private fun HealthCheckSummary(result: HealthCheckResult) {
    val cs = MaterialTheme.colorScheme
    val passed = result.ready && result.serviceRunning && result.overlayTestPassed
    val statusText = if (passed) {
        stringResource(R.string.health_check_passed)
    } else {
        stringResource(R.string.health_check_needs_attention)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(statusText, color = if (passed) cs.primary else cs.error, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(
                    R.string.health_check_detail_overlay,
                    result.overlayGranted.toStatusLabel()
                ),
                color = cs.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.health_check_detail_usage,
                    result.usageGranted.toStatusLabel()
                ),
                color = cs.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.health_check_detail_service,
                    result.serviceRunning.toStatusLabel()
                ),
                color = cs.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.health_check_detail_overlay_draw,
                    result.overlayTestPassed.toStatusLabel()
                ),
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Boolean.toStatusLabel(): String =
    if (this) stringResource(R.string.health_check_state_ok) else stringResource(R.string.health_check_state_missing)

class AppListViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun loadApps() {
        val application = getApplication<Application>()
        val pm = application.packageManager
        val defaultIconSize = (40.dp.value * application.resources.displayMetrics.density)
            .roundToInt()
            .coerceAtLeast(1)

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { ai ->
                    val label = pm.getApplicationLabel(ai).toString()
                    val iconBitmap = runCatching { pm.getApplicationIcon(ai.packageName) }
                        .getOrNull()
                        ?.toImageBitmap(defaultIconSize)

                    AppEntry(
                        pkg = ai.packageName,
                        label = label,
                        icon = iconBitmap
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()

            _uiState.value = AppListUiState(
                isLoading = false,
                apps = installedApps
            )
        }
    }
}

private fun Drawable.toImageBitmap(defaultSizePx: Int): ImageBitmap {
    val width = when {
        intrinsicWidth > 0 -> intrinsicWidth
        else -> defaultSizePx
    }
    val height = when {
        intrinsicHeight > 0 -> intrinsicHeight
        else -> defaultSizePx
    }

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}


@Composable
private fun ChangePasswordRow() {
    val context = LocalContext.current
    val repo = remember { PasswordRepository.get(context) }

    var showDialog by remember { mutableStateOf(false) }
    var isSet by remember { mutableStateOf(repo.isPasswordSet()) } // observable state

    // Refresh once when we enter composition
    LaunchedEffect(Unit) { isSet = repo.isPasswordSet() }

    // Also refresh whenever the screen resumes (e.g., returning from first-run setup)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isSet = repo.isPasswordSet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val changePasscodeLabel = stringResource(R.string.change_passcode_title)
    val changePasscodeMessage = if (isSet) {
        stringResource(R.string.update_master_passcode)
    } else {
        stringResource(R.string.set_master_passcode)
    }

    ListItem(
        headlineContent = { Text(changePasscodeLabel) },
        supportingContent = { Text(changePasscodeMessage) },
        leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) }, // use Lock for widest compatibility
        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .semantics { contentDescription = changePasscodeLabel }
    )

    if (showDialog) {
        ChangePasswordDialog(
            isExisting = isSet,
            onDismiss = { showDialog = false },
            onConfirm = { current, new, confirm ->
                val minDigits = 4
                val maxDigits = 8
                val newStr = new.joinToString("")
                when {
                    newStr.length !in minDigits..maxDigits ->
                        Toast.makeText(context, context.getString(R.string.passcode_length_error_range, minDigits, maxDigits), Toast.LENGTH_SHORT).show()
                    !newStr.all { it.isDigit() } ->
                        Toast.makeText(context, context.getString(R.string.passcode_digits_only_error), Toast.LENGTH_SHORT).show()
                    !new.contentEquals(confirm) ->
                        Toast.makeText(context, context.getString(R.string.passcode_mismatch_error), Toast.LENGTH_SHORT).show()
                    else -> {
                        val ok = repo.changePassword(current, new)
                        if (ok) {
                            isSet = true           // <- update UI state immediately
                            Toast.makeText(context, context.getString(R.string.passcode_updated_message), Toast.LENGTH_SHORT).show()
                            showDialog = false
                        } else {
                            Toast.makeText(context, context.getString(R.string.current_passcode_incorrect_message), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
}





@Composable
private fun ChangePasswordDialog(
    isExisting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (current: CharArray?, new: CharArray, confirm: CharArray) -> Unit,
) {
    var current by remember { mutableStateOf(charArrayOf()) }
    var newPw by remember { mutableStateOf(charArrayOf()) }
    var confirmPw by remember { mutableStateOf(charArrayOf()) }

    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isExisting) R.string.change_passcode_title else R.string.set_passcode_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isExisting) PasscodeField(
                    labelRes = R.string.current_passcode_label,
                    value = current,
                    onValueChange = { current = it },
                    visible = showCurrent,
                    onToggleVisibility = { showCurrent = !showCurrent },
                    imeAction = ImeAction.Next,
                    maxLength = 8
                )

                PasscodeField(
                    labelRes = R.string.new_passcode_label,
                    value = newPw,
                    onValueChange = { newPw = it },
                    visible = showNew,
                    onToggleVisibility = { showNew = !showNew },
                    imeAction = ImeAction.Next,
                    maxLength = 8
                )

                PasscodeField(
                    labelRes = R.string.confirm_new_passcode_label,
                    value = confirmPw,
                    onValueChange = { confirmPw = it },
                    visible = showConfirm,
                    onToggleVisibility = { showConfirm = !showConfirm },
                    imeAction = ImeAction.Done,
                    maxLength = 8
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(if (isExisting) current else null, newPw, confirmPw)
                current.fill('\u0000'); newPw.fill('\u0000'); confirmPw.fill('\u0000')
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                current.fill('\u0000'); newPw.fill('\u0000'); confirmPw.fill('\u0000')
            }) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

//// test after git update


@Composable
private fun PasscodeField(
    @StringRes labelRes: Int,
    value: CharArray,
    onValueChange: (CharArray) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    imeAction: ImeAction,
    maxLength: Int = 6,
) {
    var text by remember(value) { mutableStateOf(String(value)) }
    val label = stringResource(labelRes)
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            // Keep only digits and cap length
            val digitsOnly = raw.filter { it.isDigit() }.take(maxLength)
            text = digitsOnly
            onValueChange(digitsOnly.toCharArray())
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = imeAction,
            // If your Compose version supports it, you can use KeyboardType.NumberPassword
            // keyboardType = KeyboardType.NumberPassword
            keyboardType = KeyboardType.Number
        ),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val iconLabel = if (visible) stringResource(R.string.passcode_visibility_hide) else stringResource(R.string.passcode_visibility_show)
            TextButton(onClick = onToggleVisibility) { Text(iconLabel) }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
