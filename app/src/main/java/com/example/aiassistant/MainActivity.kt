package com.example.aiassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_NOTIFICATIONS_CODE = 10_031
    }

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var modeButton: Button
    private lateinit var overlayButton: Button
    private lateinit var remoteSkipToggleButton: Button
    private lateinit var watchTestButton: Button
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Seed default rules on first launch
        scope.launch {
            withContext(Dispatchers.IO) { DatabaseHelper.seedDefaultRules(this@MainActivity) }
        }

        val scroll = ScrollView(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "AI Assistant"
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        // Service & mode status
        statusText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        layout.addView(statusText)

        // Learning stats
        statsText = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        layout.addView(statsText)

        // --- Mode Cycle Button: OFF -> OBSERVE -> ASSIST -> REMOTE_SKIP -> OFF ---
        modeButton = Button(this).apply {
            setOnClickListener {
                val current = AutoAccessibilityService.getMode(this@MainActivity)
                val next = when (current) {
                    AgentMode.OFF -> AgentMode.OBSERVE
                    AgentMode.OBSERVE -> AgentMode.ASSIST
                    AgentMode.ASSIST -> AgentMode.REMOTE_SKIP
                    AgentMode.REMOTE_SKIP -> AgentMode.OFF
                }
                AutoAccessibilityService.setMode(this@MainActivity, next)
                if (next == AgentMode.ASSIST || next == AgentMode.REMOTE_SKIP) {
                    maybeRequestNotificationPermission()
                }
                refreshStatus()
            }
        }
        layout.addView(modeButton, buttonParams())

        // --- Accessibility Settings ---
        layout.addView(Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }, buttonParams())

        // --- Overlay Toggle ---
        overlayButton = Button(this).apply {
            text = "Enable Overlay"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    toggleOverlay()
                }
            }
        }
        layout.addView(overlayButton, buttonParams())

        // --- Remote Skip Confirmation Toggle ---
        remoteSkipToggleButton = Button(this).apply {
            setOnClickListener {
                val enabled = RemoteSkipController.isRemoteSkipEnabled(this@MainActivity)
                RemoteSkipController.setRemoteSkipEnabled(this@MainActivity, !enabled)
                refreshStatus()
            }
        }
        layout.addView(remoteSkipToggleButton, buttonParams())

        // --- Watch Prompt Test ---
        watchTestButton = Button(this).apply {
            text = "Test Watch Prompt"
            setOnClickListener {
                maybeRequestNotificationPermission()
                RemoteSkipController.showWatchPromptTest(this@MainActivity)
            }
        }
        layout.addView(watchTestButton, buttonParams())
        // --- Safety Settings ---
        layout.addView(Button(this).apply {
            text = "Safety Settings"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SafetyActivity::class.java))
            }
        }, buttonParams())

        // --- Manage Rules ---
        layout.addView(Button(this).apply {
            text = "Manage Rules"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, RulesActivity::class.java))
            }
        }, buttonParams())

        // --- View History ---
        layout.addView(Button(this).apply {
            text = "View History"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LogActivity::class.java))
            }
        }, buttonParams())

        // --- View Patterns ---
        layout.addView(Button(this).apply {
            text = "View Patterns"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, PatternsActivity::class.java))
            }
        }, buttonParams())

        // --- System Patterns ---
        layout.addView(Button(this).apply {
            text = "System Patterns"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SystemPatternsActivity::class.java))
            }
        }, buttonParams())

        scroll.addView(layout)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        val mode = AutoAccessibilityService.getMode(this)
        if (mode == AgentMode.ASSIST || mode == AgentMode.REMOTE_SKIP) {
            maybeRequestNotificationPermission()
        }
        refreshStatus()
        loadStats()
    }

    private fun refreshStatus() {
        val serviceConnected = AutoAccessibilityService.instance != null
        val mode = AutoAccessibilityService.getMode(this)

        val serviceLabel = if (serviceConnected) "Service: Connected" else "Service: Disconnected"
        val notificationsLabel = if (!hasNotificationPermission()) " | Notifications: OFF" else ""
        statusText.text = "$serviceLabel  |  Mode: ${mode.name}$notificationsLabel"

        modeButton.text = when (mode) {
            AgentMode.OFF -> "Turn ON (Observe Mode)"
            AgentMode.OBSERVE -> "Switch to Assist Mode"
            AgentMode.ASSIST -> "Switch to Remote Skip Mode"
            AgentMode.REMOTE_SKIP -> "Turn OFF"
        }

        val overlayAllowed = Settings.canDrawOverlays(this)
        overlayButton.text = if (overlayAllowed) "Overlay (tap to toggle)" else "Enable Overlay"

        val remoteSkipEnabled = RemoteSkipController.isRemoteSkipEnabled(this)
        remoteSkipToggleButton.text = if (remoteSkipEnabled) {
            "Remote Skip Confirm: ON (tap to disable)"
        } else {
            "Remote Skip Confirm: OFF (tap to enable)"
        }
    }

    private fun loadStats() {
        scope.launch {
            val db = DatabaseHelper.getDB(this@MainActivity)
            val patterns = withContext(Dispatchers.IO) { db.userPatternDao().totalPatterns() }
            val apps = withContext(Dispatchers.IO) { db.userPatternDao().distinctApps() }
            statsText.text = "$patterns patterns learned across $apps apps"
        }
    }

    private fun toggleOverlay() {
        val intent = Intent(this, OverlayService::class.java)
        // Derive running state from the live service instance rather than a
        // local boolean that resets on Activity recreation.
        if (OverlayService.instance != null) {
            stopService(intent)
        } else {
            startForegroundService(intent)
        }
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 16
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission()) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS_CODE
        )
    }
}
