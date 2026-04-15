import Foundation

/// Safety layer that blocks dangerous actions, enforces app exclusions,
/// and provides cooldown between consecutive actions.
/// Direct port of Android `SafetyChecker.kt`.
enum SafetyChecker {

    private static let excludedAppsKey = "excluded_apps"
    private static let cooldownMs: TimeInterval = 0.8  // 800ms
    private static var lastActionTime: Date = .distantPast

    /// Dangerous keywords — never auto-act on elements containing these.
    static let blockedKeywords: Set<String> = [
        "purchase", "buy", "pay", "payment", "checkout",
        "delete", "remove", "uninstall", "format", "erase", "wipe",
        "send money", "transfer funds", "confirm payment", "place order",
        "reset", "factory reset", "clear data",
        "subscribe", "upgrade plan", "billing"
    ]

    // MARK: - Cooldown

    /// Returns `true` if enough time has passed since the last action.
    static func checkCooldown() -> Bool {
        return Date().timeIntervalSince(lastActionTime) >= cooldownMs
    }

    static func recordActionTime() {
        lastActionTime = Date()
    }

    // MARK: - Action Safety

    /// Returns `true` if the action target does NOT contain any blocked keywords.
    static func isActionSafe(_ command: ActionCommand) -> Bool {
        let lower = command.target.lowercased()
        return !blockedKeywords.contains { lower.contains($0) }
    }

    // MARK: - App Exclusions (uses UserDefaults, mirrors SharedPreferences)

    static func isAppAllowed(_ bundleId: String) -> Bool {
        return !getExcludedApps().contains(bundleId)
    }

    static func getExcludedApps() -> Set<String> {
        let arr = UserDefaults.standard.stringArray(forKey: excludedAppsKey) ?? []
        return Set(arr)
    }

    static func setExcludedApps(_ apps: Set<String>) {
        UserDefaults.standard.set(Array(apps), forKey: excludedAppsKey)
    }

    static func addExcludedApp(_ bundleId: String) {
        var current = getExcludedApps()
        current.insert(bundleId)
        setExcludedApps(current)
    }

    static func removeExcludedApp(_ bundleId: String) {
        var current = getExcludedApps()
        current.remove(bundleId)
        setExcludedApps(current)
    }
}
