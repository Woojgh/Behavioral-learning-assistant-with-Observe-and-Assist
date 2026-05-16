import Foundation

/// Orchestrates the assist pipeline:
/// cooldown → app exclusion → pattern match → safety check → execute → log → status update.
/// Direct port of Android `AssistEngine.kt`.
///
/// On Android, this is triggered by `AutoAccessibilityService` on every screen change.
/// On iOS, it can be triggered manually or from in-app navigation events.
enum AssistEngine {

    /// The last status message from the engine, observable by views.
    @MainActor static var lastStatus: String = "Ready"

    /// Handle a screen snapshot through the full assist pipeline.
    static func handleScreen(snapshot: ScreenSnapshot) {
        let persistence = PersistenceController.shared

        // 1. Debounce / cooldown
        guard SafetyChecker.checkCooldown() else { return }

        // 2. App exclusion
        guard SafetyChecker.isAppAllowed(snapshot.packageName) else { return }

        // 3. Find best action from patterns or rules
        guard let command = PatternMatcher.findBestAction(snapshot: snapshot) else { return }

        // 4. Safety check
        if !SafetyChecker.isActionSafe(command) {
            persistence.logAction(
                packageName: snapshot.packageName,
                state: snapshot.stableState,
                actionType: command.type.rawValue,
                actionDetail: "[BLOCKED] \(command.target)",
                success: false
            )
            return
        }

        // 5. Execute
        let success = ActionExecutor.executeSafe(command: command)

        // 6. Record cooldown
        SafetyChecker.recordActionTime()

        // 7. Log
        persistence.logAction(
            packageName: snapshot.packageName,
            state: snapshot.stableState,
            actionType: command.type.rawValue,
            actionDetail: command.target,
            success: success
        )

        // 8. Update status (replaces Android OverlayService.updateStatus)
        if success {
            let status = "\(command.type.rawValue): \(command.target)"
            DispatchQueue.main.async {
                Task { @MainActor in
                    lastStatus = status
                }
            }
        }
    }
}
