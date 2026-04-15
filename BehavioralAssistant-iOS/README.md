# Behavioral Learning Assistant — iOS Port

An iOS (SwiftUI) port of the [Android Behavioral Learning Assistant](../README.md) that learns user behavior patterns and replays them.

---

## ⚠️ Platform Limitations

The original Android app relies on **Accessibility Services**, which allow system-wide observation and control of any app's UI. **iOS does not provide equivalent capabilities to third-party apps.**

Specifically, iOS sandboxing prevents:
- **Cross-app UI observation**: Cannot watch taps/scrolls/text input in other apps
- **Cross-app UI control**: Cannot programmatically click, scroll, or type in other apps
- **System-wide accessibility services**: No third-party equivalent to Android's `AccessibilityService`
- **Floating overlays**: Cannot display windows over other apps (no `SYSTEM_ALERT_WINDOW` equivalent)

### What IS ported
- ✅ All data models (ActionCommand, ScreenSnapshot, entities)
- ✅ All business logic (SafetyChecker, PatternMatcher, Observer, AssistEngine)
- ✅ All UI screens (Main, Rules, Log, Safety)
- ✅ Local database (Core Data replacing Room)
- ✅ User preferences (UserDefaults replacing SharedPreferences)
- ✅ Same architecture and pipeline: Observer → PatternMatcher → SafetyChecker → ActionExecutor

### What is adapted
- `ActionExecutor`: Logs actions instead of performing system-wide UI interactions
- `OverlayService`: Replaced with an in-app status banner in MainView
- `AutoAccessibilityService`: No direct equivalent; the engine can be triggered from in-app events

---

## Architecture

```
BehavioralAssistant-iOS/
├── BehavioralAssistant.xcodeproj/
└── BehavioralAssistant/
    ├── BehavioralAssistantApp.swift          # @main entry point
    ├── Models/
    │   ├── ActionCommand.swift               # ActionType enum + ActionCommand struct
    │   ├── Persistence.swift                 # Core Data stack (replaces Room DatabaseHelper)
    │   └── BehavioralAssistant.xcdatamodeld/ # Core Data model (3 entities)
    ├── Engine/
    │   ├── ScreenSnapshot.swift              # Immutable screen state model
    │   ├── Observer.swift                    # Records user actions as patterns
    │   ├── PatternMatcher.swift              # Best-action lookup (3+ confidence threshold)
    │   ├── SafetyChecker.swift               # Blocked keywords, app exclusions, cooldown
    │   ├── AssistEngine.swift                # Full assist pipeline orchestration
    │   └── ActionExecutor.swift              # Action execution (within-app only on iOS)
    └── Views/
        ├── MainView.swift                    # Control panel: mode selector, stats, navigation
        ├── RulesView.swift                   # CRUD for keyword-based fallback rules
        ├── LogView.swift                     # Action history with timestamps
        └── SafetyView.swift                  # App exclusion toggles
```

## Component Mapping (Android → iOS)

| Android | iOS |
|---------|-----|
| Kotlin | Swift |
| Room (SQLite) | Core Data |
| SharedPreferences | UserDefaults |
| Kotlin Coroutines | Swift async/await + GCD |
| AccessibilityService | Not available (sandboxed) |
| Activities | SwiftUI Views in NavigationStack |
| Programmatic Android Views | SwiftUI declarative views |
| OverlayService (floating window) | In-app status banner |
| Gradle (build.gradle.kts) | Xcode project (project.pbxproj) |

---

## Setup

### Requirements
- Xcode 15.0 or later
- iOS 16.0+ deployment target
- Swift 5.0

### Build & Run
1. Open `BehavioralAssistant.xcodeproj` in Xcode
2. Select an iPhone simulator or device
3. Build and run (⌘R)

### First Launch
1. The app seeds default keyword rules (skip, allow, ok, accept, continue, dismiss)
2. Use the mode selector to cycle: OFF → OBSERVE → ASSIST → OFF
3. Manage rules via "Manage Rules"
4. View action history via "View History"
5. Configure app exclusions via "Safety Settings"

---

## Data Storage

All data is stored locally on-device using Core Data (SQLite):

- **UserPatternEntity**: Learned behavior patterns with state, action text, type, frequency count, and last-seen timestamp
- **RuleEntity**: User-configurable keyword rules with action type and enabled flag
- **LogEntity**: Full action history with timestamps, bundle identifiers, action details, and success/failure

No data leaves the device.

---

## Safety

The assistant includes the same safety layers as the Android version:

- **Dangerous keyword blocklist**: Actions targeting elements with words like "purchase", "delete", "pay", "uninstall", "reset", etc. are blocked
- **App exclusions**: Users can block specific bundle identifiers from automation
- **Confidence threshold**: Actions only replay if observed 3+ times on the same screen layout
- **Cooldown**: 800ms minimum between consecutive actions

---

## License

Same as the parent project — provided as-is for educational and personal use.
