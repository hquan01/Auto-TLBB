package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AutoClickService: Core AccessibilityService to perform gestures (dispatchGesture)
 * without requiring root permissions.
 */
class AutoClickService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceRunning.value = true
        Log.d(TAG, "AutoClickService connected and active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event interception needed, purely gesture dispatching
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoClickService interrupted")
        _isServiceRunning.value = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _isServiceRunning.value = false
        Log.d(TAG, "AutoClickService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        _isServiceRunning.value = false
        super.onDestroy()
    }

    /**
     * Dispatches a single click (tap) gesture at screen coordinates (x, y).
     *
     * @param x X screen coordinate in pixels
     * @param y Y screen coordinate in pixels
     * @param durationMs Duration of tap down to up (default ~40ms)
     * @param onComplete Callback invoked with true on success or false on failure/cancel
     */
    fun dispatchClick(
        x: Float,
        y: Float,
        durationMs: Long = 40L,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete?.invoke(false)
            return false
        }

        val clickPath = Path().apply {
            moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
        }

        val stroke = GestureDescription.StrokeDescription(
            clickPath,
            0L,
            durationMs.coerceIn(10L, 500L)
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Dispatches a swipe gesture from (startX, startY) to (endX, endY).
     */
    fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete?.invoke(false)
            return false
        }

        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(
            swipePath,
            0L,
            durationMs.coerceIn(50L, 2000L)
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onComplete?.invoke(false)
            }
        }, null)
    }

    companion object {
        private const val TAG = "AutoClickService"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        @Volatile
        var instance: AutoClickService? = null
            private set
    }
}
