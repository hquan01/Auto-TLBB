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
     * 1. A GitHub raw JSON file (e.g. https://raw.githubusercontent.com/hquan01/Auto-TLBB/main/version.json)
     * 2. A standard GitHub repo URL (e.g. https://github.com/hquan01/Auto-TLBB)
     * 3. A GitHub Release API endpoint (e.g. https://api.github.com/repos/hquan01/Auto-TLBB/releases/latest)
     */
    suspend fun checkForUpdate(sourceUrl: String): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = sourceUrl.trim()
            if (trimmedUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Đường dẫn GitHub không được để trống"))
            }

            val candidateUrls = if (trimmedUrl.contains("github.com/") && !trimmedUrl.contains("api.github.com") && !trimmedUrl.endsWith(".json")) {
                val path = trimmedUrl.substringAfter("github.com/").trimEnd('/')
                val parts = path.split('/')
                if (parts.size >= 2) {
                    val owner = parts[0]
                    val repo = parts[1]
                    listOf(
                        "https://raw.githubusercontent.com/$owner/$repo/main/version.json",
                        "https://raw.githubusercontent.com/$owner/$repo/master/version.json",
                        "https://api.github.com/repos/$owner/$repo/releases/latest"
                    )
                } else {
                    listOf(trimmedUrl)
                }
            } else {
                listOf(trimmedUrl)
            }

            var lastError: Exception? = null
            for (targetUrl in candidateUrls) {
                try {
                    val url = URL(targetUrl)
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 10000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/vnd.github.v3+json, application/json, text/plain")
                        setRequestProperty("User-Agent", "AutoClicker-AppUpdate")
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                        val updateInfo = parseUpdateResponse(jsonString)

                        val currentVersionCode = getCurrentAppVersionCode()
                        Log.d(TAG, "Current Version: $currentVersionCode, Remote Version: ${updateInfo.versionCode}")

                        if (updateInfo.versionCode > currentVersionCode) {
                            showUpdateNotification(updateInfo)
                            return@withContext Result.success(updateInfo)
                        } else {
                            return@withContext Result.success(null) // Up to date
                        }
                    } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        lastError = Exception("Không tìm thấy file version.json trên GitHub (Mã HTTP: 404).")
                    } else {
                        lastError = Exception("Lỗi kết nối GitHub (Mã HTTP $responseCode)")
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }

            Result.failure(lastError ?: Exception("Không thể kết nối đến GitHub"))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates from GitHub", e)
            Result.failure(e)
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

            val cleanVersion = tagName.removePrefix("v").removePrefix("V")
            val versionCode = extractVersionCode(cleanVersion)

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
        val remoteVersionCode = json.optLong("versionCode", 1L)
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
            .setContentText(updateInfo.releaseNotes.take(60))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Phiên bản v${updateInfo.versionName} đã sẵn sàng:\n${updateInfo.releaseNotes}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)
    }

    /**
     * Downloads the APK file from the provided URL with progress callbacks.
     * Supports automatic fallback checks if the given link returns 404.
     */
    suspend fun downloadApk(
        apkUrl: String,
        targetFileName: String = "autoclicker_update.apk",
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (apkUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Link tải file APK từ GitHub đang trống. Vui lòng đính kèm file APK vào GitHub Releases hoặc điền link tải 'apkUrl' trong version.json!")
            )
        }

        // Build list of candidate APK URLs to try
        val candidateUrls = mutableListOf(apkUrl)
        if (apkUrl.contains("github.com/hquan01/Auto-TLBB")) {
            // Also try raw branch or latest release
            candidateUrls.add("https://raw.githubusercontent.com/hquan01/Auto-TLBB/main/app-debug.apk")
            candidateUrls.add("https://github.com/hquan01/Auto-TLBB/releases/latest/download/app-debug.apk")
        }

        var lastHttpCode = 0

        for (tryUrl in candidateUrls) {
            try {
                val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                val outputFile = File(downloadDir, targetFileName)
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                val url = URL(tryUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AutoClicker-AppUpdate")
                }

                // Handle HTTP 301/302 Redirects for GitHub Releases
                var redirectedConnection = connection
                var redirectCount = 0
                while (redirectedConnection.responseCode in listOf(301, 302, 303, 307, 308) && redirectCount < 5) {
                    val newUrl = redirectedConnection.getHeaderField("Location")
                    redirectedConnection.disconnect()
                    val nextUrl = URL(newUrl)
                    redirectedConnection = (nextUrl.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20000
                        readTimeout = 30000
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "AutoClicker-AppUpdate")
                    }
                    redirectCount++
                }

                val responseCode = redirectedConnection.responseCode
                lastHttpCode = responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val fileLength = redirectedConnection.contentLength
                    val inputStream = redirectedConnection.inputStream
                    val outputStream = FileOutputStream(outputFile)

                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (fileLength > 0) {
                            val progressPercent = ((totalBytesRead * 100) / fileLength).toInt()
                            onProgress(progressPercent.coerceIn(0, 100))
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    redirectedConnection.disconnect()

                    return@withContext Result.success(outputFile)
                } else {
                    redirectedConnection.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed downloading candidate: $tryUrl", e)
            }
        }

        if (lastHttpCode == HttpURLConnection.HTTP_NOT_FOUND) {
            return@withContext Result.failure(
                Exception(
                    "Không tìm thấy file APK trên GitHub (Mã HTTP: 404).\n\n" +
                    "Nguyên nhân: Đường link APK chưa tồn tại hoặc bạn chưa upload file .apk lên GitHub Releases.\n\n" +
                    "Cách khắc phục:\n" +
                    "1. Vào https://github.com/hquan01/Auto-TLBB/releases\n" +
                    "2. Bấm 'Draft a new release' và đính kèm file .apk vào mục Assets.\n" +
                    "3. Hoặc cập nhật link 'apkUrl' trực tiếp trong file version.json."
                )
            )
        } else {
            return@withContext Result.failure(
                Exception("Không thể tải file APK từ GitHub (Mã HTTP: $lastHttpCode)")
            )
        }
    }

    /**
     * Triggers the Android package installer for the downloaded APK file
     */
    fun installApk(apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found at: ${apkFile.absolutePath}")
            return
        }

        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            // Check Unknown App Install permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start APK installation intent", e)
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
            "https://raw.githubusercontent.com/hquan01/Auto-TLBB/main/version.json"
    }
}
