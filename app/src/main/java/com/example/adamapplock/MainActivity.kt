package com.example.adamapplock

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt
import androidx.activity.ComponentActivity
import androidx.core.graphics.createBitmap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val ctx = LocalContext.current
            val repo = remember { PasswordRepository.get(ctx) }

            // null = loading; true = needs setup; false = ready
            var needsSetup by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) { needsSetup = !repo.isPasswordSet() }

            // App-wide theme state
            var themeMode by remember { mutableStateOf(Prefs.getThemeMode(ctx)) }




            AdamAppLockTheme(themeMode = themeMode) {

                when (needsSetup) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    true -> PasscodeSetupScreen(
                        onDone = { needsSetup = false } // setup screen already saved to repo  // flip immediately after save
                    )

                    false -> {
                        var selectedTab by remember { mutableIntStateOf(0) } // 0 = Main, 1 = Settings
                        val tabs = listOf("Main", "Settings")
                        val cs = MaterialTheme.colorScheme
                        val appListViewModel: AppListViewModel = viewModel()

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
                                        Text(t, modifier = Modifier.padding(12.dp))
                                    }
                                }
                            }

                            when (selectedTab) {
                                0 -> AppSelectionScreen(appListViewModel)
                                1 -> SettingsScreen(
                                    onBack = { selectedTab = 0 },
                                    themeMode = themeMode,
                                    onThemeChange = { mode ->
                                        themeMode = mode
                                        Prefs.setThemeMode(ctx, mode) // keep your theme persistence
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
        "${pi.versionName} ($code)"
    } catch (_: Exception) {
        "Unknown"
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
        Text("Set a passcode", color = cs.onBackground, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = p1,
            onValueChange = { raw -> p1 = raw.filter { it.isDigit() }.take(8) }, // digits only, max 8
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(firstFR),
            label = { Text("Enter passcode") },
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
            onValueChange = { raw -> p2 = raw.filter { it.isDigit() }.take(8) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(secondFR),
            label = { Text("Confirm passcode") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val okLen = p1.length in 4..8
                    if (okLen && p1 == p2) {
                        repo.setNewPassword(p1.toCharArray()) // make setNewPassword use .commit()
                        p1 = ""; p2 = ""
                        onDone()
                    } else {
                        Toast.makeText(
                            ctx,
                            if (!okLen) "Passcode must be 4–8 digits" else "Passcodes do not match",
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
                val okLen = p1.length in 4..8
                if (okLen && p1 == p2) {
                    repo.setNewPassword(p1.toCharArray())
                    onDone() // tell parent to switch UI (see section B)
                } else {
                    Toast.makeText(
                        ctx,
                        if (!okLen) "Passcode must be 4–8 digits" else "Passcodes do not match",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
    }
}


@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    BackHandler { onBack() }  // system back pops you to Main/AppSelection
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var useBiometric by remember { mutableStateOf(Prefs.useBiometric(ctx)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .navigationBarsPadding()   // keep content above gesture bar
            .statusBarsPadding()      // avoid notch/status bar
            //.verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // (small visual fix) this text should use onBackground, not background
        //Text("Settings", color = cs.onBackground, style = MaterialTheme.typography.titleLarge)
        Text("Security", style = MaterialTheme.typography.titleMedium, color = cs.primary)

        // Change passcode Block
        ChangePasswordRow()
        //HorizontalDivider(Modifier, DividerDefaults.Thickness, color = cs.outlineVariant)
        //Spacer(Modifier.height(16.dp))

        // ---- Biometric
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null, // decorative
                    tint = cs.onBackground
                )
            },
            headlineContent = { Text("Use fingerprint", color = cs.onBackground) },
            trailingContent = {

                /*Switch(
                    checked = useBiometric,
                    onCheckedChange = {
                        useBiometric = it
                        Prefs.setUseBiometric(ctx, it)
                    }
                )*/

                //// new switch with colors styles
                Switch(
                    checked = useBiometric,
                    onCheckedChange = {
                        useBiometric = it
                        Prefs.setUseBiometric(ctx, it)
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


        HorizontalDivider(Modifier, DividerDefaults.Thickness, color = cs.outlineVariant)

        // ---- Theme
        //Text("Theme", color = cs.onBackground)
        Text("Theme", style = MaterialTheme.typography.titleMedium, color = cs.primary)
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeChange(ThemeMode.SYSTEM) }
                )
                Text("System", color = cs.onBackground)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeChange(ThemeMode.LIGHT) }
                )
                Text("Light", color = cs.onBackground)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = themeMode == ThemeMode.DARK,
                    onClick = { onThemeChange(ThemeMode.DARK) }
                )
                Text("Dark", color = cs.onBackground)
            }
        }

        // ---- About
        HorizontalDivider(Modifier, DividerDefaults.Thickness, color = cs.outlineVariant)

        Text("About", style = MaterialTheme.typography.titleMedium, color = cs.primary)

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LabeledValue(label = "App version", value = getAppVersion(ctx))
                HorizontalDivider(Modifier, DividerDefaults.Thickness, color = cs.outlineVariant)
                LabeledValue(label = "Developer", value = "Adam Ali")
            }
        }

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

private enum class AppSelectionSegment(val label: String) {
    Unlocked("Unlocked"),
    Locked("Locked")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSelectionScreen(
    appListViewModel: AppListViewModel
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val uiState by appListViewModel.uiState.collectAsState()

    var locked by remember { mutableStateOf(Prefs.getLockedApps(ctx).toSet()) }
    var selectedSegment by rememberSaveable { mutableStateOf(AppSelectionSegment.Unlocked) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val segments = remember { AppSelectionSegment.entries.toTypedArray() }

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
        if (!hasAnyApps) {
            "No launchable apps found"
        } else if (searchQuery.isBlank()) {
            if (selectedSegment == AppSelectionSegment.Locked) {
                "No locked apps yet"
            } else {
                "All of your apps are currently locked"
            }
        } else {
            "No apps match your search"
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        SingleChoiceSegmentedButtonRow {
            segments.forEachIndexed { index, segment ->
                SegmentedButton(
                    selected = selectedSegment == segment,
                    onClick = { selectedSegment = segment },
                    shape = SegmentedButtonDefaults.itemShape(index, segments.size)
                ) { Text(segment.label) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                /*SingleChoiceSegmentedButtonRow {
                    segments.forEachIndexed { index, segment ->
                        SegmentedButton(
                            selected = selectedSegment == segment,
                            onClick = { selectedSegment = segment },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = segments.size)
                        ) {
                            Text(segment.label)
                        }
                    }
                }*/

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps") },
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
            HorizontalDivider(thickness = DividerDefaults.Thickness, color = cs.outlineVariant)
        }

        when {
            uiState.isLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            visibleApps.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emptyStateMessage, color = cs.onSurfaceVariant)
                    }
                }
            }

            else -> {
                items(
                    items = visibleApps,
                    key = { it.pkg }
                ) { app ->
                    val checked = locked.contains(app.pkg)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MaterialTheme.shapes.small),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Android,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Text(
                            text = app.label,
                            color = cs.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = checked,
                            onCheckedChange = {
                                Prefs.toggleLocked(ctx, app.pkg)
                                locked = Prefs.getLockedApps(ctx).toSet()
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

                    HorizontalDivider(thickness = DividerDefaults.Thickness, color = cs.outlineVariant)
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

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

    ListItem(
        headlineContent = { Text("Change passcode") }, // or "Change password" if you kept text
        supportingContent = {
            Text(if (isSet) "Update your master passcode" else "Set a master passcode")
        },
        leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) }, // use Lock for widest compatibility
        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .semantics { contentDescription = "Change passcode" }
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
                        Toast.makeText(context, "Passcode must be $minDigits–$maxDigits digits", Toast.LENGTH_SHORT).show()
                    !newStr.all { it.isDigit() } ->
                        Toast.makeText(context, "Digits only", Toast.LENGTH_SHORT).show()
                    !new.contentEquals(confirm) ->
                        Toast.makeText(context, "Passcodes do not match", Toast.LENGTH_SHORT).show()
                    else -> {
                        val ok = repo.changePassword(current, new)
                        if (ok) {
                            isSet = true           // <- update UI state immediately
                            Toast.makeText(context, "Passcode updated", Toast.LENGTH_SHORT).show()
                            showDialog = false
                        } else {
                            Toast.makeText(context, "Current passcode incorrect", Toast.LENGTH_SHORT).show()
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
        title = { Text(if (isExisting) "Change passcode" else "Set passcode") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isExisting) PasscodeField(
                    label = "Current passcode",
                    value = current,
                    onValueChange = { current = it },
                    visible = showCurrent,
                    onToggleVisibility = { showCurrent = !showCurrent },
                    imeAction = ImeAction.Next,
                    maxLength = 8
                )

                PasscodeField(
                    label = "New passcode",
                    value = newPw,
                    onValueChange = { newPw = it },
                    visible = showNew,
                    onToggleVisibility = { showNew = !showNew },
                    imeAction = ImeAction.Next,
                    maxLength = 8
                )

                PasscodeField(
                    label = "Confirm new passcode",
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
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                current.fill('\u0000'); newPw.fill('\u0000'); confirmPw.fill('\u0000')
            }) { Text("Cancel") }
        }
    )
}

//// test after git update


@Composable
private fun PasscodeField(
    label: String,
    value: CharArray,
    onValueChange: (CharArray) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    imeAction: ImeAction,
    maxLength: Int = 6,
) {
    var text by remember(value) { mutableStateOf(String(value)) }
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
            val iconLabel = if (visible) "Hide" else "Show"
            TextButton(onClick = onToggleVisibility) { Text(iconLabel) }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
