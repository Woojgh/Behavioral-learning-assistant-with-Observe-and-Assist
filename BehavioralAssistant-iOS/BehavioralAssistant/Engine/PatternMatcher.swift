import Foundation

/// Finds the best action for a screen by looking up learned user patterns.
/// Falls back to rule-based matching if no confident pattern exists.
/// Direct port of Android `PatternMatcher.kt`.
enum PatternMatcher {

    /// Minimum number of times a pattern must have been observed
    /// before the app will replay it automatically.
    static let minConfidence = 3

    /// Find the best action for the given screen snapshot.
    /// Returns `nil` if no confident action is found.
    static func findBestAction(snapshot: ScreenSnapshot) -> ActionCommand? {
        // 1. Try learned patterns first
        if let patternAction = findFromPatterns(snapshot: snapshot) {
            return patternAction
        }
        // 2. Fall back to rule-based matching
        return findFromRules(snapshot: snapshot)
    }

    // MARK: - Private

    private static func findFromPatterns(snapshot: ScreenSnapshot) -> ActionCommand? {
        let persistence = PersistenceController.shared
        guard let top = persistence.fetchTopPattern(forState: snapshot.stableState) else {
            return nil
        }

        // Only act on high-confidence patterns
        guard top.count >= Int32(minConfidence) else { return nil }

        // Verify the target text still exists on the current screen
        let actionText = top.actionText ?? ""
        let targetExists = snapshot.textElements.contains {
            $0.caseInsensitiveCompare(actionText) == .orderedSame
        }
        guard targetExists else { return nil }

        let type = ActionType.from(top.actionType ?? "CLICK")
        return ActionCommand(type: type, target: actionText)
    }

    private static func findFromRules(snapshot: ScreenSnapshot) -> ActionCommand? {
        let rules = PersistenceController.shared.fetchEnabledRules()
        guard !rules.isEmpty else { return nil }

        for element in snapshot.textElements {
            let lower = element.lowercased()
            for rule in rules {
                let keyword = (rule.keyword ?? "").lowercased()
                if lower.contains(keyword) {
                    let type = ActionType.from(rule.actionType ?? "CLICK")
                    return ActionCommand(type: type, target: element)
                }
            }
        }
        return nil
    }
}
