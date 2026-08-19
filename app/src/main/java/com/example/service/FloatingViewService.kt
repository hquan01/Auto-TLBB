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
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AutoClickScript
import com.example.data.ScriptPoint
import com.example.data.ScriptRepository
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
 * FloatingViewService: Draggable floating overlay menu over any game or app.
 * Executes custom Auto Click scripts with individual point delays, touch duration,
 * loop repeats, loop delays, stop timer, and in-game floating configuration editors.
 */
class FloatingViewService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clickJob: Job? = null

    private lateinit var windowManager: WindowManager
    private lateinit var scriptRepository: ScriptRepository

    // Floating Toolbar View
    private var floatingToolbar: View? = null
    private var toolbarParams: WindowManager.LayoutParams? = null
    private var toolbarContainer: LinearLayout? = null
    private var toolbarDragHandle: View? = null
    private val toolbarActionViews = mutableListOf<View>()
    private var playButtonView: TextView? = null

    // Target pointers list
    private val targetViews = mutableListOf<TargetPointerHolder>()

    // Dialog Views
    private var pointEditorDialog: View? = null
    private var scriptPickerDialog: View? = null
    private var loopSettingsDialog: View? = null

    private var isPlaying = false
    private var clickCount = 0L
    private var currentLoopCount = 0

    // Active Running Script
    private var activeScript: AutoClickScript = AutoClickScript.getDefaultPresets().first()

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        scriptRepository = ScriptRepository.getInstance(this)
        activeScript = scriptRepository.getActiveScript()

        startForegroundServiceNotification()
        _isOverlayActive.value = true

        setupFloatingToolbar()
        loadScriptPointsToScreen(activeScript)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_SCRIPT_ID)?.let { scriptId ->
            val script = scriptRepository.scriptsFlow.value.find { it.id == scriptId }
            if (script != null) {
                activeScript = script
                loadScriptPointsToScreen(activeScript)
            }
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
            .setContentTitle("Auto Clicker: ${activeScript.name}")
            .setContentText("Bảng điều khiển nổi đang sẵn sàng trên màn hình")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    // ==========================================
    // FLOATING TOOLBAR UI
    // ==========================================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingToolbar() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16f)
            y = dp(180f)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0B132B"))
                cornerRadius = dp(24f).toFloat()
                setStroke(dp(1.5f), Color.parseColor("#00E5FF"))
            }
            background = bg
            setPadding(dp(6f), dp(8f), dp(6f), dp(8f))
            elevation = dp(12f).toFloat()
        }

        // Top Drag Handle Indicator
        val dragHandle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2f), 0, dp(6f))
            val dragBar = View(this@FloatingViewService).apply {
                val lp = LinearLayout.LayoutParams(dp(22f), dp(3.5f))
                layoutParams = lp
                val barBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#48CAE4"))
                    cornerRadius = dp(2f).toFloat()
                }
                background = barBg
            }
            addView(dragBar)
        }
        toolbarDragHandle = dragHandle
        container.addView(dragHandle)

        // --- Action Buttons ---
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 1. Play / Pause Button (▶)
        val playBtn = createMenuButton(
            text = "▶",
            bgColor = "#10B981",
            textColor = "#FFFFFF",
            sizeDp = 42f
        )
        playButtonView = playBtn
        playBtn.setOnClickListener {
            toggleClicker(playBtn)
        }
        actionsRow.addView(playBtn)

        toolbarActionViews.clear()

        // 2. Add Target Point Button (+)
        val addBtn = createMenuButton(
            text = "+",
            bgColor = "#1E293B",
            textColor = "#38BDF8",
            sizeDp = 36f
        )
        addBtn.setOnClickListener {
            if (targetViews.size < 20) {
                val nextId = targetViews.size + 1
                val newPoint = ScriptPoint(
                    id = nextId,
                    name = "Điểm $nextId",
                    x = 350f + (nextId * 30),
                    y = 500f + (nextId * 40),
                    delayBeforeMs = 0L,
                    clickDurationMs = 40L,
                    delayAfterMs = 100L
                )
                addTargetPointer(newPoint)
                syncCurrentScreenToActiveScript()
                Toast.makeText(this, "Đã thêm Điểm #$nextId", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Tối đa 20 điểm chạm", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(addBtn)
        toolbarActionViews.add(addBtn)

        // 3. Remove Target Point Button (−)
        val removeBtn = createMenuButton(
            text = "−",
            bgColor = "#1E293B",
            textColor = "#F87171",
            sizeDp = 36f
        )
        removeBtn.setOnClickListener {
            if (targetViews.size > 1) {
                removeLastTargetPointer()
                syncCurrentScreenToActiveScript()
            } else {
                Toast.makeText(this, "Tối thiểu 1 điểm chạm", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(removeBtn)
        toolbarActionViews.add(removeBtn)

        // 4. Quick Loop & Timer Settings Button (⚙️)
        val loopSettingsBtn = createMenuButton(
            text = "⚙️",
            bgColor = "#1E293B",
            textColor = "#F59E0B",
            sizeDp = 36f
        )
        loopSettingsBtn.setOnClickListener {
            showLoopAndTimerSettingsOverlay()
        }
        actionsRow.addView(loopSettingsBtn)
        toolbarActionViews.add(loopSettingsBtn)

        // 5. Script Switcher & Presets Button (📂)
        val scriptBtn = createMenuButton(
            text = "📂",
            bgColor = "#1E293B",
            textColor = "#FBBF24",
            sizeDp = 36f
        )
        scriptBtn.setOnClickListener {
            showScriptPickerOverlay()
        }
        actionsRow.addView(scriptBtn)
        toolbarActionViews.add(scriptBtn)

        // 6. Save Current Positions Button (💾)
        val saveBtn = createMenuButton(
            text = "💾",
            bgColor = "#1E293B",
            textColor = "#34D399",
            sizeDp = 36f
        )
        saveBtn.setOnClickListener {
            syncCurrentScreenToActiveScript()
            serviceScope.launch {
                scriptRepository.saveOrUpdateScript(activeScript)
                Toast.makeText(
                    this@FloatingViewService,
                    "Đã lưu tọa độ & thời gian vào '${activeScript.name}'!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        actionsRow.addView(saveBtn)
        toolbarActionViews.add(saveBtn)

        // 7. Close Button (✕)
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
        toolbarActionViews.add(closeBtn)

        container.addView(actionsRow)

        // Setup Touch Dragging
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
        toolbarContainer = container
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
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(textColor))
            gravity = Gravity.CENTER
            val size = dp(sizeDp)
            val lp = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, dp(3f), 0, dp(3f))
            }
            layoutParams = lp

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(bgColor))
            }
            background = bg
        }
    }

    // ==========================================
    // TARGET POINTERS (DRAGGABLE CIRCLES OVER GAME)
    // ==========================================

    private data class TargetPointerHolder(
        var point: ScriptPoint,
        val view: View,
        val discFrame: View,
        val params: WindowManager.LayoutParams,
        val pulseRing: View,
        val label: TextView,
        val timeBadge: TextView
    )

    private fun loadScriptPointsToScreen(script: AutoClickScript) {
        targetViews.forEach {
            try {
                windowManager.removeView(it.view)
            } catch (_: Exception) {}
        }
        targetViews.clear()

        script.points.forEach { point ->
            addTargetPointer(point)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTargetPointer(point: ScriptPoint) {
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
            dp(64f),
            dp(72f),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = point.x.toInt()
            y = point.y.toInt()
        }

        val targetFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        val discFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f))
        }

        val pulseRing = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(48f), dp(48f), Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4400E5FF"))
                setStroke(dp(3f), Color.parseColor("#00E5FF"))
            }
            visibility = View.INVISIBLE
        }
        discFrame.addView(pulseRing)

        val disc = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(42f), dp(42f), Gravity.CENTER)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#DD0F172A"))
                setStroke(dp(2.5f), Color.parseColor("#00E5FF"))
            }
        }

        val label = TextView(this).apply {
            text = "${point.id}"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
        }
        disc.addView(label)
        discFrame.addView(disc)

        val centerDot = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFFFFF"))
            }
        }
        discFrame.addView(centerDot)
        targetFrame.addView(discFrame)

        val timeBadge = TextView(this).apply {
            text = "${point.delayAfterMs}ms"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FBBF24"))
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0F172A"))
                cornerRadius = dp(6f).toFloat()
                setStroke(dp(0.8f), Color.parseColor("#FBBF24"))
            }
            background = bg
            setPadding(dp(4f), dp(1f), dp(4f), dp(1f))
        }
        targetFrame.addView(timeBadge)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchStartTime = 0L
        var isDragging = false

        targetFrame.setOnTouchListener { _, event ->
            if (isPlaying) {
                // Ignore touches on target views while playing
                return@setOnTouchListener false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = pointerParams.x
                    initialY = pointerParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                        isDragging = true
                    }
                    pointerParams.x = initialX + dx
                    pointerParams.y = initialY + dy
                    point.x = pointerParams.x.toFloat()
                    point.y = pointerParams.y.toFloat()
                    windowManager.updateViewLayout(targetFrame, pointerParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - touchStartTime
                    if (!isDragging && clickDuration < 300) {
                        showPointTimingEditor(point, timeBadge)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(targetFrame, pointerParams)
        targetViews.add(TargetPointerHolder(point, targetFrame, discFrame, pointerParams, pulseRing, label, timeBadge))
    }

    private fun setTargetsTouchPassThrough(passThrough: Boolean) {
        targetViews.forEach { holder ->
            try {
                if (passThrough) {
                    holder.params.flags = holder.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    holder.params.flags = holder.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
                windowManager.updateViewLayout(holder.view, holder.params)
            } catch (_: Exception) {}
        }
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

    private fun syncCurrentScreenToActiveScript() {
        val updatedPoints = targetViews.mapIndexed { idx, holder ->
            holder.point.copy(
                id = idx + 1,
                name = holder.point.name.ifBlank { "Điểm ${idx + 1}" },
                x = holder.params.x.toFloat(),
                y = holder.params.y.toFloat()
            )
        }
        activeScript = activeScript.copy(points = updatedPoints)
    }

    // ==========================================
    // ON-SCREEN POINT TIMING EDITOR DIALOG
    // ==========================================

    @SuppressLint("ClickableViewAccessibility")
    private fun showPointTimingEditor(point: ScriptPoint, timeBadge: TextView) {
        dismissAllDialogs()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        val dialogParams = WindowManager.LayoutParams(
            dp(280f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = dp(16f).toFloat()
                setStroke(dp(1.5f), Color.parseColor("#00E5FF"))
            }
            background = bg
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            elevation = dp(16f).toFloat()
        }

        val title = TextView(this).apply {
            text = "⏱ Cài đặt Điểm #${point.id} (${point.name})"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
        }
        root.addView(title)

        root.addView(createSectionLabel("Thời gian chờ sau khi click (ms):"))
        val delayAfterInput = createNumberEditText("${point.delayAfterMs}")
        root.addView(delayAfterInput)

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4f), 0, dp(6f))
        }
        listOf(20L, 50L, 100L, 200L, 500L).forEach { presetMs ->
            val chip = TextView(this).apply {
                text = "${presetMs}ms"
                textSize = 11f
                setTextColor(Color.parseColor("#38BDF8"))
                setPadding(dp(6f), dp(3f), dp(6f), dp(3f))
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dp(6f).toFloat()
                }
                background = bg
                setOnClickListener {
                    delayAfterInput.setText("$presetMs")
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, dp(4f), 0)
                }
                layoutParams = lp
            }
            presetRow.addView(chip)
        }
        root.addView(presetRow)

        root.addView(createSectionLabel("Thời gian giữ chạm click (ms):"))
        val durationInput = createNumberEditText("${point.clickDurationMs}")
        root.addView(durationInput)

        root.addView(createSectionLabel("Thời gian chờ trước khi click (ms):"))
        val delayBeforeInput = createNumberEditText("${point.delayBeforeMs}")
        root.addView(delayBeforeInput)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12f), 0, 0)
        }

        val cancelBtn = TextView(this).apply {
            text = "Đóng"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            setOnClickListener {
                dismissPointEditor()
            }
        }
        btnRow.addView(cancelBtn)

        val saveBtn = TextView(this).apply {
            text = "Lưu Thay Đổi"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFFFFF"))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0284C7"))
                cornerRadius = dp(8f).toFloat()
            }
            background = bg
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            setOnClickListener {
                val newDelayAfter = delayAfterInput.text.toString().toLongOrNull() ?: point.delayAfterMs
                val newDuration = durationInput.text.toString().toLongOrNull() ?: point.clickDurationMs
                val newDelayBefore = delayBeforeInput.text.toString().toLongOrNull() ?: point.delayBeforeMs

                point.delayAfterMs = newDelayAfter.coerceAtLeast(5L)
                point.clickDurationMs = newDuration.coerceAtLeast(10L)
                point.delayBeforeMs = newDelayBefore.coerceAtLeast(0L)

                timeBadge.text = "${point.delayAfterMs}ms"
                syncCurrentScreenToActiveScript()

                serviceScope.launch {
                    scriptRepository.saveOrUpdateScript(activeScript)
                }

                Toast.makeText(this@FloatingViewService, "Đã cập nhật thời gian Điểm #${point.id}!", Toast.LENGTH_SHORT).show()
                dismissPointEditor()
            }
        }
        btnRow.addView(saveBtn)
        root.addView(btnRow)

        pointEditorDialog = root
        windowManager.addView(root, dialogParams)
    }

    private fun dismissPointEditor() {
        pointEditorDialog?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            pointEditorDialog = null
        }
    }

    // ==========================================
    // IN-GAME LOOP & TIMER SETTINGS DIALOG (⚙️)
    // ==========================================

    @SuppressLint("ClickableViewAccessibility")
    private fun showLoopAndTimerSettingsOverlay() {
        dismissAllDialogs()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        val dialogParams = WindowManager.LayoutParams(
            dp(290f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = dp(16f).toFloat()
                setStroke(dp(1.5f), Color.parseColor("#F59E0B"))
            }
            background = bg
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            elevation = dp(16f).toFloat()
        }

        // Header
        val title = TextView(this).apply {
            text = "⚙️ Cài đặt Số Vòng Lặp & Hẹn Giờ"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F59E0B"))
        }
        root.addView(title)

        // 1. Repeat Count (Số vòng lặp)
        root.addView(createSectionLabel("Số vòng lặp kịch bản (0 = Vô hạn):"))
        val repeatInput = createNumberEditText("${activeScript.repeatCount}")
        root.addView(repeatInput)

        // Repeat Presets
        val repeatPresetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3f), 0, dp(6f))
        }
        listOf(1 to "1 Lượt (Ko lặp)", 0 to "Vô hạn", 5 to "5 lần", 10 to "10 lần", 50 to "50 lần", 100 to "100 lần").forEach { (count, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 10f
                setTextColor(Color.parseColor("#38BDF8"))
                setPadding(dp(5f), dp(2f), dp(5f), dp(2f))
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dp(5f).toFloat()
                }
                background = bg
                setOnClickListener {
                    repeatInput.setText("$count")
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, dp(3f), 0)
                }
                layoutParams = lp
            }
            repeatPresetRow.addView(chip)
        }
        root.addView(repeatPresetRow)

        // 2. Loop Delay (Thời gian nghỉ giữa các vòng)
        root.addView(createSectionLabel("Thời gian nghỉ giữa các vòng lặp (ms):"))
        val loopDelayInput = createNumberEditText("${activeScript.loopDelayMs}")
        root.addView(loopDelayInput)

        val loopDelayPresetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3f), 0, dp(6f))
        }
        listOf(0L to "0ms", 200L to "200ms", 500L to "500ms", 1000L to "1s", 3000L to "3s").forEach { (ms, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 10f
                setTextColor(Color.parseColor("#38BDF8"))
                setPadding(dp(5f), dp(2f), dp(5f), dp(2f))
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dp(5f).toFloat()
                }
                background = bg
                setOnClickListener {
                    loopDelayInput.setText("$ms")
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, dp(3f), 0)
                }
                layoutParams = lp
            }
            loopDelayPresetRow.addView(chip)
        }
        root.addView(loopDelayPresetRow)

        // 3. Stop Timer (Tự dừng sau X phút)
        val currentMinutes = activeScript.stopTimerSeconds / 60
        root.addView(createSectionLabel("Hẹn giờ tự động dừng (0 = Chạy liên tục):"))
        val stopTimerInput = createNumberEditText("$currentMinutes")
        root.addView(stopTimerInput)

        val timerPresetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3f), 0, dp(6f))
        }
        listOf(0L to "Tắt", 5L to "5p", 15L to "15p", 30L to "30p", 60L to "1h").forEach { (mins, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 10f
                setTextColor(Color.parseColor("#F59E0B"))
                setPadding(dp(5f), dp(2f), dp(5f), dp(2f))
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dp(5f).toFloat()
                }
                background = bg
                setOnClickListener {
                    stopTimerInput.setText("$mins")
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, dp(3f), 0)
                }
                layoutParams = lp
            }
            timerPresetRow.addView(chip)
        }
        root.addView(timerPresetRow)

        // Buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12f), 0, 0)
        }

        val cancelBtn = TextView(this).apply {
            text = "Đóng"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            setOnClickListener {
                dismissLoopSettingsDialog()
            }
        }
        btnRow.addView(cancelBtn)

        val saveBtn = TextView(this).apply {
            text = "Lưu Cài Đặt"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFFFFF"))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#D97706"))
                cornerRadius = dp(8f).toFloat()
            }
            background = bg
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            setOnClickListener {
                val newRepeat = repeatInput.text.toString().toIntOrNull() ?: activeScript.repeatCount
                val newLoopDelay = loopDelayInput.text.toString().toLongOrNull() ?: activeScript.loopDelayMs
                val newStopMins = stopTimerInput.text.toString().toLongOrNull() ?: currentMinutes

                activeScript = activeScript.copy(
                    repeatCount = newRepeat.coerceAtLeast(0),
                    loopDelayMs = newLoopDelay.coerceAtLeast(0L),
                    stopTimerSeconds = (newStopMins.coerceAtLeast(0L)) * 60L
                )

                serviceScope.launch {
                    scriptRepository.saveOrUpdateScript(activeScript)
                }

                val repStr = if (activeScript.repeatCount == 0) "Vô hạn" else "${activeScript.repeatCount} vòng"
                Toast.makeText(this@FloatingViewService, "Đã lưu: $repStr • Nghỉ ${activeScript.loopDelayMs}ms", Toast.LENGTH_SHORT).show()
                dismissLoopSettingsDialog()
            }
        }
        btnRow.addView(saveBtn)
        root.addView(btnRow)

        loopSettingsDialog = root
        windowManager.addView(root, dialogParams)
    }

    private fun dismissLoopSettingsDialog() {
        loopSettingsDialog?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            loopSettingsDialog = null
        }
    }

    // ==========================================
    // SCRIPT PICKER OVERLAY (📂)
    // ==========================================

    @SuppressLint("ClickableViewAccessibility")
    private fun showScriptPickerOverlay() {
        dismissAllDialogs()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        val dialogParams = WindowManager.LayoutParams(
            dp(280f),
            dp(320f),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = dp(16f).toFloat()
                setStroke(dp(1.5f), Color.parseColor("#00E5FF"))
            }
            background = bg
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            elevation = dp(16f).toFloat()
        }

        val title = TextView(this).apply {
            text = "📂 Chọn Kịch Bản Auto"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(0, 0, 0, dp(8f))
        }
        root.addView(title)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scripts = scriptRepository.scriptsFlow.value
        scripts.forEach { script ->
            val isSelected = script.id == activeScript.id
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val bg = GradientDrawable().apply {
                    setColor(if (isSelected) Color.parseColor("#1E293B") else Color.parseColor("#0F172A"))
                    cornerRadius = dp(8f).toFloat()
                    if (isSelected) {
                        setStroke(dp(1f), Color.parseColor("#00E5FF"))
                    }
                }
                background = bg
                setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(6f))
                }
                layoutParams = lp
                setOnClickListener {
                    if (isPlaying) {
                        stopClicking()
                    }
                    activeScript = script
                    scriptRepository.setActiveScript(script)
                    loadScriptPointsToScreen(script)
                    Toast.makeText(this@FloatingViewService, "Đã đổi sang: ${script.name}", Toast.LENGTH_SHORT).show()
                    dismissScriptPicker()
                }
            }

            val nameView = TextView(this).apply {
                text = script.name
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isSelected) Color.parseColor("#00E5FF") else Color.parseColor("#F8FAFC"))
            }
            item.addView(nameView)

            val repStr = if (script.repeatCount == 0) "Vô hạn" else "${script.repeatCount} vòng"
            val infoView = TextView(this).apply {
                text = "${script.category.iconEmoji} ${script.points.size} điểm • Lặp: $repStr • ${script.loopDelayMs}ms"
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, dp(2f), 0, 0)
            }
            item.addView(infoView)

            listContainer.addView(item)
        }

        scrollView.addView(listContainer)
        root.addView(scrollView)

        val closeBtn = TextView(this).apply {
            text = "Đóng"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10f), 0, 0)
            setOnClickListener {
                dismissScriptPicker()
            }
        }
        root.addView(closeBtn)

        scriptPickerDialog = root
        windowManager.addView(root, dialogParams)
    }

    private fun dismissScriptPicker() {
        scriptPickerDialog?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            scriptPickerDialog = null
        }
    }

    private fun dismissAllDialogs() {
        dismissPointEditor()
        dismissLoopSettingsDialog()
        dismissScriptPicker()
    }

    private fun createSectionLabel(text: String): TextView {
        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(6f), 0, dp(2f))
        }
    }

    private fun createNumberEditText(initialValue: String): EditText {
        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }
        return EditText(this).apply {
            setText(initialValue)
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(8f).toFloat()
                setStroke(dp(1f), Color.parseColor("#334155"))
            }
            background = bg
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        }
    }

    // ==========================================
    // AUTO CLICK SCRIPT EXECUTION ENGINE
    // ==========================================

    private fun updateToolbarUiState(running: Boolean) {
        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        mainHandler.post {
            toolbarActionViews.forEach { it.visibility = if (running) View.GONE else View.VISIBLE }
            toolbarDragHandle?.visibility = if (running) View.GONE else View.VISIBLE

            toolbarContainer?.let { container ->
                if (running) {
                    container.setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
                    val bg = GradientDrawable().apply {
                        setColor(Color.parseColor("#EE0B132B"))
                        cornerRadius = dp(24f).toFloat()
                        setStroke(dp(1.5f), Color.parseColor("#EF4444"))
                    }
                    container.background = bg
                } else {
                    container.setPadding(dp(6f), dp(8f), dp(6f), dp(8f))
                    val bg = GradientDrawable().apply {
                        setColor(Color.parseColor("#EE0B132B"))
                        cornerRadius = dp(24f).toFloat()
                        setStroke(dp(1.5f), Color.parseColor("#00E5FF"))
                    }
                    container.background = bg
                }

                floatingToolbar?.let { view ->
                    toolbarParams?.let { params ->
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (_: Exception) {}
                    }
                }
            }

            targetViews.forEach { holder ->
                holder.timeBadge.visibility = if (running) View.GONE else View.VISIBLE
            }
        }
    }

    private fun toggleClicker(playBtn: TextView) {
        val accessibilityService = AutoClickService.instance
        if (accessibilityService == null) {
            Toast.makeText(
                this,
                "⚠️ Vui lòng cấp quyền Trợ năng (Accessibility Service) trước!",
                Toast.LENGTH_LONG
            ).show()
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
            setTargetsTouchPassThrough(false)
            updateToolbarUiState(false)
            Toast.makeText(this, "Đã tạm dừng kịch bản", Toast.LENGTH_SHORT).show()
        } else {
            dismissAllDialogs()
            startClicking(playBtn)
            playBtn.text = "⏸"
            (playBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#EF4444"))
            setTargetsTouchPassThrough(true)
            updateToolbarUiState(true)
            val repeatText = if (activeScript.repeatCount > 0) "${activeScript.repeatCount} vòng" else "Vô hạn"
            Toast.makeText(this, "Đang chạy '${activeScript.name}' ($repeatText)...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startClicking(playBtn: TextView) {
        isPlaying = true
        _isClicking.value = true
        currentLoopCount = 0
        clickJob?.cancel()

        val startTimestamp = System.currentTimeMillis()

        clickJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive && isPlaying) {
                // Check Stop Timer
                if (activeScript.stopTimerSeconds > 0) {
                    val elapsedSeconds = (System.currentTimeMillis() - startTimestamp) / 1000
                    if (elapsedSeconds >= activeScript.stopTimerSeconds) {
                        mainHandler.post {
                            stopClicking()
                            playBtn.text = "▶"
                            (playBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#10B981"))
                            setTargetsTouchPassThrough(false)
                            updateToolbarUiState(false)
                            Toast.makeText(
                                this@FloatingViewService,
                                "⏰ Đã hết thời gian chạy kịch bản (${activeScript.stopTimerSeconds / 60} phút)!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        break
                    }
                }

                val targets = synchronized(targetViews) { targetViews.sortedBy { it.point.id } }
                if (targets.isEmpty()) {
                    delay(200)
                    continue
                }

                // Execute each point in strict sequential order (1 -> 2 -> 3 -> 4 ...)
                for (holder in targets) {
                    if (!isActive || !isPlaying) break

                    val point = holder.point

                    if (point.delayBeforeMs > 0) {
                        delay(point.delayBeforeMs)
                    }

                    if (!isActive || !isPlaying) break

                    // Calculate exact pixel position of the center dot on screen
                    val screenPos = IntArray(2)
                    holder.view.getLocationOnScreen(screenPos)

                    val clickX = if (screenPos[0] > 0 || screenPos[1] > 0) {
                        screenPos[0].toFloat() + (holder.view.width / 2f)
                    } else {
                        holder.params.x.toFloat() + (holder.view.width / 2f)
                    }

                    val clickY = if (screenPos[0] > 0 || screenPos[1] > 0) {
                        screenPos[1].toFloat() + (holder.discFrame.height / 2f)
                    } else {
                        holder.params.y.toFloat() + (holder.discFrame.height / 2f)
                    }

                    mainHandler.post {
                        animateTargetPulse(holder.pulseRing)
                    }

                    val duration = point.clickDurationMs.coerceIn(10L, 500L)
                    AutoClickService.instance?.dispatchClick(
                        x = clickX,
                        y = clickY,
                        durationMs = duration
                    )

                    clickCount++
                    _clickCountFlow.value = clickCount

                    val waitTime = point.delayAfterMs.coerceAtLeast(5L)
                    delay(waitTime)
                }

                // Delay between loop cycles
                if (activeScript.loopDelayMs > 0) {
                    delay(activeScript.loopDelayMs)
                }

                currentLoopCount++

                // Check Repeat Count Limit (1 = 1 lượt duy nhất không lặp lại)
                if (activeScript.repeatCount > 0 && currentLoopCount >= activeScript.repeatCount) {
                    mainHandler.post {
                        stopClicking()
                        playBtn.text = "▶"
                        (playBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#10B981"))
                        setTargetsTouchPassThrough(false)
                        updateToolbarUiState(false)
                        val completeMsg = if (activeScript.repeatCount == 1) {
                            "🎉 Đã chạy xong 1 lượt theo thứ tự (1 ➔ ${targets.size}) và tự động dừng!"
                        } else {
                            "🎉 Đã hoàn thành chính xác ${activeScript.repeatCount} vòng lặp kịch bản!"
                        }
                        Toast.makeText(
                            this@FloatingViewService,
                            completeMsg,
                            Toast.LENGTH_LONG
                        ).show()
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
        setTargetsTouchPassThrough(false)
        updateToolbarUiState(false)
    }

    override fun onDestroy() {
        stopClicking()
        dismissAllDialogs()
        serviceScope.cancel()

        floatingToolbar?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        targetViews.forEach {
            try {
                windowManager.removeView(it.view)
            } catch (_: Exception) {}
        }
        targetViews.clear()

        _isOverlayActive.value = false
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_SCRIPT_ID = "extra_script_id"

        private val _isOverlayActive = MutableStateFlow(false)
        val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

        private val _isClicking = MutableStateFlow(false)
        val isClicking: StateFlow<Boolean> = _isClicking.asStateFlow()

        private val _clickCountFlow = MutableStateFlow(0L)
        val totalClicks: StateFlow<Long> = _clickCountFlow.asStateFlow()

        fun start(
            context: Context,
            scriptId: String? = null
        ) {
            val intent = Intent(context, FloatingViewService::class.java).apply {
                if (scriptId != null) {
                    putExtra(EXTRA_SCRIPT_ID, scriptId)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingViewService::class.java)
            context.stopService(intent)
        }
    }
}
