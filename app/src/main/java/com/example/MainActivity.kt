package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AppUpdateInfo
import com.example.data.AutoClickScript
import com.example.data.ScriptCategory
import com.example.data.ScriptPoint
import com.example.ui.MainViewModel
import com.example.ui.UpdateUiState
import com.example.ui.theme.BrightCyan
import com.example.ui.theme.DarkNavyBg
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavyCardBorder
import com.example.ui.theme.DeepCyan
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.updater.UpdateManager
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.getBooleanExtra(UpdateManager.EXTRA_AUTO_UPDATE, false) == true) {
            viewModel.checkForUpdates()
        }

        setContent {
            MyApplicationTheme {
                AutoClickerMainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(UpdateManager.EXTRA_AUTO_UPDATE, false)) {
            viewModel.checkForUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }
}

@Composable
fun AutoClickerMainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsState by viewModel.permissionsState.collectAsState()
    val scriptsList by viewModel.scriptsList.collectAsState()
    val activeScript by viewModel.activeScript.collectAsState()
    val editingScript by viewModel.editingScript.collectAsState()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsState()
    val isOverlayActive by viewModel.isOverlayActive.collectAsState()
    val isClicking by viewModel.isClicking.collectAsState()
    val totalClicks by viewModel.totalClicks.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val githubUrl by viewModel.githubUrl.collectAsState()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsState()

    var showJsonHelpDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkNavyBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Section
            HeaderSection(
                versionName = viewModel.getCurrentVersionName(),
                versionCode = viewModel.getCurrentVersionCode(),
                isOverlayActive = isOverlayActive,
                isClicking = isClicking,
                totalClicks = totalClicks
            )

            // 1. Permissions Card
            PermissionsCard(
                isAccessibilityGranted = permissionsState.isAccessibilityGranted,
                isOverlayGranted = permissionsState.isOverlayGranted,
                isInstallGranted = permissionsState.isInstallUnknownAppsGranted,
                onOpenAccessibility = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                onOpenOverlay = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                onOpenInstallSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }
            )

            // 2. SCRIPT MANAGEMENT & MULTI-AUTO CATEGORIES CARD
            ScriptManagerCard(
                scriptsList = scriptsList,
                activeScript = activeScript,
                selectedCategoryFilter = selectedCategoryFilter,
                isOverlayActive = isOverlayActive,
                onSelectCategoryFilter = { viewModel.setCategoryFilter(it) },
                onSelectScript = { viewModel.selectActiveScript(it) },
                onEditScript = { viewModel.openScriptEditor(it) },
                onCreateNewScript = {
                    viewModel.openScriptEditor(null, selectedCategoryFilter ?: ScriptCategory.FARMING)
                },
                onDeleteScript = { viewModel.deleteScript(it) },
                onToggleOverlay = {
                    if (isOverlayActive) {
                        viewModel.stopFloatingOverlay()
                        Toast.makeText(context, "Đã tắt Bảng điều khiển nổi", Toast.LENGTH_SHORT).show()
                    } else {
                        if (!permissionsState.isAccessibilityGranted) {
                            Toast.makeText(context, "⚠️ Vui lòng cấp quyền Trợ năng (Accessibility) trước!", Toast.LENGTH_LONG).show()
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            return@ScriptManagerCard
                        }
                        if (!permissionsState.isOverlayGranted) {
                            Toast.makeText(context, "⚠️ Vui lòng cấp quyền Cửa sổ nổi (Overlay) trước!", Toast.LENGTH_LONG).show()
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            return@ScriptManagerCard
                        }
                        viewModel.startFloatingOverlay()
                        Toast.makeText(context, "Đã bật Bảng điều khiển nổi cho '${activeScript.name}'!", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 3. GitHub Auto-Updater Card
            GitHubUpdateCard(
                githubUrl = githubUrl,
                autoCheckUpdates = autoCheckUpdates,
                updateState = updateState,
                onUrlChanged = { viewModel.setGithubUrl(it) },
                onToggleAutoCheck = { viewModel.toggleAutoCheckUpdates(it) },
                onCheckUpdate = { viewModel.checkForUpdates() },
                onShowJsonHelp = { showJsonHelpDialog = true }
            )

            // 4. Instructions Card
            InstructionsCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Full Advanced Script Editor Dialog
    editingScript?.let { script ->
        ScriptEditorFullDialog(
            script = script,
            onDismiss = { viewModel.closeScriptEditor() },
            onUpdateName = { viewModel.updateEditingScriptName(it) },
            onUpdateCategory = { viewModel.updateEditingScriptCategory(it) },
            onUpdateRepeat = { viewModel.updateEditingScriptRepeat(it) },
            onUpdateLoopDelay = { viewModel.updateEditingScriptLoopDelay(it) },
            onUpdateStopTimer = { viewModel.updateEditingScriptStopTimer(it) },
            onUpdatePoint = { idx, pt -> viewModel.updateEditingScriptPoint(idx, pt) },
            onAddPoint = { viewModel.addPointToEditingScript() },
            onRemovePoint = { viewModel.removePointFromEditingScript(it) },
            onSave = { viewModel.saveEditingScript() }
        )
    }

    // Update Result Dialogs
    UpdateStateDialogs(
        updateState = updateState,
        onDismiss = { viewModel.dismissUpdateDialog() },
        onDownload = { info -> viewModel.downloadAndInstallUpdate(info) },
        onInstall = { file -> viewModel.installApk(file) }
    )

    // Help Dialog for version.json schema
    if (showJsonHelpDialog) {
        GitHubJsonHelpDialog(onDismiss = { showJsonHelpDialog = false })
    }
}

// ==========================================
// 1. HEADER
// ==========================================

@Composable
fun HeaderSection(
    versionName: String,
    versionCode: Long,
    isOverlayActive: Boolean,
    isClicking: Boolean,
    totalClicks: Long
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        border = BorderStroke(1.dp, DarkNavyCardBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(listOf(DeepCyan, ElectricCyan))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Auto Clicker",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Auto Clicker Đa Thể Loại",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "🌾 Trồng trọt • 🎣 Câu cá • ⚔️ Đánh quái • ⛏️ Đào khoáng",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = BrightCyan,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Surface(
                    color = SurfaceHighlight,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DarkNavyCardBorder)
                ) {
                    Text(
                        text = "v$versionName ($versionCode)",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isClicking) Color(0x33EF4444)
                        else if (isOverlayActive) Color(0x3310B981)
                        else Color(0x221E293B)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isClicking) ErrorRed
                                else if (isOverlayActive) EmeraldGreen
                                else TextMuted
                            )
                    )
                    Text(
                        text = when {
                            isClicking -> "Đang tự động thực thi kịch bản..."
                            isOverlayActive -> "Bảng điều khiển nổi đang chạy đè màn hình"
                            else -> "Trạng thái: Chưa kích hoạt"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = when {
                                isClicking -> ErrorRed
                                isOverlayActive -> EmeraldGreen
                                else -> TextSecondary
                            }
                        )
                    )
                }

                if (totalClicks > 0) {
                    Text(
                        text = "$totalClicks clicks",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. PERMISSIONS CARD
// ==========================================

@Composable
fun PermissionsCard(
    isAccessibilityGranted: Boolean,
    isOverlayGranted: Boolean,
    isInstallGranted: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenInstallSettings: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        border = BorderStroke(1.dp, DarkNavyCardBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Quyền Hệ Thống Bắt Buộc",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }

            PermissionItem(
                title = "1. Dịch vụ Trợ năng (Accessibility)",
                description = "Bắt buộc để ứng dụng tự động thực hiện thao tác chạm (dispatchGesture) đè lên Game.",
                isGranted = isAccessibilityGranted,
                actionLabel = if (isAccessibilityGranted) "Đã cấp quyền" else "Cấp quyền Trợ năng",
                onClick = onOpenAccessibility,
                testTag = "btn_grant_accessibility"
            )

            Divider(color = DarkNavyCardBorder)

            PermissionItem(
                title = "2. Vẽ đè lên ứng dụng khác (Overlay)",
                description = "Bắt buộc để hiển thị nút Bắt đầu / Tạm dừng và các điểm ngắm kịch bản trên Game.",
                isGranted = isOverlayGranted,
                actionLabel = if (isOverlayGranted) "Đã cấp quyền" else "Cấp quyền Cửa sổ nổi",
                onClick = onOpenOverlay,
                testTag = "btn_grant_overlay"
            )

            Divider(color = DarkNavyCardBorder)

            PermissionItem(
                title = "3. Cài đặt bản cập nhật APK (GitHub Update)",
                description = "Cho phép app tự động tải file APK phiên bản mới từ GitHub về và cài đặt trực tiếp.",
                isGranted = isInstallGranted,
                actionLabel = if (isInstallGranted) "Đã cấp quyền" else "Cấp quyền Cài đặt APK",
                onClick = onOpenInstallSettings,
                testTag = "btn_grant_install"
            )
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) EmeraldGreen else WarningAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isGranted) "ĐÃ BẬT" else "CHƯA BẬT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) EmeraldGreen else WarningAmber
                    )
                )
            }
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                lineHeight = 18.sp
            )
        )

        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.dp,
                if (isGranted) EmeraldGreen.copy(alpha = 0.4f) else ElectricCyan
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isGranted) EmeraldGreen else ElectricCyan,
                containerColor = if (isGranted) EmeraldGreen.copy(alpha = 0.1f) else ElectricCyan.copy(alpha = 0.1f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        ) {
            Text(
                text = actionLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

// ==========================================
// 3. SCRIPT MANAGEMENT CARD (MULTI-CATEGORIES)
// ==========================================

@Composable
fun ScriptManagerCard(
    scriptsList: List<AutoClickScript>,
    activeScript: AutoClickScript,
    selectedCategoryFilter: ScriptCategory?,
    isOverlayActive: Boolean,
    onSelectCategoryFilter: (ScriptCategory?) -> Unit,
    onSelectScript: (AutoClickScript) -> Unit,
    onEditScript: (AutoClickScript) -> Unit,
    onCreateNewScript: () -> Unit,
    onDeleteScript: (String) -> Unit,
    onToggleOverlay: () -> Unit
) {
    val filteredScripts = if (selectedCategoryFilter == null) {
        scriptsList
    } else {
        scriptsList.filter { it.category == selectedCategoryFilter }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        border = BorderStroke(1.dp, DarkNavyCardBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Kho Kịch Bản Auto Game",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                TextButton(onClick = onCreateNewScript) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = BrightCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Thêm mới", color = BrightCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onToggleOverlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOverlayActive) ErrorRed else EmeraldGreen
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_toggle_overlay")
            ) {
                Icon(
                    imageVector = if (isOverlayActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isOverlayActive) "TẮT BẢNG ĐIỀU KHIỂN NỔI" else "BẬT OVERLAY (${activeScript.name.take(20)})",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // All Filter
                val isAllSelected = selectedCategoryFilter == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isAllSelected) ElectricCyan.copy(alpha = 0.25f) else SurfaceHighlight)
                        .border(1.dp, if (isAllSelected) ElectricCyan else DarkNavyCardBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelectCategoryFilter(null) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⭐ Tất cả (${scriptsList.size})",
                        fontSize = 11.sp,
                        color = if (isAllSelected) ElectricCyan else TextSecondary,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Category Chips
                ScriptCategory.values().forEach { cat ->
                    val isCatSelected = selectedCategoryFilter == cat
                    val count = scriptsList.count { it.category == cat }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCatSelected) BrightCyan.copy(alpha = 0.25f) else SurfaceHighlight)
                            .border(1.dp, if (isCatSelected) BrightCyan else DarkNavyCardBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelectCategoryFilter(cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${cat.iconEmoji} ${cat.displayName} ($count)",
                            fontSize = 11.sp,
                            color = if (isCatSelected) BrightCyan else TextSecondary,
                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Divider(color = DarkNavyCardBorder)

            if (filteredScripts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chưa có kịch bản trong thể loại này. Bấm '+ Thêm mới' để tạo!",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredScripts.forEach { script ->
                        val isSelected = script.id == activeScript.id
                        val repeatText = if (script.repeatCount == 0) "Vô hạn" else "${script.repeatCount} vòng"
                        val timerText = if (script.stopTimerSeconds > 0) " • Tự dừng: ${script.stopTimerSeconds / 60}p" else ""

                        Surface(
                            color = if (isSelected) SurfaceHighlight else DarkNavyBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) ElectricCyan else DarkNavyCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectScript(script) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = script.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) ElectricCyan else TextPrimary
                                            )
                                        )
                                        if (isSelected) {
                                            Surface(
                                                color = ElectricCyan.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "ĐANG CHỌN",
                                                    color = ElectricCyan,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${script.category.iconEmoji} ${script.points.size} điểm • Lặp: $repeatText • Nghỉ: ${script.loopDelayMs}ms$timerText",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onEditScript(script) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Chỉnh sửa kịch bản",
                                            tint = BrightCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (scriptsList.size > 1) {
                                        IconButton(
                                            onClick = { onDeleteScript(script.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Xóa kịch bản",
                                                tint = ErrorRed.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { onEditScript(activeScript) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BrightCyan),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = BrightCyan,
                    containerColor = BrightCyan.copy(alpha = 0.08f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chỉnh Sửa Toàn Diện '${activeScript.name.take(22)}'", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 4. ADVANCED SCRIPT EDITOR FULL DIALOG
// ==========================================

@Composable
fun ScriptEditorFullDialog(
    script: AutoClickScript,
    onDismiss: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateCategory: (ScriptCategory) -> Unit,
    onUpdateRepeat: (Int) -> Unit,
    onUpdateLoopDelay: (Long) -> Unit,
    onUpdateStopTimer: (Long) -> Unit,
    onUpdatePoint: (Int, ScriptPoint) -> Unit,
    onAddPoint: () -> Unit,
    onRemovePoint: (Int) -> Unit,
    onSave: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DarkNavyBg,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, ElectricCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Trình Chỉnh Sửa Kịch Bản",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondary)
                    }
                }

                Divider(color = DarkNavyCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Script Name Field
                    OutlinedTextField(
                        value = script.name,
                        onValueChange = onUpdateName,
                        label = { Text("Tên Kịch Bản") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = DarkNavyCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 1B. Thể Loại Auto (Category)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Category, contentDescription = null, tint = BrightCyan, modifier = Modifier.size(16.dp))
                            Text("Thể loại Auto:", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ScriptCategory.values().forEach { cat ->
                                val isSelected = script.category == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) BrightCyan.copy(alpha = 0.25f) else SurfaceHighlight)
                                        .border(1.dp, if (isSelected) BrightCyan else DarkNavyCardBorder, RoundedCornerShape(8.dp))
                                        .clickable { onUpdateCategory(cat) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${cat.iconEmoji} ${cat.displayName}",
                                        fontSize = 11.sp,
                                        color = if (isSelected) BrightCyan else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // 2. LOOP REPEAT & STOP TIMER & LOOP DELAY CARD
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DarkNavyCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Loop, contentDescription = null, tint = BrightCyan, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Cài Đặt Số Vòng Lặp & Thời Gian Nghỉ",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = BrightCyan
                                    )
                                )
                            }

                            // 2A. SỐ VÒNG LẶP (REPEAT COUNT)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Số vòng lặp (0 = Vô hạn):",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                                    )
                                    Text(
                                        text = if (script.repeatCount == 0) "Vô hạn" else "${script.repeatCount} vòng",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldGreen
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(0 to "Vô hạn", 5 to "5", 20 to "20", 50 to "50", 100 to "100", 500 to "500", 1000 to "1000").forEach { (count, label) ->
                                        val isSelected = script.repeatCount == count
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isSelected) EmeraldGreen.copy(alpha = 0.25f)
                                                    else SurfaceHighlight
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) EmeraldGreen else DarkNavyCardBorder,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { onUpdateRepeat(count) }
                                                .padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) EmeraldGreen else TextSecondary,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // 2B. THỜI GIAN NGHỈ GIỮA CÁC VÒNG (LOOP DELAY)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Thời gian nghỉ giữa các vòng:",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                                    )
                                    Text(
                                        text = "${script.loopDelayMs}ms",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = ElectricCyan
                                        )
                                    )
                                }

                                Slider(
                                    value = script.loopDelayMs.toFloat(),
                                    onValueChange = { onUpdateLoopDelay(it.toLong()) },
                                    valueRange = 0f..5000f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ElectricCyan,
                                        activeTrackColor = ElectricCyan,
                                        inactiveTrackColor = SurfaceHighlight
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(0L to "0ms", 100L to "100ms", 500L to "500ms", 1000L to "1s", 2000L to "2s", 5000L to "5s").forEach { (ms, label) ->
                                        val isSelected = script.loopDelayMs == ms
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isSelected) ElectricCyan.copy(alpha = 0.25f)
                                                    else SurfaceHighlight
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) ElectricCyan else DarkNavyCardBorder,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { onUpdateLoopDelay(ms) }
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                color = if (isSelected) ElectricCyan else TextSecondary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            // 2C. HẸN GIỜ TỰ ĐỘNG DỪNG (STOP TIMER)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val currentMinutes = script.stopTimerSeconds / 60
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.HourglassTop, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "Hẹn giờ tự động dừng:",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                                        )
                                    }
                                    Text(
                                        text = if (script.stopTimerSeconds == 0L) "Chạy liên tục" else "Tự dừng sau $currentMinutes phút",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = WarningAmber
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(0L to "Tắt", 1L to "1p", 5L to "5p", 15L to "15p", 30L to "30p", 60L to "1h").forEach { (mins, label) ->
                                        val isSelected = currentMinutes == mins
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isSelected) WarningAmber.copy(alpha = 0.25f)
                                                    else SurfaceHighlight
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) WarningAmber else DarkNavyCardBorder,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { onUpdateStopTimer(mins * 60L) }
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                color = if (isSelected) WarningAmber else TextSecondary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. TARGET POINTS LIST & TIMINGS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Danh sách Điểm Chạm (${script.points.size} điểm):",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )

                        Button(
                            onClick = onAddPoint,
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighlight),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ElectricCyan)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Thêm Điểm", color = ElectricCyan, fontSize = 12.sp)
                        }
                    }

                    script.points.forEachIndexed { index, point ->
                        PointTimingEditorCard(
                            point = point,
                            canDelete = script.points.size > 1,
                            onUpdate = { updatedPoint -> onUpdatePoint(index, updatedPoint) },
                            onDelete = { onRemovePoint(index) }
                        )
                    }
                }

                Divider(color = DarkNavyCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy", color = TextSecondary)
                    }

                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(2f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lưu Kịch Bản Ngay", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PointTimingEditorCard(
    point: ScriptPoint,
    canDelete: Boolean,
    onUpdate: (ScriptPoint) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        border = BorderStroke(1.dp, DarkNavyCardBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.2f))
                            .border(1.dp, ElectricCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${point.id}",
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = point.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // 1. Delay After Click
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Thời gian chờ sau khi bấm (delay sau):", fontSize = 12.sp, color = TextSecondary)
                    Text("${point.delayAfterMs}ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrightCyan)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(20L, 50L, 100L, 200L, 500L, 1000L).forEach { speed ->
                        val isSelected = point.delayAfterMs == speed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) BrightCyan.copy(alpha = 0.25f)
                                    else SurfaceHighlight
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) BrightCyan else DarkNavyCardBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onUpdate(point.copy(delayAfterMs = speed)) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${speed}ms",
                                fontSize = 10.sp,
                                color = if (isSelected) BrightCyan else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // 2. Click Duration
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Thời gian giữ click (touch duration):", fontSize = 12.sp, color = TextSecondary)
                    Text("${point.clickDurationMs}ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarningAmber)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(20L to "20ms", 40L to "40ms", 100L to "100ms", 500L to "500ms").forEach { (dur, label) ->
                        val isSelected = point.clickDurationMs == dur
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) WarningAmber.copy(alpha = 0.25f)
                                    else SurfaceHighlight
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) WarningAmber else DarkNavyCardBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onUpdate(point.copy(clickDurationMs = dur)) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                color = if (isSelected) WarningAmber else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // 3. Delay Before Click
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Thời gian chờ trước khi bấm (delay trước):", fontSize = 12.sp, color = TextSecondary)
                    Text("${point.delayBeforeMs}ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0L to "0ms", 50L to "50ms", 100L to "100ms", 200L to "200ms", 500L to "500ms").forEach { (speed, label) ->
                        val isSelected = point.delayBeforeMs == speed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) EmeraldGreen.copy(alpha = 0.25f)
                                    else SurfaceHighlight
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) EmeraldGreen else DarkNavyCardBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onUpdate(point.copy(delayBeforeMs = speed)) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                color = if (isSelected) EmeraldGreen else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. GITHUB UPDATE CARD
// ==========================================

@Composable
fun GitHubUpdateCard(
    githubUrl: String,
    autoCheckUpdates: Boolean,
    updateState: UpdateUiState,
    onUrlChanged: (String) -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onShowJsonHelp: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        border = BorderStroke(1.dp, DarkNavyCardBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = BrightCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Tự Động Cập Nhật (GitHub)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                TextButton(onClick = onShowJsonHelp) {
                    Text("Cấu trúc JSON", color = BrightCyan, fontSize = 12.sp)
                }
            }

            Surface(
                color = SurfaceHighlight,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkNavyCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Mỗi khi bạn đẩy thêm tính năng hoặc kịch bản Auto mới lên GitHub, app sẽ tự thông báo và 1-chạm nâng cấp ngay!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextPrimary,
                            lineHeight = 17.sp
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceHighlight.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Tự động kiểm tra khi mở app",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Tự động quét GitHub và hiện thông báo khi có bản mới",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Switch(
                    checked = autoCheckUpdates,
                    onCheckedChange = onToggleAutoCheck,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = EmeraldGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfaceHighlight
                    )
                )
            }

            OutlinedTextField(
                value = githubUrl,
                onValueChange = onUrlChanged,
                label = { Text("Link GitHub Repo hoặc raw version.json") },
                placeholder = { Text("https://github.com/user/repo hoặc link raw...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = DarkNavyCardBorder,
                    focusedLabelColor = ElectricCyan,
                    cursorColor = ElectricCyan,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_github_url")
            )

            Button(
                onClick = onCheckUpdate,
                enabled = updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepCyan
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_check_update")
            ) {
                if (updateState is UpdateUiState.Checking) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Đang kiểm tra bản mới trên GitHub...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kiểm Tra Cập Nhật Ngay",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. INSTRUCTIONS CARD
// ==========================================

@Composable
fun InstructionsCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        border = BorderStroke(1.dp, DarkNavyCardBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "📖 Hệ Thống Auto Mở Rộng Cho Nhiều Game",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            StepItem(
                step = "1",
                title = "🌾 Auto Nông Trại & Trồng Trọt",
                desc = "Gieo hạt liên hoàn theo ô, tưới nước vườn đất, bón phân và thu hoạch nông sản tự động theo chu kỳ thời gian."
            )
            StepItem(
                step = "2",
                title = "🎣 Auto Câu Cá & Kéo Phao",
                desc = "Thả mồi câu, nhấp giữ giằng cá khi cắn câu và kéo cá lên bờ tự động."
            )
            StepItem(
                step = "3",
                title = "⚔️ Auto Đánh Quái & Combo Kỹ Năng (RPG)",
                desc = "Tự động kích hoạt chuỗi chiêu thức AOE/Stun, kết hợp bơm bình HP/Mana định kỳ."
            )
            StepItem(
                step = "4",
                title = "⛏️ Auto Khai Thác & ⚡ Clicker Siêu Tốc",
                desc = "Đập quặng khoáng, đổi công cụ hoặc tap siêu tốc phá giáp với tốc độ hàng chục click mỗi giây."
            )
            StepItem(
                step = "5",
                title = "🚀 Tự Động Cập Nhật Kịch Bản Mới Qua GitHub",
                desc = "Khi bạn viết thêm kịch bản mới và push lên GitHub, ứng dụng của người dùng sẽ tự động nhận diện và cập nhật ngay lập tức!"
            )
        }
    }
}

@Composable
fun StepItem(step: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(SurfaceHighlight)
                .border(1.dp, ElectricCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            )
        }
    }
}

// ==========================================
// 7. UPDATE DIALOGS & ALERTS
// ==========================================

@Composable
fun UpdateStateDialogs(
    updateState: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: (AppUpdateInfo) -> Unit,
    onInstall: (File) -> Unit
) {
    when (updateState) {
        is UpdateUiState.UpdateAvailable -> {
            val info = updateState.updateInfo
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkNavyCard,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = EmeraldGreen
                        )
                        Text(
                            text = "Có tính năng mới trên GitHub!",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Phiên bản mới: v${info.versionName} (Build ${info.versionCode})",
                            color = BrightCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (info.fileSize != null) {
                            Text(
                                text = "Dung lượng: ${info.fileSize}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "Tính năng mới được cập nhật:\n${info.releaseNotes}",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onDownload(info) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cập Nhật Ngay", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Để sau", color = TextSecondary)
                    }
                }
            )
        }

        is UpdateUiState.Downloading -> {
            val progress = updateState.progress
            AlertDialog(
                onDismissRequest = { },
                containerColor = DarkNavyCard,
                title = {
                    Text(
                        text = "Đang tải tính năng mới...",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            color = ElectricCyan,
                            trackColor = SurfaceHighlight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "Tiến độ tải: $progress%",
                            color = BrightCyan,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateUiState.DownloadComplete -> {
            val file = updateState.apkFile
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkNavyCard,
                title = {
                    Text("Tải hoàn tất!", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "File APK đã được tải về. Nhấn Cài Đặt để cập nhật ngay các tính năng mới.",
                        color = TextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onInstall(file) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cài Đặt Ngay", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng", color = TextSecondary)
                    }
                }
            )
        }

        is UpdateUiState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkNavyCard,
                title = {
                    Text("Đã là bản mới nhất", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "Ứng dụng của bạn đã có đầy đủ tất cả các tính năng mới nhất từ GitHub.",
                        color = TextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = DeepCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Đồng ý")
                    }
                }
            )
        }

        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkNavyCard,
                title = {
                    Text("Thông báo cập nhật", color = WarningAmber, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(updateState.message, color = TextPrimary)
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighlight),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Đóng", color = TextPrimary)
                    }
                }
            )
        }

        else -> {}
    }
}

// ==========================================
// 8. GITHUB JSON HELP DIALOG
// ==========================================

@Composable
fun GitHubJsonHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sampleJson = """
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "apkUrl": "https://github.com/your-username/your-repo/releases/download/v1.1.0/app-release.apk",
  "releaseNotes": "- Thêm các chế độ Auto: Nông Trại, Câu Cá, Đánh Quái RPG, Đào Khoáng\n- Tùy chỉnh số vòng lặp & thời gian tự động dừng\n- Nút ⚙️ đổi nhanh cấu hình ngay trên Game",
  "fileSize": "6.2 MB",
  "publishDate": "2026-08-19"
}
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavyCard,
        title = {
            Text("Cấu trúc file version.json trên GitHub", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Tạo file version.json trên GitHub repository của bạn với nội dung mẫu sau:",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                Surface(
                    color = DarkNavyBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DarkNavyCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = sampleJson,
                        color = BrightCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("version.json", sampleJson)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Đã sao chép cấu trúc JSON vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sao chép mẫu JSON", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DeepCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Đóng")
            }
        }
    )
}
