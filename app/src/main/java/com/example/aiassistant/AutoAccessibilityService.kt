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
            // Invalidate debounce and re-run pipeline immediately so the user
            // doesn't have to navigate away / toggle the accessibility service
            // for the new mode to apply to the screen already on display.
            instance?.onModeChanged(mode)
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

    /**
     * How long the user must be idle (no taps/scrolls/text input) before the
     * assistant acts in Assist mode. Prevents the assistant from firing while
     * the user is actively using the screen.
     */
    private val userIdleThresholdMs = 1_500L

    /** Timestamp of the last user-initiated interaction event (all modes). */
    @Volatile private var lastUserInteractionTime = 0L

    /** Pending debounced jobs — cancelled and rescheduled on each new screen change. */
    private var pendingAssistJob: Job? = null
    private var pendingReactorJob: Job? = null

    /**
     * Set just before the assistant executes an action so that the observation
     * block doesn't record the assistant's own accessibility events as patterns.
     */
    @Volatile private var suppressLearningUntil = 0L

    /**
     * Tracks the last action the assistant took. If the user interacts with the
     * same screen within [correctionWindowMs], it's treated as a correction and
     * that pattern's confidence is decremented.
     */
    @Volatile private var lastAssistActionState: String? = null
    @Volatile private var lastAssistActionTarget: String? = null
    @Volatile private var lastAssistActionTime = 0L
    private val correctionWindowMs = 3_000L

    override fun onServiceConnected() {
        _instanceRef = WeakReference(this)
        // Load cached mode from prefs once at startup
        getMode(this)
        // Start monitoring system setting changes
        systemStateMonitor = SystemStateMonitor(this).also { it.start() }
        // Notify overlay so the bubble colour updates immediately
        OverlayService.refreshServiceState()
        // Decay patterns that haven't been observed in 30+ days.
        scope.launch {
            try {
                DatabaseHelper.decayOldPatterns(this@AutoAccessibilityService)
            } catch (e: Exception) {
                Logger.logError("Pattern decay failed: ${e.message}")
            }
        }
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

        // --- INTERACTION TRACKING: update idle timer in all modes ---
        // This must run in both OBSERVE and ASSIST so the idle-delay logic works.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) {
            val interactionTime = System.currentTimeMillis()
            lastUserInteractionTime = interactionTime

            // --- OBSERVATION: record user-initiated actions ---
            // Active in OBSERVE mode always. Active in ASSIST mode unless the
            // assistant is currently executing (to avoid recording its own events).
            val assistActing = interactionTime <= suppressLearningUntil
            if (!assistActing && (mode == AgentMode.OBSERVE || mode == AgentMode.ASSIST)) {
                val pkg = currentPackage ?: return
                val state = lastScreenState ?: return

                // --- FEEDBACK LOOP: correction detection ---
                // If the user acts on the same screen the assistant just acted on,
                // within the correction window, treat it as negative feedback.
                if (mode == AgentMode.ASSIST) {
                    val assistState = lastAssistActionState
                    val assistTarget = lastAssistActionTarget
                    if (assistState != null && assistTarget != null &&
                        state == assistState &&
                        interactionTime - lastAssistActionTime < correctionWindowMs
                    ) {
                        // Clear immediately so rapid taps only count once.
                        lastAssistActionState = null
                        lastAssistActionTarget = null
                        scope.launch {
                            try {
                                DatabaseHelper.recordCorrection(
                                    context = this@AutoAccessibilityService,
                                    state = assistState,
                                    actionText = assistTarget
                                )
                                DatabaseHelper.logAction(
                                    context = this@AutoAccessibilityService,
                                    packageName = pkg,
                                    state = assistState,
                                    actionType = "CORRECTION",
                                    actionDetail = "[-1] $assistTarget",
                                    success = true
                                )
                            } catch (e: Exception) {
                                Logger.logError("Correction recording failed: ${e.message}")
                            }
                        }
                    }
                }

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

            // Debounce on looseState — ignore changes that only affect non-interactable nodes
            // (e.g. a new ad banner appearing doesn't force a new assist cycle).
            if (snapshot.looseState == lastScreenState) return
            lastScreenState = snapshot.looseState

            // Check system patterns and run assist engine after user goes idle.
            // Cancel any previously pending action so we always wait for a fresh
            // idle period from the most recent screen change.
            val capturedSnapshot = snapshot

            pendingReactorJob?.cancel()
            pendingReactorJob = scope.launch {
                delay(userIdleThresholdMs)
                // Secondary guard: abort if the user interacted during the delay.
                if (System.currentTimeMillis() - lastUserInteractionTime < userIdleThresholdMs) return@launch
                try {
                    ScreenReactor.checkAndReact(this@AutoAccessibilityService, capturedSnapshot, mode)
                } catch (e: Exception) {
                    Logger.logError("ScreenReactor.checkAndReact failed: ${e.message}")
                }
            }

            // Only run assist engine in ASSIST mode
            if (mode == AgentMode.ASSIST) {
                pendingAssistJob?.cancel()
                pendingAssistJob = scope.launch {
                    try {
                        // Peek at what action we'd take NOW (before the idle delay)
                        // so we can show a preview the user can cancel.
                        val pending = AssistEngine.findPendingAction(
                            context = this@AutoAccessibilityService,
                            snapshot = capturedSnapshot
                        ) ?: return@launch

                        OverlayService.showPendingAction(pending)

                        delay(userIdleThresholdMs)
                        // Secondary guard: abort if the user interacted during the delay.
                        if (System.currentTimeMillis() - lastUserInteractionTime < userIdleThresholdMs) return@launch

                        if (assistBusy.compareAndSet(false, true)) {
                            try {
                                suppressLearningUntil = System.currentTimeMillis() + 1_000L
                                val executed = AssistEngine.executeAction(
                                    service = this@AutoAccessibilityService,
                                    context = this@AutoAccessibilityService,
                                    snapshot = capturedSnapshot,
                                    command = pending
                                )
                                if (executed != null) {
                                    lastAssistActionState = capturedSnapshot.looseState
                                    lastAssistActionTarget = executed.target
                                    lastAssistActionTime = System.currentTimeMillis()
                                }
                            } catch (e: Exception) {
                                Logger.logError("AssistEngine.executeAction failed: ${e.message}")
                            } finally {
                                assistBusy.set(false)
                            }
                        }
                    } finally {
                        // Always clear the bubble preview — whether executed, cancelled, or errored.
                        OverlayService.clearPendingAction()
                    }
                }
            }
        }
    }

    /**
     * Called when the user toggles OFF <-> OBSERVE <-> ASSIST while the service
     * is already running. Without this, the stableState debounce in
     * onAccessibilityEvent suppresses re-processing of the current screen,
     * making it look like ASSIST "isn't doing anything" until the next screen
     * change — the exact symptom that disappeared after restarting the service.
     */
    fun onModeChanged(newMode: AgentMode) {
        // Reset debounce so the next accessibility event will reprocess.
        lastScreenState = null
        lastSnapshotTime = 0L

        // Cancel any assist/reactor actions pending from the previous mode.
        pendingAssistJob?.cancel()
        pendingAssistJob = null
        pendingReactorJob?.cancel()
        pendingReactorJob = null

        if (newMode == AgentMode.OFF) return

        // Proactively run the new-mode pipeline on the screen already showing.
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return
        val pkg = currentPackage
        val snapshot = try {
            UIUtils.snapshotScreen(pkg, root)
        } catch (_: Exception) {
            return
        }

        lastSnapshot = snapshot
        lastScreenState = snapshot.stableState

        // Also apply the idle delay here — the user just tapped the mode toggle,
        // so they're actively interacting. Wait for them to stop before acting.
        val capturedSnapshot = snapshot

        pendingReactorJob?.cancel()
        pendingReactorJob = scope.launch {
            delay(userIdleThresholdMs)
            if (System.currentTimeMillis() - lastUserInteractionTime < userIdleThresholdMs) return@launch
            try {
                ScreenReactor.checkAndReact(this@AutoAccessibilityService, capturedSnapshot, newMode)
            } catch (e: Exception) {
                Logger.logError("ScreenReactor.checkAndReact failed after mode change: ${e.message}")
            }
        }

        if (newMode == AgentMode.ASSIST) {
            pendingAssistJob?.cancel()
            pendingAssistJob = scope.launch {
                try {
                    val pending = AssistEngine.findPendingAction(
                        context = this@AutoAccessibilityService,
                        snapshot = capturedSnapshot
                    ) ?: return@launch

                    OverlayService.showPendingAction(pending)

                    delay(userIdleThresholdMs)
                    if (System.currentTimeMillis() - lastUserInteractionTime < userIdleThresholdMs) return@launch

                    if (assistBusy.compareAndSet(false, true)) {
                        try {
                            suppressLearningUntil = System.currentTimeMillis() + 1_000L
                            val executed = AssistEngine.executeAction(
                                service = this@AutoAccessibilityService,
                                context = this@AutoAccessibilityService,
                                snapshot = capturedSnapshot,
                                command = pending
                            )
                            if (executed != null) {
                                lastAssistActionState = capturedSnapshot.looseState
                                lastAssistActionTarget = executed.target
                                lastAssistActionTime = System.currentTimeMillis()
                            }
                        } catch (e: Exception) {
                            Logger.logError("AssistEngine.executeAction failed after mode change: ${e.message}")
                        } finally {
                            assistBusy.set(false)
                        }
                    }
                } finally {
                    OverlayService.clearPendingAction()
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

    /**
     * Called by [OverlayService] when the user taps the bubble during a pending action.
     * Cancels the queued job; the job's finally block clears the overlay preview.
     */
    fun cancelPendingAssist() {
        pendingAssistJob?.cancel()
        pendingAssistJob = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        systemStateMonitor?.stop()
        systemStateMonitor = null
        lastSnapshot = null
        _instanceRef = null
        scope.cancel()
        // Notify overlay so the bubble colour updates immediately
        OverlayService.refreshServiceState()
    }
}
