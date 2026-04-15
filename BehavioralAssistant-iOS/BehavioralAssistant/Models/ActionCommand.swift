import Foundation

/// Mirrors the Android `ActionType` enum.
enum ActionType: String, CaseIterable {
    case click = "CLICK"
    case scrollForward = "SCROLL_FORWARD"
    case scrollBackward = "SCROLL_BACKWARD"
    case swipe = "SWIPE"
    case type = "TYPE"

    /// Parse from a stored string (case-insensitive, with aliases).
    static func from(_ raw: String) -> ActionType {
        switch raw.uppercased() {
        case "CLICK":           return .click
        case "SCROLL", "SCROLL_FORWARD": return .scrollForward
        case "SCROLL_BACKWARD": return .scrollBackward
        case "SWIPE":           return .swipe
        case "TYPE":            return .type
        default:                return .click
        }
    }
}

/// Describes an action to perform, with its type and target text.
struct ActionCommand {
    let type: ActionType
    let target: String
}
