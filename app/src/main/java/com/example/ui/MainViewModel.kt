package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppUpdateInfo
import com.example.data.AutoClickScript
import com.example.data.ScriptCategory
import com.example.data.ScriptPoint
import com.example.data.ScriptRepository
import com.example.service.AutoClickService
import com.example.service.FloatingViewService
import com.example.updater.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

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
    private val scriptRepository = ScriptRepository.getInstance(context)

    // Permissions State
    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

    // Filter Category
    private val _selectedCategoryFilter = MutableStateFlow<ScriptCategory?>(null) // null = Tất cả
    val selectedCategoryFilter: StateFlow<ScriptCategory?> = _selectedCategoryFilter.asStateFlow()

    // Scripts List & Active Script
    val scriptsList: StateFlow<List<AutoClickScript>> = scriptRepository.scriptsFlow
    val activeScript: StateFlow<AutoClickScript> = scriptRepository.activeScriptFlow

    // Script being edited in modal dialog
    private val _editingScript = MutableStateFlow<AutoClickScript?>(null)
    val editingScript: StateFlow<AutoClickScript?> = _editingScript.asStateFlow()

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
        if (_autoCheckUpdates.value) {
            checkUpdateSilentlyOnLaunch()
        }
    }

    fun setCategoryFilter(category: ScriptCategory?) {
        _selectedCategoryFilter.value = category
    }

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

    // ==========================================
    // SCRIPT MANAGEMENT & EDITING
    // ==========================================

    fun selectActiveScript(script: AutoClickScript) {
        scriptRepository.setActiveScript(script)
        if (isOverlayActive.value) {
            FloatingViewService.start(context, script.id)
        }
    }

    fun openScriptEditor(script: AutoClickScript?, defaultCategory: ScriptCategory = ScriptCategory.FARMING) {
        if (script == null) {
            _editingScript.value = AutoClickScript(
                id = UUID.randomUUID().toString(),
                name = "Kịch bản ${defaultCategory.iconEmoji} mới",
                category = defaultCategory,
                repeatCount = 0,
                loopDelayMs = 500L,
                stopTimerSeconds = 0L,
                points = listOf(
                    ScriptPoint(1, "Điểm 1", 300f, 600f, delayBeforeMs = 0L, clickDurationMs = 40L, delayAfterMs = 200L),
                    ScriptPoint(2, "Điểm 2", 500f, 700f, delayBeforeMs = 50L, clickDurationMs = 40L, delayAfterMs = 300L)
                )
            )
        } else {
            _editingScript.value = script.copy(
                points = script.points.map { it.copy() }
            )
        }
    }

    fun closeScriptEditor() {
        _editingScript.value = null
    }

    fun updateEditingScriptName(name: String) {
        _editingScript.value = _editingScript.value?.copy(name = name)
    }

    fun updateEditingScriptCategory(category: ScriptCategory) {
        _editingScript.value = _editingScript.value?.copy(category = category)
    }

    fun updateEditingScriptRepeat(repeatCount: Int) {
        _editingScript.value = _editingScript.value?.copy(repeatCount = repeatCount.coerceAtLeast(0))
    }

    fun updateEditingScriptLoopDelay(loopDelayMs: Long) {
        _editingScript.value = _editingScript.value?.copy(loopDelayMs = loopDelayMs.coerceAtLeast(0L))
    }

    fun updateEditingScriptStopTimer(seconds: Long) {
        _editingScript.value = _editingScript.value?.copy(stopTimerSeconds = seconds.coerceAtLeast(0L))
    }

    fun updateEditingScriptPoint(pointIndex: Int, updatedPoint: ScriptPoint) {
        val current = _editingScript.value ?: return
        val currentPoints = current.points.toMutableList()
        if (pointIndex in currentPoints.indices) {
            currentPoints[pointIndex] = updatedPoint
            _editingScript.value = current.copy(points = currentPoints)
        }
    }

    fun addPointToEditingScript() {
        val current = _editingScript.value ?: return
        val currentPoints = current.points.toMutableList()
        val nextId = currentPoints.size + 1
        currentPoints.add(
            ScriptPoint(
                id = nextId,
                name = "Điểm $nextId",
                x = 350f + (nextId * 30),
                y = 550f + (nextId * 40),
                delayBeforeMs = 50L,
                clickDurationMs = 40L,
                delayAfterMs = 200L
            )
        )
        _editingScript.value = current.copy(points = currentPoints)
    }

    fun removePointFromEditingScript(pointIndex: Int) {
        val current = _editingScript.value ?: return
        val currentPoints = current.points.toMutableList()
        if (currentPoints.size > 1 && pointIndex in currentPoints.indices) {
            currentPoints.removeAt(pointIndex)
            val reIndexed = currentPoints.mapIndexed { idx, p ->
                p.copy(id = idx + 1)
            }
            _editingScript.value = current.copy(points = reIndexed)
        }
    }

    fun saveEditingScript() {
        val script = _editingScript.value ?: return
        viewModelScope.launch {
            scriptRepository.saveOrUpdateScript(script)
            scriptRepository.setActiveScript(script)
            _editingScript.value = null

            if (isOverlayActive.value) {
                FloatingViewService.start(context, script.id)
            }
        }
    }

    fun deleteScript(scriptId: String) {
        viewModelScope.launch {
            scriptRepository.deleteScript(scriptId)
        }
    }

    fun startFloatingOverlay() {
        FloatingViewService.start(
            context = context,
            scriptId = activeScript.value.id
        )
    }

    fun stopFloatingOverlay() {
        FloatingViewService.stop(context)
    }

    fun setGithubUrl(url: String) {
        val clean = url.trim()
        _githubUrl.value = clean
        prefs.edit().putString("pref_github_url", clean).apply()
    }

    fun toggleAutoCheckUpdates(enabled: Boolean) {
        _autoCheckUpdates.value = enabled
        prefs.edit().putBoolean("pref_auto_check_updates", enabled).apply()
    }

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
