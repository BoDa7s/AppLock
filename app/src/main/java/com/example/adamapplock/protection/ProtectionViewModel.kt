package com.example.adamapplock.protection

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.adamapplock.PermissionUtils
import com.example.adamapplock.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ProtectionUiState(
    val permissions: PermissionSnapshot = PermissionSnapshot(false, false),
    val protectionEnabled: Boolean = false,
    val serviceRunning: Boolean = false,
    val pendingEnable: Boolean = false,
    val healthCheckResult: HealthCheckResult? = null,
    val healthCheckInProgress: Boolean = false
)

class ProtectionViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val _uiState = MutableStateFlow(
        ProtectionUiState(
            permissions = PermissionSnapshot.capture(appContext),
            protectionEnabled = Prefs.isProtectionEnabled(appContext),
            serviceRunning = ProtectionDiagnostics.isServiceRunning(appContext)
        )
    )
    val uiState: StateFlow<ProtectionUiState> = _uiState

    private var lastPermissions: PermissionSnapshot = _uiState.value.permissions

    init {
        refreshPermissions(log = true)
        viewModelScope.launch {
            while (isActive) {
                refreshPermissions()
                delay(2_000)
            }
        }
    }

    fun refreshPermissions(log: Boolean = false) {
        val snapshot = PermissionSnapshot.capture(appContext)
        if (snapshot != lastPermissions || log) {
            lastPermissions = snapshot
            Log.i(
                TAG,
                "permission_state overlay=${snapshot.overlayGranted} usage=${snapshot.usageGranted}"
            )
        }
        _uiState.update { state ->
            state.copy(
                permissions = snapshot,
                serviceRunning = ProtectionDiagnostics.isServiceRunning(appContext)
            )
        }
        handlePendingEnable()
    }

    fun setProtectionEnabled(enabled: Boolean) {
        val permissions = _uiState.value.permissions
        if (enabled && !permissions.ready) {
            Log.w(TAG, "Enable requested but permissions missing; marking pending.")
            Prefs.setProtectionEnabled(appContext, false)
            _uiState.update { it.copy(protectionEnabled = false, pendingEnable = true) }
            return
        }

        if (enabled) {
            startProtection()
        } else {
            stopProtection()
        }
    }

    fun refreshServiceState() {
        _uiState.update { it.copy(serviceRunning = ProtectionDiagnostics.isServiceRunning(appContext)) }
    }

    fun runHealthCheck() {
        if (_uiState.value.healthCheckInProgress) return
        _uiState.update { it.copy(healthCheckInProgress = true) }
        viewModelScope.launch {
            val result = ProtectionDiagnostics.runHealthCheck(appContext)
            Log.i(TAG, "health_check ${result.notes}")
            _uiState.update {
                it.copy(healthCheckResult = result, healthCheckInProgress = false)
            }
        }
    }

    private fun startProtection() {
        if (!PermissionUtils.hasOverlayPermission(appContext) || !PermissionUtils.hasUsageAccess(
                appContext
            )
        ) {
            Log.w(TAG, "startProtection skipped: missing permission")
            _uiState.update { it.copy(pendingEnable = true, protectionEnabled = false) }
            Prefs.setProtectionEnabled(appContext, false)
            return
        }
        Log.i(TAG, "Starting protection service")
        Prefs.setProtectionEnabled(appContext, true)
        ProtectionController.start(appContext)
        _uiState.update {
            it.copy(
                protectionEnabled = true,
                pendingEnable = false,
                serviceRunning = true
            )
        }
    }

    private fun stopProtection() {
        Log.i(TAG, "Stopping protection service")
        Prefs.setProtectionEnabled(appContext, false)
        ProtectionController.stop(appContext)
        _uiState.update { it.copy(protectionEnabled = false, pendingEnable = false, serviceRunning = false) }
    }

    private fun handlePendingEnable() {
        val state = _uiState.value
        if (state.pendingEnable && state.permissions.ready) {
            Log.i(TAG, "Pending enable satisfied; starting protection now.")
            startProtection()
        } else if (state.protectionEnabled && !state.permissions.ready) {
            Log.w(TAG, "Permissions revoked while enabled; stopping protection.")
            stopProtection()
        } else if (state.protectionEnabled && state.permissions.ready && !state.serviceRunning) {
            Log.i(TAG, "Protection toggle is on but service not running; restarting.")
            startProtection()
        }
    }

    companion object {
        private const val TAG = "ProtectionViewModel"
    }
}
