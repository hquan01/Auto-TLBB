package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppUpdateInfo
import com.example.data.ClickConfig
import com.example.data.ClickMode
import com.example.service.AutoClickService
import com.example.service.FloatingViewService
import com.example.updater.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * UI State for GitHub In-App Updates
 */
sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateUiState
    object UpToDate : UpdateUiState
    data class Downloading(val progress: Int, val updateInfo: AppUpdateInfo) : UpdateUiState
    data class DownloadComplete(val apkFile: File, val updateInfo: AppUpdateInfo) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

/**
 * Permissions status state
 */
data class PermissionsState(
    val isAccessibilityGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isInstallUnknownAppsGranted: Boolean = false,
    val isNotificationGranted: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val prefs = context.getSharedPreferences("autoclicker_prefs", Context.MODE_PRIVATE)
    private val updateManager = UpdateManager(context)

    // Permissions State
    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

    // Auto Clicker Configuration
    private val _clickConfig = MutableStateFlow(
        ClickConfig(
            mode = ClickMode.SINGLE_POINT,
            intervalMs = prefs.getLong("pref_interval", 100L),
            clickDurationMs = 40L,
            repeatCount = prefs.getInt("pref_repeat", 0)
        )
    )
    val clickConfig: StateFlow<ClickConfig> = _clickConfig.asStateFlow()

    // Overlay active state
    val isOverlayActive: StateFlow<Boolean> = FloatingViewService.isOverlayActive
    val isClicking: StateFlow<Boolean> = FloatingViewService.isClicking
    val totalClicks: StateFlow<Long> = FloatingViewService.totalClicks

    // Update State
    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    // GitHub repository or version.json URL
    private val _githubUrl = MutableStateFlow(
        prefs.getString("pref_github_url", UpdateManager.DEFAULT_GITHUB_VERSION_URL)
            ?: UpdateManager.DEFAULT_GITHUB_VERSION_URL
    )
    val githubUrl: StateFlow<String> = _githubUrl.asStateFlow()

    // Auto check on start
    private val _autoCheckUpdates = MutableStateFlow(
        prefs.getBoolean("pref_auto_check_updates", true)
    )
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    init {
        refreshPermissions()
        // Automatically check for GitHub updates on app launch if enabled
        if (_autoCheckUpdates.value) {
            checkUpdateSilentlyOnLaunch()
        }
    }

    /**
     * Refreshes all system permission statuses
     */
    fun refreshPermissions() {
        val isAccessibility = AutoClickService.isServiceRunning.value || checkAccessibilitySetting()
        val isOverlay = Settings.canDrawOverlays(context)
        val isInstallGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

        _permissionsState.value = PermissionsState(
            isAccessibilityGranted = isAccessibility,
            isOverlayGranted = isOverlay,
            isInstallUnknownAppsGranted = isInstallGranted,
            isNotificationGranted = true
        )
    }

    private fun checkAccessibilitySetting(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Updates Auto Clicker Config
     */
    fun updateInterval(intervalMs: Long) {
        val newConfig = _clickConfig.value.copy(intervalMs = intervalMs.coerceAtLeast(10L))
        _clickConfig.value = newConfig
        prefs.edit().putLong("pref_interval", newConfig.intervalMs).apply()
    }

    fun updateRepeatCount(count: Int) {
        val newConfig = _clickConfig.value.copy(repeatCount = count.coerceAtLeast(0))
        _clickConfig.value = newConfig
        prefs.edit().putInt("pref_repeat", newConfig.repeatCount).apply()
    }

    fun updateClickMode(mode: ClickMode) {
        _clickConfig.value = _clickConfig.value.copy(mode = mode)
    }

    /**
     * Starts the Floating Overlay Service over Game
     */
    fun startFloatingOverlay() {
        val cfg = _clickConfig.value
        FloatingViewService.start(
            context = context,
            intervalMs = cfg.intervalMs,
            durationMs = cfg.clickDurationMs,
            repeatCount = cfg.repeatCount,
            mode = cfg.mode
        )
    }

    /**
     * Stops the Floating Overlay Service
     */
    fun stopFloatingOverlay() {
        FloatingViewService.stop(context)
    }

    /**
     * Sets custom GitHub JSON / Repo URL
     */
    fun setGithubUrl(url: String) {
        val clean = url.trim()
        _githubUrl.value = clean
        prefs.edit().putString("pref_github_url", clean).apply()
    }

    fun toggleAutoCheckUpdates(enabled: Boolean) {
        _autoCheckUpdates.value = enabled
        prefs.edit().putBoolean("pref_auto_check_updates", enabled).apply()
    }

    /**
     * Silently checks GitHub on startup and only shows alert/modal if an update is found
     */
    fun checkUpdateSilentlyOnLaunch() {
        viewModelScope.launch {
            val result = updateManager.checkForUpdate(_githubUrl.value)
            result.onSuccess { updateInfo ->
                if (updateInfo != null) {
                    _updateState.value = UpdateUiState.UpdateAvailable(updateInfo)
                }
            }
        }
    }

    /**
     * Checks for update from GitHub manually with loading indicators
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Checking
            val result = updateManager.checkForUpdate(_githubUrl.value)
            result.onSuccess { updateInfo ->
                if (updateInfo != null) {
                    _updateState.value = UpdateUiState.UpdateAvailable(updateInfo)
                } else {
                    _updateState.value = UpdateUiState.UpToDate
                }
            }.onFailure { err ->
                _updateState.value = UpdateUiState.Error(
                    err.localizedMessage ?: "Không thể kết nối đến GitHub để kiểm tra cập nhật"
                )
            }
        }
    }

    /**
     * 1-Click Update Flow: Downloads APK from GitHub release and immediately launches installer
     */
    fun downloadAndInstallUpdate(updateInfo: AppUpdateInfo) {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading(0, updateInfo)
            val downloadResult = updateManager.downloadApkDirect(updateInfo.apkUrl) { progress ->
                _updateState.value = UpdateUiState.Downloading(progress, updateInfo)
            }

            downloadResult.onSuccess { apkFile ->
                _updateState.value = UpdateUiState.DownloadComplete(apkFile, updateInfo)
                installApk(apkFile)
            }.onFailure { error ->
                _updateState.value = UpdateUiState.Error(
                    "Lỗi khi tải file APK từ GitHub: ${error.localizedMessage}"
                )
            }
        }
    }

    fun installApk(apkFile: File) {
        updateManager.installApk(apkFile)
    }

    fun dismissUpdateDialog() {
        _updateState.value = UpdateUiState.Idle
    }

    fun getCurrentVersionName(): String = updateManager.getCurrentAppVersionName()
    fun getCurrentVersionCode(): Long = updateManager.getCurrentAppVersionCode()
}
