import Foundation

/// Observes user-initiated actions and records them as patterns.
/// This runs in both OBSERVE and ASSIST modes — always learning.
///
/// On Android, this receives events from the AccessibilityService.
/// On iOS, this must be called explicitly from within the app's own
/// UI event handlers, since cross-app observation is not possible.
enum Observer {

    /// Record a user action.
    ///
    /// - Parameters:
    ///   - actionText: The text/label of the element the user interacted with.
    ///   - eventType: A string describing the interaction ("CLICK", "SCROLL", "TYPE").
    ///   - currentState: The stable structural fingerprint of the current screen.
    ///   - packageName: The bundle identifier of the app being used.
    static func onUserAction(
        actionText: String,
        eventType: String,
        currentState: String,
        packageName: String
    ) {
        PersistenceController.shared.recordPattern(
            state: currentState,
            packageName: packageName,
            actionText: actionText,
            actionType: eventType
        )
    }
}
