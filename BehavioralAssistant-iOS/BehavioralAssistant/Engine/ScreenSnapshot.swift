import Foundation

/// Snapshot of a single UI element — mirrors Android `NodeSnapshot`.
struct NodeSnapshot {
    let text: String?
    let contentDesc: String?
    let className: String?
    let isClickable: Bool
    let isScrollable: Bool
    let isEditable: Bool
}

/// Immutable snapshot of the full screen state — mirrors Android `ScreenSnapshot`.
///
/// On Android, this is built from the live `AccessibilityNodeInfo` tree.
/// On iOS, this would be built from in-app view introspection since
/// iOS does not expose other apps' view hierarchies to third-party apps.
struct ScreenSnapshot {
    let packageName: String      // Bundle identifier (iOS) or package name (Android)
    let stableState: String      // Structural fingerprint: "bundleId:hash"
    let nodes: [NodeSnapshot]
    let textElements: [String]
}

// MARK: - Builder (iOS equivalent of UIUtils.snapshotScreen)

enum ScreenSnapshotBuilder {

    /// Build a ScreenSnapshot from a set of in-app UI element descriptions.
    ///
    /// On Android this walks the AccessibilityNodeInfo tree system-wide.
    /// On iOS this can only operate within the app's own view hierarchy.
    static func build(
        bundleIdentifier: String,
        nodes: [NodeSnapshot]
    ) -> ScreenSnapshot {
        var textElements: [String] = []
        var structParts: [String] = []

        for node in nodes {
            if let t = node.text { textElements.append(t) }
            if let d = node.contentDesc { textElements.append(d) }

            let cls = node.className ?? "?"
            let flags = "\(node.isClickable ? "C" : "")\(node.isScrollable ? "S" : "")\(node.isEditable ? "E" : "")"
            structParts.append("\(cls):\(flags)")
        }

        let structString = structParts.joined(separator: "|")
        let hash = String(UInt32(truncatingIfNeeded: structString.hashValue), radix: 16)
        let stableState = "\(bundleIdentifier):\(hash)"

        return ScreenSnapshot(
            packageName: bundleIdentifier,
            stableState: stableState,
            nodes: nodes,
            textElements: textElements
        )
    }
}
