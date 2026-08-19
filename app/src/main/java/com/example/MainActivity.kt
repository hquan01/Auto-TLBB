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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AppUpdateInfo
import com.example.data.ClickMode
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

        // Handle auto-update trigger if opened from system notification
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoClickerMainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-refresh permissions whenever screen regains focus
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
    val clickConfig by viewModel.clickConfig.collectAsState()
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

            // 2. Auto Clicker Configuration & Launcher Card
            AutoClickControlCard(
                isOverlayActive = isOverlayActive,
                isClicking = isClicking,
                intervalMs = clickConfig.intervalMs,
                repeatCount = clickConfig.repeatCount,
                clickMode = clickConfig.mode,
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
                            return@AutoClickControlCard
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
                            return@AutoClickControlCard
                        }
                        viewModel.startFloatingOverlay()
                        Toast.makeText(context, "Đã bật Bảng điều khiển nổi đè lên màn hình!", Toast.LENGTH_SHORT).show()
                    }
                },
                onIntervalChanged = { viewModel.updateInterval(it) },
                onRepeatCountChanged = { viewModel.updateRepeatCount(it) },
                onModeChanged = { viewModel.updateClickMode(it) }
            )

            // 3. GitHub Auto-Updater Card (Instant Notification & 1-Click Update)
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
                            text = "Auto Clicker",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Nút nổi Game & Auto-Updater",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = BrightCyan
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

            // Real-time Status Badge
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
                            isClicking -> "Đang tự động click liên tục..."
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

            // 1. Accessibility Service
            PermissionItem(
                title = "1. Dịch vụ Trợ năng (Accessibility)",
                description = "Bắt buộc để ứng dụng tự động thực hiện thao tác chạm (dispatchGesture) đè lên Game.",
                isGranted = isAccessibilityGranted,
                actionLabel = if (isAccessibilityGranted) "Đã cấp quyền" else "Cấp quyền Trợ năng",
                onClick = onOpenAccessibility,
                testTag = "btn_grant_accessibility"
            )

            Divider(color = DarkNavyCardBorder)

            // 2. SYSTEM_ALERT_WINDOW (Overlay)
            PermissionItem(
                title = "2. Vẽ đè lên ứng dụng khác (Overlay)",
                description = "Bắt buộc để hiển thị nút Bắt đầu / Tạm dừng và các điểm chấm ngắm tròn trên Game.",
                isGranted = isOverlayGranted,
                actionLabel = if (isOverlayGranted) "Đã cấp quyền" else "Cấp quyền Cửa sổ nổi",
                onClick = onOpenOverlay,
                testTag = "btn_grant_overlay"
            )

            Divider(color = DarkNavyCardBorder)

            // 3. REQUEST_INSTALL_PACKAGES
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
// 3. AUTO CLICK CONTROL CARD
// ==========================================

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutoClickControlCard(
    isOverlayActive: Boolean,
    isClicking: Boolean,
    intervalMs: Long,
    repeatCount: Int,
    clickMode: ClickMode,
    onToggleOverlay: () -> Unit,
    onIntervalChanged: (Long) -> Unit,
    onRepeatCountChanged: (Int) -> Unit,
    onModeChanged: (ClickMode) -> Unit
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
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Bảng Điều Khiển Nổi & Tốc Độ Click",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }

            // Big Launch / Stop Overlay Button
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
                    text = if (isOverlayActive) "TẮT BẢNG ĐIỀU KHIỂN NỔI" else "BẬT BẢNG ĐIỀU KHIỂN NỔI (OVERLAY)",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Divider(color = DarkNavyCardBorder)

            // Mode Selector
            Text(
                text = "Chế độ Auto Click",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = clickMode == ClickMode.SINGLE_POINT,
                    onClick = { onModeChanged(ClickMode.SINGLE_POINT) },
                    label = { Text("1 Điểm chạm") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (clickMode == ClickMode.SINGLE_POINT) ElectricCyan else DarkNavyCardBorder,
                        enabled = true,
                        selected = clickMode == ClickMode.SINGLE_POINT
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = clickMode == ClickMode.MULTI_POINT,
                    onClick = { onModeChanged(ClickMode.MULTI_POINT) },
                    label = { Text("Đa điểm (Tuần tự)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (clickMode == ClickMode.MULTI_POINT) ElectricCyan else DarkNavyCardBorder,
                        enabled = true,
                        selected = clickMode == ClickMode.MULTI_POINT
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Interval Speed Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = BrightCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Khoảng cách click:",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )
                }

                Text(
                    text = "${intervalMs}ms (${if (intervalMs > 0) 1000 / intervalMs else 0} lần/s)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(20L, 50L, 100L, 200L, 500L, 1000L)
                presets.forEach { speed ->
                    val isSelected = intervalMs == speed
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) ElectricCyan.copy(alpha = 0.25f)
                                else SurfaceHighlight
                            )
                            .border(
                                1.dp,
                                if (isSelected) ElectricCyan else DarkNavyCardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onIntervalChanged(speed) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${speed}ms",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ElectricCyan else TextSecondary
                            )
                        )
                    }
                }
            }

            // Interval Slider
            Slider(
                value = intervalMs.toFloat(),
                onValueChange = { onIntervalChanged(it.toLong()) },
                valueRange = 10f..2000f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = SurfaceHighlight
                )
            )

            // Repeat Count Configuration
            Text(
                text = "Số lần nhấp tối đa:",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val repeatPresets = listOf(0 to "Vô hạn", 100 to "100 lần", 500 to "500 lần", 1000 to "1000 lần")
                repeatPresets.forEach { (count, label) ->
                    val isSelected = repeatCount == count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) EmeraldGreen.copy(alpha = 0.25f)
                                else SurfaceHighlight
                            )
                            .border(
                                1.dp,
                                if (isSelected) EmeraldGreen else DarkNavyCardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onRepeatCountChanged(count) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) EmeraldGreen else TextSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. GITHUB UPDATE CARD
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

            // Feature Banner: Auto-Update Flow
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
                        text = "Mỗi khi bạn cập nhật tính năng lên GitHub (Release hoặc version.json), app sẽ tự động thông báo và 1-chạm nâng cấp tính năng mới ngay lập tức!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextPrimary,
                            lineHeight = 17.sp
                        )
                    )
                }
            }

            // Toggle Auto-check on launch
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

            // GitHub URL field (supports repo URL, release API, or raw json)
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
// 5. INSTRUCTIONS CARD
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
                text = "📖 Cách Triển Khai Tính Năng Mới Lên GitHub",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            StepItem(
                step = "1",
                title = "Thêm tính năng và build file APK mới",
                desc = "Khi code xong tính năng mới, bạn tăng versionCode trong app/build.gradle.kts và build file APK."
            )
            StepItem(
                step = "2",
                title = "Đăng bản mới lên GitHub",
                desc = "Tạo một GitHub Release và đính kèm file APK, hoặc cập nhật versionCode và link APK trong file version.json trên GitHub."
            )
            StepItem(
                step = "3",
                title = "Ứng dụng tự động thông báo",
                desc = "Điện thoại của bạn sẽ tự động quét thấy bản mới và hiển thị thông báo cập nhật ngay lập tức."
            )
            StepItem(
                step = "4",
                title = "1-chạm cập nhật tính năng mới",
                desc = "Chỉ cần nhấn 'Cập nhật ngay', ứng dụng sẽ tự động tải file APK về và mở cài đặt nâng cấp giữ nguyên mọi dữ liệu."
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
// 6. UPDATE DIALOGS & ALERTS
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
                onDismissRequest = { /* non-cancelable during download */ },
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
                        Text(
                            text = "Sau khi tải xong, ứng dụng sẽ tự động mở cài đặt để nâng cấp!",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
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
// 7. GITHUB JSON HELP DIALOG
// ==========================================

@Composable
fun GitHubJsonHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sampleJson = """
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "apkUrl": "https://github.com/your-username/your-repo/releases/download/v1.1.0/app-release.apk",
  "releaseNotes": "- Thêm tính năng tự động phát hiện bản mới\n- Tối ưu tốc độ click game cực nhanh\n- Sửa lỗi đa điểm chạm",
  "fileSize": "5.8 MB",
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
                    "Tạo file version.json trên GitHub repository của bạn (hoặc GitHub Gist / GitHub Releases) với nội dung mẫu sau:",
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
