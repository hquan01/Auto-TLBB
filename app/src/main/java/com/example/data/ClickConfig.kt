package com.example.data

import com.squareup.moshi.JsonClass

/**
 * Click Mode: Single Point or Multi-Point sequence
 */
enum class ClickMode {
    SINGLE_POINT,
    MULTI_POINT
}

/**
 * Represents a single click target point on screen
 */
data class ClickTarget(
    val id: Int,
    var x: Float,
    var y: Float,
    val delayAfterMs: Long = 0L
)

/**
 * Configuration options for the Auto Clicker
 */
data class ClickConfig(
    val mode: ClickMode = ClickMode.SINGLE_POINT,
    val intervalMs: Long = 100L,
    val clickDurationMs: Long = 40L,
    val repeatCount: Int = 0, // 0 = infinite
    val targets: List<ClickTarget> = listOf(ClickTarget(1, 300f, 600f))
)

/**
 * Realtime execution statistics
 */
data class ClickStats(
    val isRunning: Boolean = false,
    val totalClicks: Long = 0L,
    val activeTargetIndex: Int = 0
)

/**
 * Model representing version.json hosted on GitHub
 */
@JsonClass(generateAdapter = true)
data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String = "",
    val fileSize: String? = null,
    val publishDate: String? = null,
    val minRequiredVersion: Long? = null
)
