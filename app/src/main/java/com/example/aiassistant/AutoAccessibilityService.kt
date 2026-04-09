package com.example.aiassistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

enum class AgentMode { OFF, OBSERVE, ASSIST }

class AutoAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutoAccessibilityService? = null
        private const val PREFS = "ai_assistant_prefs"
        private const val KEY_MODE = "agent_mode"

        fun getMode(context: Context): AgentMode {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, AgentMode.OFF.name) ?: AgentMode.OFF.name
            return try { AgentMode.valueOf(name) } catch (_: Exception) { AgentMode.OFF }
        }

        fun setMode(context: Context, mode: AgentMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_MODE, mode.name).apply()
        }
    }

    private var currentPackage: String? = null
    private var lastScreenState: String? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val mode = getMode(this)
        if (mode == AgentMode.OFF) return

        // Track current package
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackage = event.packageName?.toString()
        }

        // --- OBSERVATION: record user-initiated actions (always, in both modes) ---
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) {
            val pkg = currentPackage ?: return
            val state = lastScreenState ?: return

            scope.launch {
                try {
                    Observer.onUserAction(
                        context = this@AutoAccessibilityService,
                        event = event,
                        currentState = state,
                        packageName = pkg
                    )
                } catch (_: Exception) { }
            }
        }

        // --- SCREEN CHANGE: snapshot + assist ---
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            val root = rootInActiveWindow ?: return
            val pkg = currentPackage

            // Build snapshot synchronously (fixes stale-node issue)
            val snapshot = try {
                UIUtils.snapshotScreen(pkg, root)
            } catch (_: Exception) {
                return
            }

            // Debounce: skip if same screen state
            if (snapshot.stableState == lastScreenState && mode == AgentMode.ASSIST) return
            lastScreenState = snapshot.stableState

            // Only run assist engine in ASSIST mode
            if (mode == AgentMode.ASSIST) {
                scope.launch {
                    try {
                        AssistEngine.handleScreen(
                            service = this@AutoAccessibilityService,
                            context = this@AutoAccessibilityService,
                            snapshot = snapshot
                        )
                    } catch (_: Exception) { }
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
    }
}
