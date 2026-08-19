package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ClickConfig
import com.example.data.ClickMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * FloatingViewService: Displays a draggable floating overlay menu (SYSTEM_ALERT_WINDOW)
 * over any game or app with start/pause, target pointer management, and auto-click runner.
 */
class FloatingViewService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clickJob: Job? = null

    private lateinit var windowManager: WindowManager

    // Floating Toolbar View
    private var floatingToolbar: View? = null
    private var toolbarParams: WindowManager.LayoutParams? = null

    // Target pointers list
    private val targetViews = mutableListOf<TargetPointerHolder>()

    private var isPlaying = false
    private var isMinimized = false
    private var clickCount = 0L

    // Active Config
    private var config = ClickConfig(
        mode = ClickMode.SINGLE_POINT,
        intervalMs = 100L,
        clickDurationMs = 40L,
        repeatCount = 0
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()

        _isOverlayActive.value = true

        setupFloatingToolbar()
        // Add initial target pointer #1
        addTargetPointer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val interval = it.getLongExtra(EXTRA_INTERVAL_MS, config.intervalMs)
            val duration = it.getLongExtra(EXTRA_DURATION_MS, config.clickDurationMs)
            val repeat = it.getIntExtra(EXTRA_REPEAT_COUNT, config.repeatCount)
            val modeName = it.getStringExtra(EXTRA_MODE)
            val mode = if (modeName == ClickMode.MULTI_POINT.name) ClickMode.MULTI_POINT else ClickMode.SINGLE_POINT

            config = config.copy(
                intervalMs = interval,
                clickDurationMs = duration,
                repeatCount = repeat,
                mode = mode
            )
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "autoclicker_overlay_channel"
        val channelName = "Auto Clicker Overlay"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị thông báo khi Bảng điều khiển nổi đang chạy"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Clicker đang hoạt động")
            .setContentText("Bảng điều khiển nổi đang hiển thị đè lên màn hình")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    // ==========================================
    // FLOATING TOOLBAR UI & INTERACTION
    // ==========================================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingToolbar() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        // Build Custom Floating Bar UI programmatically for clean standalone service rendering
        val context = this
        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A")) // Dark Slate
                cornerRadius = dp(24f).toFloat()
                setStroke(dp(1.5f), Color.parseColor("#0284C7")) // Cyan accent border
            }
            background = bg
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            elevation = dp(12f).toFloat()
        }

        // --- Drag Header ---
        val dragHandle = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8f), dp(4f), dp(8f), dp(6f))

            val dragBar = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28f), dp(4f))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#64748B"))
                    cornerRadius = dp(2f).toFloat()
                }
            }
            addView(dragBar)
        }
        container.addView(dragHandle)

        // --- Action Buttons Container ---
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 1. Play / Pause Button
        val playBtn = createMenuButton(
            text = "▶",
            bgColor = "#10B981", // Emerald
            textColor = "#FFFFFF",
            sizeDp = 42f
        )
        playBtn.setOnClickListener {
            toggleClicker(playBtn)
        }
        actionsRow.addView(playBtn)

        // 2. Add Pointer Button (+)
        val addBtn = createMenuButton(
            text = "+",
            bgColor = "#1E293B",
            textColor = "#38BDF8",
            sizeDp = 36f
        )
        addBtn.setOnClickListener {
            if (targetViews.size < 10) {
                addTargetPointer()
                Toast.makeText(context, "Đã thêm điểm chạm #${targetViews.size}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Tối đa 10 điểm chạm", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(addBtn)

        // 3. Remove Pointer Button (-)
        val removeBtn = createMenuButton(
            text = "−",
            bgColor = "#1E293B",
            textColor = "#F87171",
            sizeDp = 36f
        )
        removeBtn.setOnClickListener {
            if (targetViews.size > 1) {
                removeLastTargetPointer()
            } else {
                Toast.makeText(context, "Tối thiểu 1 điểm chạm", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(removeBtn)

        // 4. Quick Interval Settings Button (⏱)
        val speedBtn = createMenuButton(
            text = "⏱",
            bgColor = "#1E293B",
            textColor = "#FBBF24",
            sizeDp = 36f
        )
        speedBtn.setOnClickListener {
            cycleIntervalSpeed(speedBtn)
        }
        actionsRow.addView(speedBtn)

        // 5. Close / Exit Button (✕)
        val closeBtn = createMenuButton(
            text = "✕",
            bgColor = "#334155",
            textColor = "#94A3B8",
            sizeDp = 36f
        )
        closeBtn.setOnClickListener {
            stopClicking()
            stopSelf()
        }
        actionsRow.addView(closeBtn)

        container.addView(actionsRow)

        // Setup Touch Dragging for the entire Toolbar
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoving = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = toolbarParams!!.x
                    initialY = toolbarParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isMoving = true
                    }
                    toolbarParams!!.x = initialX + dx
                    toolbarParams!!.y = initialY + dy
                    windowManager.updateViewLayout(container, toolbarParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isMoving
                }
                else -> false
            }
        }

        floatingToolbar = container
        windowManager.addView(floatingToolbar, toolbarParams)
    }

    private fun createMenuButton(
        text: String,
        bgColor: String,
        textColor: String,
        sizeDp: Float
    ): TextView {
        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }
        return TextView(this).apply {
            this.text = text
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(textColor))
            gravity = Gravity.CENTER
            val size = dp(sizeDp)
            val lp = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, dp(4f), 0, dp(4f))
            }
            layoutParams = lp

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(bgColor))
            }
            background = bg
        }
    }

    private fun cycleIntervalSpeed(btn: TextView) {
        val speeds = listOf(50L, 100L, 200L, 500L, 1000L)
        val currentIndex = speeds.indexOf(config.intervalMs)
        val nextInterval = speeds[(currentIndex + 1) % speeds.size]
        config = config.copy(intervalMs = nextInterval)
        Toast.makeText(this, "Tốc độ click: ${nextInterval}ms (${1000 / nextInterval} lần/s)", Toast.LENGTH_SHORT).show()
    }

    // ==========================================
    // TARGET POINTERS (DRAGGABLE CIRCLES OVER GAME)
    // ==========================================

    private data class TargetPointerHolder(
        val id: Int,
        val view: View,
        val params: WindowManager.LayoutParams,
        val pulseRing: View
    )

    @SuppressLint("ClickableViewAccessibility")
    private fun addTargetPointer() {
        val index = targetViews.size + 1
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        val pointerParams = WindowManager.LayoutParams(
            dp(56f),
            dp(56f),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Offset each new pointer slightly
            x = 300 + (index * 40)
            y = 500 + (index * 60)
        }

        // Outer Root Frame
        val targetFrame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        // Animated Pulse Ring
        val pulseRing = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(54f), dp(54f), Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#220284C7"))
                setStroke(dp(2f), Color.parseColor("#00E5FF"))
            }
            visibility = View.INVISIBLE
        }
        targetFrame.addView(pulseRing)

        // Main Center Target Disc
        val disc = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(44f), dp(44f), Gravity.CENTER)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC0F172A")) // Semi-transparent dark
                setStroke(dp(2.5f), Color.parseColor("#00E5FF")) // Neon Cyan
            }

            val label = TextView(context).apply {
                text = "$index"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#00E5FF"))
            }
            addView(label)
        }
        targetFrame.addView(disc)

        // Center crosshair dot
        val centerDot = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFFFFF"))
            }
        }
        targetFrame.addView(centerDot)

        // Touch listener to drag target point
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        targetFrame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = pointerParams.x
                    initialY = pointerParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    pointerParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    pointerParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(targetFrame, pointerParams)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(targetFrame, pointerParams)
        targetViews.add(TargetPointerHolder(index, targetFrame, pointerParams, pulseRing))
    }

    private fun removeLastTargetPointer() {
        if (targetViews.isNotEmpty()) {
            val last = targetViews.removeAt(targetViews.size - 1)
            try {
                windowManager.removeView(last.view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==========================================
    // AUTO CLICK EXECUTION LOOP
    // ==========================================

    private fun toggleClicker(playBtn: TextView) {
        val accessibilityService = AutoClickService.instance
        if (accessibilityService == null) {
            Toast.makeText(
                this,
                "⚠️ Vui lòng cấp quyền Trợ năng (Accessibility Service) cho ứng dụng trước!",
                Toast.LENGTH_LONG
            ).show()
            // Open accessibility settings
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            return
        }

        if (isPlaying) {
            stopClicking()
            playBtn.text = "▶"
            (playBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#10B981"))
            Toast.makeText(this, "Đã tạm dừng Auto Click", Toast.LENGTH_SHORT).show()
        } else {
            startClicking(playBtn)
            playBtn.text = "⏸"
            (playBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#EF4444")) // Red
            Toast.makeText(this, "Đang tự động click...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startClicking(playBtn: TextView) {
        isPlaying = true
        _isClicking.value = true
        clickJob?.cancel()

        clickJob = serviceScope.launch(Dispatchers.Default) {
            var currentIteration = 0
            val dpCenterOffset = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                28f,
                resources.displayMetrics
            )

            while (isActive && isPlaying) {
                val targets = synchronized(targetViews) { targetViews.toList() }
                if (targets.isEmpty()) {
                    delay(200)
                    continue
                }

                for (holder in targets) {
                    if (!isActive || !isPlaying) break

                    val clickX = holder.params.x.toFloat() + dpCenterOffset
                    val clickY = holder.params.y.toFloat() + dpCenterOffset

                    // Visual pulse animation on UI thread
                    mainHandler.post {
                        animateTargetPulse(holder.pulseRing)
                    }

                    // Dispatch accessibility gesture click
                    AutoClickService.instance?.dispatchClick(
                        x = clickX,
                        y = clickY,
                        durationMs = config.clickDurationMs
                    )

                    clickCount++
                    _clickCountFlow.value = clickCount

                    // Wait interval between clicks
                    val waitTime = (config.intervalMs - config.clickDurationMs).coerceAtLeast(10L)
                    delay(waitTime)
                }

                currentIteration++
                if (config.repeatCount > 0 && currentIteration >= config.repeatCount) {
                    mainHandler.post {
                        stopClicking()
                        playBtn.text = "▶"
                        (playBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#10B981"))
                        Toast.makeText(this@FloatingViewService, "Đã hoàn thành ${config.repeatCount} lượt click", Toast.LENGTH_SHORT).show()
                    }
                    break
                }
            }
        }
    }

    private fun animateTargetPulse(view: View) {
        view.visibility = View.VISIBLE
        val anim = AlphaAnimation(0.9f, 0.0f).apply {
            duration = 180
            fillAfter = false
        }
        view.startAnimation(anim)
        mainHandler.postDelayed({
            view.visibility = View.INVISIBLE
        }, 180)
    }

    private fun stopClicking() {
        isPlaying = false
        _isClicking.value = false
        clickJob?.cancel()
        clickJob = null
    }

    override fun onDestroy() {
        stopClicking()
        serviceScope.cancel()

        // Clean up floating views
        floatingToolbar?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        targetViews.forEach {
            try {
                windowManager.removeView(it.view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        targetViews.clear()

        _isOverlayActive.value = false
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_REPEAT_COUNT = "extra_repeat_count"
        const val EXTRA_MODE = "extra_mode"

        private val _isOverlayActive = MutableStateFlow(false)
        val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

        private val _isClicking = MutableStateFlow(false)
        val isClicking: StateFlow<Boolean> = _isClicking.asStateFlow()

        private val _clickCountFlow = MutableStateFlow(0L)
        val clickCountFlow: StateFlow<Boolean> = MutableStateFlow(false) // helper
        val totalClicks: StateFlow<Long> = _clickCountFlow.asStateFlow()

        fun start(
            context: Context,
            intervalMs: Long = 100L,
            durationMs: Long = 40L,
            repeatCount: Int = 0,
            mode: ClickMode = ClickMode.SINGLE_POINT
        ) {
            val intent = Intent(context, FloatingViewService::class.java).apply {
                putExtra(EXTRA_INTERVAL_MS, intervalMs)
                putExtra(EXTRA_DURATION_MS, durationMs)
                putExtra(EXTRA_REPEAT_COUNT, repeatCount)
                putExtra(EXTRA_MODE, mode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingViewService::class.java))
        }
    }
}
