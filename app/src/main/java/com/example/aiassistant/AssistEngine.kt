package com.example.aiassistant

import android.accessibilityservice.AccessibilityService
import android.content.Context

/**
 * Orchestrates the assist pipeline:
 * cooldown -> app exclusion -> pattern match -> safety check -> execute -> log -> overlay
 */
object AssistEngine {

    suspend fun handleScreen(
        service: AccessibilityService,
        context: Context,
        snapshot: ScreenSnapshot
    ) {
        // 1. Debounce
        if (!SafetyChecker.checkCooldown()) return

        // 2. App exclusion
        if (!SafetyChecker.isAppAllowed(context, snapshot.packageName)) return

        // 3. Find best action from patterns or rules
        val command = PatternMatcher.findBestAction(context, snapshot) ?: return

        // 4. Safety check
        if (!SafetyChecker.isActionSafe(command)) {
            DatabaseHelper.logAction(
                context = context,
                packageName = snapshot.packageName,
                state = snapshot.stableState,
                actionType = command.type.name,
                actionDetail = "[BLOCKED] ${command.target}",
                success = false
            )
            return
        }

        // 5. Execute
        val success = ActionExecutor.executeSafe(service, command)

        // 6. Record cooldown
        SafetyChecker.recordActionTime()

        // 7. Log
        DatabaseHelper.logAction(
            context = context,
            packageName = snapshot.packageName,
            state = snapshot.stableState,
            actionType = command.type.name,
            actionDetail = command.target,
            success = success
        )

        // 8. Update overlay
        if (success) {
            OverlayService.updateStatus("${command.type.name}: ${command.target}")
        }
    }
}
