package com.example.updater

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.MainActivity
import com.example.R
import com.example.data.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * UpdateManager: Handles In-App Update checks via GitHub Releases API or raw version.json,
 * downloads new .apk releases with progress tracking,
 * displays update notifications, and triggers seamless Android Package Installation.
 */
class UpdateManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "Cập nhật ứng dụng",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi có phiên bản mới trên GitHub"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Checks for updates by fetching and parsing either:
     * 1. A GitHub Release API endpoint (e.g. https://api.github.com/repos/owner/repo/releases/latest)
     * 2. A direct GitHub raw JSON file (e.g. https://raw.githubusercontent.com/owner/repo/main/version.json)
     * 3. A standard GitHub repo URL (e.g. https://github.com/owner/repo)
     */
    suspend fun checkForUpdate(sourceUrl: String): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = sourceUrl.trim()
            if (trimmedUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Đường dẫn GitHub không hợp lệ"))
            }

            // Normalize GitHub Repo URL to GitHub Releases API if needed
            val targetUrl = normalizeGitHubUrl(trimmedUrl)

            val url = URL(targetUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 12000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json, application/json")
                setRequestProperty("User-Agent", "AutoClicker-AppUpdate")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Lỗi kết nối GitHub (Mã HTTP $responseCode)"))
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val updateInfo = parseUpdateResponse(jsonString)

            val currentVersionCode = getCurrentAppVersionCode()
            Log.d(TAG, "Current Version: $currentVersionCode, Remote Version: ${updateInfo.versionCode}")

            if (updateInfo.versionCode > currentVersionCode) {
                // Show high-priority system notification about the new update
                showUpdateNotification(updateInfo)
                Result.success(updateInfo)
            } else {
                Result.success(null) // Up to date
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates from GitHub", e)
            Result.failure(e)
        }
    }

    /**
     * Converts a github.com repository URL to GitHub Releases API URL
     */
    private fun normalizeGitHubUrl(inputUrl: String): String {
        return when {
            inputUrl.contains("api.github.com") || inputUrl.contains("raw.githubusercontent.com") || inputUrl.endsWith(".json") -> {
                inputUrl
            }
            inputUrl.contains("github.com/") -> {
                // E.g. https://github.com/username/repo -> https://api.github.com/repos/username/repo/releases/latest
                val path = inputUrl.substringAfter("github.com/").trimEnd('/')
                val parts = path.split('/')
                if (parts.size >= 2) {
                    val owner = parts[0]
                    val repo = parts[1]
                    "https://api.github.com/repos/$owner/$repo/releases/latest"
                } else {
                    inputUrl
                }
            }
            else -> inputUrl
        }
    }

    /**
     * Parses either a custom version.json or a standard GitHub Releases API response
     */
    private fun parseUpdateResponse(jsonString: String): AppUpdateInfo {
        val json = JSONObject(jsonString)

        // Case 1: Standard GitHub Releases API response
        if (json.has("tag_name") && json.has("assets")) {
            val tagName = json.optString("tag_name", "1.0.0")
            val releaseName = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "Bản phát hành tính năng mới trên GitHub.")
            val publishDate = json.optString("published_at", "")

            // Parse version numbers
            val cleanVersion = tagName.removePrefix("v").removePrefix("V")
            val versionCode = extractVersionCode(cleanVersion)

            // Look for .apk file in release assets
            var apkUrl = ""
            var fileSize: String? = null
            val assets: JSONArray = json.optJSONArray("assets") ?: JSONArray()
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    val sizeBytes = asset.optLong("size", 0L)
                    if (sizeBytes > 0) {
                        fileSize = String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
                    }
                    break
                }
            }

            // Fallback to first asset or zipball if no APK found
            if (apkUrl.isBlank() && assets.length() > 0) {
                apkUrl = assets.getJSONObject(0).optString("browser_download_url", "")
            }

            return AppUpdateInfo(
                versionCode = versionCode,
                versionName = if (cleanVersion.isNotBlank()) cleanVersion else releaseName,
                apkUrl = apkUrl,
                releaseNotes = releaseNotes,
                fileSize = fileSize,
                publishDate = publishDate
            )
        }

        // Case 2: Custom version.json file
        val remoteVersionCode = json.optLong("versionCode", 0L)
        val remoteVersionName = json.optString("versionName", "1.0.0")
        val apkUrl = json.optString("apkUrl", "")
        val releaseNotes = json.optString("releaseNotes", "Bản cập nhật tính năng mới.")
        val fileSize = json.optString("fileSize", "").ifBlank { null }
        val publishDate = json.optString("publishDate", "").ifBlank { null }

        return AppUpdateInfo(
            versionCode = remoteVersionCode,
            versionName = remoteVersionName,
            apkUrl = apkUrl,
            releaseNotes = releaseNotes,
            fileSize = fileSize,
            publishDate = publishDate
        )
    }

    private fun extractVersionCode(versionName: String): Long {
        return try {
            val parts = versionName.split('.').mapNotNull { it.takeWhile { char -> char.isDigit() }.toLongOrNull() }
            when (parts.size) {
                1 -> parts[0]
                2 -> parts[0] * 100 + parts[1]
                3 -> parts[0] * 10000 + parts[1] * 100 + parts[2]
                else -> parts.fold(0L) { acc, num -> acc * 100 + num }
            }
        } catch (_: Exception) {
            1L
        }
    }

    /**
     * Shows a heads-up system notification when an update is available
     */
    fun showUpdateNotification(updateInfo: AppUpdateInfo) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_UPDATE, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            101,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🔔 Có bản cập nhật mới v${updateInfo.versionName}!")
            .setContentText("Nhấn vào đây để tải và tự động cập nhật tính năng mới")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Phiên bản v${updateInfo.versionName} đã sẵn sàng.\n\nTính năng mới:\n${updateInfo.releaseNotes}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)
    }

    /**
     * Downloads the APK file directly with real-time percentage progress callback
     */
    suspend fun downloadApkDirect(
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (apkUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Link tải APK trống"))
            }

            val url = URL(apkUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AutoClicker-AppUpdate")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != 302 && responseCode != 301) {
                return@withContext Result.failure(Exception("Không thể tải file APK (Mã HTTP $responseCode)"))
            }

            val fileLength = connection.contentLength
            val updatesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates").apply {
                mkdirs()
            }
            val apkFile = File(updatesDir, "update_latest.apk")
            if (apkFile.exists()) apkFile.delete()

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    var lastPercent = -1

                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            val percent = ((total * 100) / fileLength).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) {
                                    onProgress(percent)
                                }
                            }
                        }
                        output.write(data, 0, count)
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(100)
            }
            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            Result.failure(e)
        }
    }

    /**
     * Triggers Android's Package Installer to install the new APK over the existing app.
     * All existing app data and preferences are preserved seamlessly.
     */
    fun installApk(apkFile: File): Boolean {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
                return false
            }

            // Check Unknown App Sources permission (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return false
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            return false
        }
    }

    fun getCurrentAppVersionCode(): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }

    fun getCurrentAppVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    companion object {
        private const val TAG = "UpdateManager"
        const val UPDATE_CHANNEL_ID = "app_update_channel"
        const val NOTIFICATION_ID_UPDATE = 2002
        const val EXTRA_AUTO_UPDATE = "extra_auto_update"
        const val DEFAULT_GITHUB_VERSION_URL =
            "https://raw.githubusercontent.com/example/autoclicker/main/version.json"
    }
}
