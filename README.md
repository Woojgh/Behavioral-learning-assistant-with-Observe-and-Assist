# Behavioral Learning Assistant

An Android accessibility service that **learns how you use your phone** by observing your taps, scrolls, and text input — then **replays your patterns hands-free** when you need it.

No cloud. No LLM. All intelligence comes from watching *you*.

---

## How It Works

The app runs as an Android Accessibility Service with two operational modes:

### Observe Mode
The assistant silently watches your interactions across every app on your device. Each time you tap a button, scroll a list, or enter text, it records:

- **What** you interacted with (the text/label of the element)
- **Where** you were (a stable structural fingerprint of the screen layout)
- **Which app** you were in

These observations are stored locally as **patterns** in a Room database. The more you repeat an action on a given screen, the higher its confidence score becomes.

### Assist Mode
When you go hands-free, the assistant takes over. For each screen it encounters, it:

1. Looks up learned patterns for that screen's structural signature
2. Picks the action you perform most frequently (only if observed 3+ times)
3. Runs the action through a safety checker
4. Executes if safe — clicks, scrolls, swipes, or types on your behalf

If no confident learned pattern exists, it falls back to configurable keyword rules (e.g., auto-click "Skip", "Allow", "OK").

---

## Architecture

```
┌──────────────────────────────────────────────────────┐
│              AutoAccessibilityService                │
│  ┌─────────────┐    ┌─────────────────────────────┐  │
│  │  Observer    │    │  AssistEngine               │  │
│  │  (always on) │    │  (ASSIST mode only)         │  │
│  │             │    │                             │  │
│  │  Records    │    │  SafetyChecker              │  │
│  │  user taps, │    │    ↓                        │  │
│  │  scrolls,   │    │  PatternMatcher             │  │
│  │  text input │    │    ↓                        │  │
│  └──────┬──────┘    │  ActionExecutor             │  │
│         │           └──────────────┬──────────────┘  │
│         ↓                          ↓                 │
│  ┌──────────────────────────────────────────────┐    │
│  │              Room Database                    │    │
│  │  UserPatternEntity  RuleEntity  LogEntity     │    │
│  └──────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
```

### Key Components

| File | Purpose |
|------|--------|
| `AutoAccessibilityService.kt` | Core service. Dual-mode event handling (OBSERVE/ASSIST). Builds screen snapshots synchronously to avoid stale-node crashes. |
| `Observer.kt` | Records user-initiated clicks, scrolls, and text input as patterns in the database. Runs in both modes. |
| `AssistEngine.kt` | Orchestrates the assist pipeline: debounce → app exclusion → pattern match → safety check → execute → log → overlay update. |
| `PatternMatcher.kt` | Finds the best action for a screen. Queries learned patterns first (requires 3+ observations), falls back to keyword rules. |
| `SafetyChecker.kt` | Blocks dangerous actions (purchase, delete, pay, etc.), enforces app exclusions, and provides 800ms cooldown between actions. |
| `ActionExecutor.kt` | Executes actions against the live UI tree: click, scroll forward/backward, swipe (gesture-based), and type text. |
| `ScreenSnapshot.kt` | Immutable data model capturing the full UI state. Built synchronously from `AccessibilityNodeInfo` before any async work. |
| `UIUtils.kt` | Builds `ScreenSnapshot` objects and generates stable structural signatures using class names + interactivity flags (not text content). |
| `DatabseHelper.kt` | Room database with entities for user patterns, keyword rules, and action logs. Handles pattern upsert logic. |
| `OverlayService.kt` | Foreground service that shows a floating bubble with the last action taken. |
| `MainActivity.kt` | Control panel: mode selector (OFF → OBSERVE → ASSIST), learning stats, links to all settings screens. |
| `RulesActivity.kt` | Manage keyword-based fallback rules (e.g., auto-click "Skip" with action type CLICK). |
| `LogActivity.kt` | Scrollable history of all actions taken, with timestamps and success/failure indicators. |
| `SafetyActivity.kt` | Block or allow specific apps from being automated. |
| `ActionCommand.kt` | Data model for actions with type enum (CLICK, SCROLL_FORWARD, SCROLL_BACKWARD, SWIPE, TYPE) and target text. |

---

## Screen Identification

The app identifies screens using a **stable structural signature** rather than raw text content. This means:

- A screen's identity is determined by the **types of UI elements** present and their **interactivity flags** (clickable, scrollable, editable)
- Changing text content (timestamps, counters, notifications) does **not** change the screen identity
- The signature format is `packageName:structuralHash`

This allows the assistant to recognize "the same screen" reliably across visits, even when dynamic content changes.

---

## Safety

The assistant includes multiple safety layers:

- **Dangerous keyword blocklist**: Actions targeting elements with words like "purchase", "delete", "pay", "uninstall", "reset", etc. are automatically blocked and logged as `[BLOCKED]`
- **App exclusions**: Users can block specific apps from being automated via Safety Settings
- **Confidence threshold**: Actions are only replayed if they've been observed 3+ times on that exact screen layout
- **Cooldown**: Minimum 800ms between consecutive actions to prevent rapid-fire mistakes
- **Stale node protection**: The UI tree is snapshotted synchronously before any async processing, and action execution fetches a fresh root node with try/catch wrapping

---

## Setup

### Requirements
- Android Studio (Arctic Fox or later)
- Android SDK 26+ (Android 8.0 Oreo)
- JDK 17

### Build & Install
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Run on a device or emulator (API 26+)

### First Launch
1. Open the app
2. Tap **Open Accessibility Settings**
3. Find "AI Assistant" and enable the service
4. Grant overlay permission when prompted
5. Start in **Observe Mode** — use your phone normally and let the assistant learn
6. Switch to **Assist Mode** when you want hands-free automation

---

## Permissions

| Permission | Reason |
|------------|--------|
| `BIND_ACCESSIBILITY_SERVICE` | Core functionality — observing and interacting with the UI |
| `SYSTEM_ALERT_WINDOW` | Floating overlay bubble showing real-time status |
| `FOREGROUND_SERVICE` | Keeps the overlay service alive |

---

## Data Storage

All data is stored locally on-device using Room (SQLite):

- **UserPatternEntity**: Learned behavior patterns with state, action text, type, frequency count, and last-seen timestamp
- **RuleEntity**: User-configurable keyword rules with action type and enabled flag
- **LogEntity**: Full action history with timestamps, package names, action details, and success/failure

No data leaves the device.

---

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Database**: Room 2.6.1 with KSP
- **Async**: Kotlin Coroutines 1.7.3
- **UI**: Programmatic Android views (no XML layouts)
- **Build**: Gradle 8.13 with Kotlin DSL

---

## License

This project is provided as-is for educational and personal use.
