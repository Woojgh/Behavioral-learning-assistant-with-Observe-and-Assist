package com.example.aiassistant

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*

/**
 * Overlay service that shows a draggable floating bubble.
 * Tapping the bubble opens/closes a mini control panel with:
 *   - Live AI status text
 *   - Mode toggle buttons (Off / Observe / Assist)
 *   - Shortcut to Accessibility Settings
 *   - Button to open the main app
 */
class OverlayService : Service() {

    companion object {
        var instance: OverlayService? = null
        private const val CHANNEL_ID = "ai_overlay_channel"
        private const val NOTIFICATION_ID = 1

        /** Called from any thread to push a status message to the overlay. */
        fun updateStatus(text: String) {
            instance?.postStatus(text)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null

    // Bubble
    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // Expanded panel
    private var panelView: LinearLayout? = null
    private var panelStatusTv: TextView? = null
    private val panelModeBtns = Array<Button?>(3) { null } // [OFF, OBSERVE, ASSIST]

    private var lastStatus = "AI Ready"

    // Drag tracking
    private var dragInitX = 0
    private var dragInitY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f
    private var isDragging = false

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        mainHandler.post { showBubble() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        safeRemove(bubbleView)
        safeRemove(panelView)
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun postStatus(text: String) {
        lastStatus = text
        mainHandler.post {
            panelStatusTv?.text = text
            updateBubbleAppearance()
        }
    }

    // -------------------------------------------------------------------------
    // Bubble
    // -------------------------------------------------------------------------

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 200
        }
        bubbleParams = params

        val bubble = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            gravity = Gravity.CENTER
        }
        bubbleView = bubble
        updateBubbleAppearance()

        bubble.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragInitX = params.x
                    dragInitY = params.y
                    dragTouchX = event.rawX
                    dragTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragTouchX).toInt()
                    val dy = (event.rawY - dragTouchY).toInt()
                    if (!isDragging && (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = dragInitX + dx
                        params.y = dragInitY + dy
                        try { windowManager?.updateViewLayout(v, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) togglePanel()
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(bubble, params)
    }

    private fun updateBubbleAppearance() {
        val mode = AutoAccessibilityService.cachedMode
        bubbleView?.apply {
            text = when (mode) {
                AgentMode.OFF     -> "AI\nOFF"
                AgentMode.OBSERVE -> "AI\nOBS"
                AgentMode.ASSIST  -> "AI\nAST"
            }
            setBackgroundColor(modeColor(mode))
        }
    }

    // -------------------------------------------------------------------------
    // Panel
    // -------------------------------------------------------------------------

    private fun togglePanel() {
        if (panelView != null) dismissPanel() else showPanel()
    }

    private fun showPanel() {
        val wm = windowManager ?: return
        val bp = bubbleParams ?: return

        val panelParams = WindowManager.LayoutParams(
            dp(270),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = maxOf(0, bp.x - dp(270) + dp(80))
            y = bp.y + dp(75)
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(240, 22, 22, 22))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // -- Header: title + close button --
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "AI Assistant"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.argb(200, 180, 180, 180))
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener { dismissPanel() }
        })
        panel.addView(header, fullWidthWrap().apply { bottomMargin = dp(10) })

        // -- Status section --
        panel.addView(sectionLabel("Status"))
        val statusTv = TextView(this).apply {
            text = lastStatus
            textSize = 13f
            setTextColor(Color.argb(230, 80, 210, 130))
            setPadding(0, dp(2), 0, dp(12))
        }
        panelStatusTv = statusTv
        panel.addView(statusTv, fullWidthWrap())

        // -- Mode section --
        panel.addView(sectionLabel("Mode", bottomPad = dp(6)))

        val currentMode = AutoAccessibilityService.cachedMode
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val modeList = listOf(
            AgentMode.OFF     to "Off",
            AgentMode.OBSERVE to "Observe",
            AgentMode.ASSIST  to "Assist"
        )

        modeList.forEachIndexed { i, (mode, label) ->
            val btn = Button(this).apply {
                text = label
                textSize = 10f
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setBackgroundColor(modeBtnColor(mode, active = currentMode == mode))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply { if (i < 2) marginEnd = dp(4) }

                setOnClickListener {
                    AutoAccessibilityService.setMode(this@OverlayService, mode)
                    updateBubbleAppearance()
                    // Refresh all mode button highlights in-place
                    modeList.forEachIndexed { j, (m, _) ->
                        panelModeBtns[j]?.setBackgroundColor(modeBtnColor(m, active = m == mode))
                    }
                }
            }
            panelModeBtns[i] = btn
            modeRow.addView(btn)
        }
        panel.addView(modeRow, fullWidthWrap().apply { bottomMargin = dp(10) })

        // -- Service status + toggle --
        // Android doesn't allow programmatic enable/disable of accessibility services;
        // the best we can do is show connection state and deep-link to the exact service
        // page in Settings so the user can flip the toggle themselves in one tap.
        panel.addView(sectionLabel("Accessibility Service", bottomPad = dp(4)))
        val svcConnected = AutoAccessibilityService.instance != null
        panel.addView(TextView(this).apply {
            text = if (svcConnected) "● Connected" else "● Disconnected"
            textSize = 12f
            setTextColor(
                if (svcConnected) Color.argb(230, 80, 210, 130)
                else Color.argb(230, 220, 80, 80)
            )
            setPadding(0, 0, 0, dp(6))
        }, fullWidthWrap())
        val svcBtnLabel = if (svcConnected) "Disable Service →" else "Enable Service →"
        panel.addView(actionButton(svcBtnLabel) {
            // Deep-link directly to this service's toggle in accessibility settings
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val bundle = Bundle()
            bundle.putString(
                ":settings:fragment_args_key",
                "$packageName/${AutoAccessibilityService::class.java.name}"
            )
            intent.putExtra(":settings:show_fragment_args", bundle)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            dismissPanel()
        })

        // -- Open App --
        panel.addView(actionButton("Open App") {
            startActivity(Intent(this@OverlayService, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            dismissPanel()
        })

        wm.addView(panel, panelParams)
        panelView = panel
    }

    private fun dismissPanel() {
        safeRemove(panelView)
        panelView = null
        panelStatusTv = null
        panelModeBtns.fill(null)
    }

    // -------------------------------------------------------------------------
    // Color helpers
    // -------------------------------------------------------------------------

    private fun modeColor(mode: AgentMode): Int = when (mode) {
        AgentMode.OFF     -> Color.argb(215, 70, 70, 70)
        AgentMode.OBSERVE -> Color.argb(215, 20, 90, 180)
        AgentMode.ASSIST  -> Color.argb(215, 20, 140, 60)
    }

    private fun modeBtnColor(mode: AgentMode, active: Boolean): Int =
        if (active) modeColor(mode) else Color.argb(180, 50, 50, 50)

    // -------------------------------------------------------------------------
    // View helpers
    // -------------------------------------------------------------------------

    private fun actionButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        setBackgroundColor(Color.argb(180, 50, 50, 50))
        setTextColor(Color.WHITE)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setOnClickListener { onClick() }
        layoutParams = fullWidthWrap().apply { bottomMargin = dp(6) }
    }

    private fun sectionLabel(text: String, bottomPad: Int = 0) = TextView(this).apply {
        this.text = text
        textSize = 10f
        setTextColor(Color.argb(160, 170, 170, 170))
        if (bottomPad > 0) setPadding(0, 0, 0, bottomPad)
    }

    private fun fullWidthWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun safeRemove(view: View?) {
        view?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "AI Assistant Overlay", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows AI Assistant overlay status" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AI Assistant")
                .setContentText("Overlay active \u2014 tap bubble to control")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("AI Assistant")
                .setContentText("Overlay active \u2014 tap bubble to control")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .build()
        }
    }
}
