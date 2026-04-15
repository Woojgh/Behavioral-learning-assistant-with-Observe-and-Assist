package com.example.aiassistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

enum class AgentMode { OFF, OBSERVE, ASSIST }

class AutoAccessibilityService : AccessibilityService() {

    companion object {
        /**
         * WeakReference prevents a memory leak if the service is destroyed but something
         * still holds a reference to this companion object. External callers treat
         * `instance` as a regular nullable — no API change required.
         */
        private var _instanceRef: WeakReference<AutoAccessibilityService>? = null
        val instance: AutoAccessibilityService?
            get() = _instanceRef?.get()

        private const val PREFS = "ai_assistant_prefs"
        private const val KEY_MODE = "agent_mode"

        /** Latest screen snapshot — read by SystemStateMonitor for context correlation. */
        @Volatile
        var lastSnapshot: ScreenSnapshot? = null

        /** Cached mode — avoids reading SharedPreferences on every event. */
        @Volatile
        var cachedMode: AgentMode = AgentMode.OFF
            private set

        fun getMode(context: Context): AgentMode {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, AgentMode.OFF.name) ?: AgentMode.OFF.name
            val mode = try { AgentMode.valueOf(name) } catch (_: Exception) { AgentMode.OFF }
            cachedMode = mode
            return mode
        }

        fun setMode(context: Context, mode: AgentMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_MODE, mode.name).apply()
            cachedMode = mode
        }
    }

    private var currentPackage: String? = null
    private var lastScreenState: String? = null
    private var lastSnapshotTime = 0L
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var systemStateMonitor: SystemStateMonitor? = null

    /** Prevents coroutine pileup — skip if previous work is still running. */
    private val observeBusy = AtomicBoolean(false)
    private val assistBusy = AtomicBoolean(false)

    /** Minimum ms between snapshot builds (applies to both modes). */
    private val snapshotCooldownMs = 500L

    override fun onServiceConnected() {
        _instanceRef = WeakReference(this)
        // Load cached mode from prefs once at startup
        getMode(this)
        // Start monitoring system setting changes
        systemStateMonitor = SystemStateMonitor(this).also { it.start() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Use cached mode — no disk I/O
        val mode = cachedMode
        if (mode == AgentMode.OFF) return

        // Track current package
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackage = event.packageName?.toString()
        }

        // --- OBSERVATION: record user-initiated actions (OBSERVE mode only) ---
        if (mode == AgentMode.OBSERVE && (
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
        ) {
            val pkg = currentPackage ?: return
            val state = lastScreenState ?: return

            // Extract event data NOW, before the event is recycled by Android.
            // AccessibilityEvent objects are reused after onAccessibilityEvent returns.
            val actionText = extractEventText(event)
            val eventType = event.eventType

            if (actionText != null && observeBusy.compareAndSet(false, true)) {
                scope.launch {
                    try {
                        Observer.onUserActionDirect(
                            context = this@AutoAccessibilityService,
                            actionText = actionText,
                            eventType = eventType,
                            currentState = state,
                            packageName = pkg
                        )
                    } catch (e: Exception) {
                        Logger.logError("Observer.onUserActionDirect failed: ${e.message}")
                    } finally {
                        observeBusy.set(false)
                    }
                }
            }
        }

        // --- SCREEN CHANGE: snapshot + assist ---
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            // Throttle snapshot builds for both modes
            val now = System.currentTimeMillis()
            if (now - lastSnapshotTime < snapshotCooldownMs) return
            lastSnapshotTime = now

            val root = rootInActiveWindow ?: return
            val pkg = currentPackage

            // Build snapshot synchronously (fixes stale-node issue)
            val snapshot = try {
                UIUtils.snapshotScreen(pkg, root)
            } catch (_: Exception) {
                return
            }

            // Update lastSnapshot for SystemStateMonitor context correlation
            lastSnapshot = snapshot

            // Debounce: skip if same screen state
            if (snapshot.stableState == lastScreenState) return
            lastScreenState = snapshot.stableState

            // Check system patterns (runs in both modes, only acts in ASSIST)
            scope.launch {
                try {
                    ScreenReactor.checkAndReact(this@AutoAccessibilityService, snapshot, mode)
                } catch (e: Exception) {
                    Logger.logError("ScreenReactor.checkAndReact failed: ${e.message}")
                }
            }

            // Only run assist engine in ASSIST mode
            if (mode == AgentMode.ASSIST && assistBusy.compareAndSet(false, true)) {
                scope.launch {
                    try {
                        AssistEngine.handleScreen(
                            service = this@AutoAccessibilityService,
                            context = this@AutoAccessibilityService,
                            snapshot = snapshot
                        )
                    } catch (e: Exception) {
                        Logger.logError("AssistEngine.handleScreen failed: ${e.message}")
                    } finally {
                        assistBusy.set(false)
                    }
                }
            }
        }
    }

    /**
     * Extract text from the event SYNCHRONOUSLY before Android recycles it.
     */
    private fun extractEventText(event: AccessibilityEvent): String? {
        event.text?.let { texts ->
            val joined = texts.joinToString(" ").trim()
            if (joined.isNotEmpty()) return joined
        }
        event.contentDescription?.toString()?.let {
            if (it.isNotEmpty()) return it
        }
        return null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        systemStateMonitor?.stop()
        systemStateMonitor = null
        lastSnapshot = null
        _instanceRef = null
        scope.cancel()
    }
}
