# Behavioral Learning Assistant

An Android app that watches how you use your phone — then does it for you.

No cloud. No LLM. No programming required. It learns by watching you, and it gets better the more you use it.

---

## Why This Exists

Every day you do the same things on your phone: dismiss cookie banners, skip ad screens, tap "Allow" on permission dialogs, lower the volume when an ad starts playing, close pop-ups. You do these things without thinking — but you still have to do them.

This app learns those patterns and handles them automatically, especially when your hands are busy.

---

## Real-World Use Cases

### Hands-Free Phone Operation
You're cooking, driving, working out, or carrying groceries. Your phone keeps interrupting with dialogs that need a tap. The assistant handles them — it already knows what you'd click because it watched you do it before.

### Accessibility for Motor Impairments
Someone with limited hand mobility can have a caregiver use the phone normally for a day. The app learns the caregiver's patterns, then replays them — giving the user independent control without needing to precisely tap small UI targets.

### Repetitive Dialog Dismissal
Cookie consent banners. "Rate this app" pop-ups. Permission requests. Onboarding screens you've seen a hundred times. The app clicks through all of them once it's seen you do it 3 times on the same screen.

### Context-Aware System Settings
The app correlates **what's on your screen** with **system changes you make**. If you always lower the volume when an ad appears ("Sponsored", "Visit Advertiser"), it learns that. If you raise brightness in a specific app, it learns that too. It tracks:

- **Volume** (media, ring, alarm, notification)
- **Screen brightness**
- **Auto-rotate**
- **Ringer mode** (silent, vibrate, normal)

After 3 observations of the same change on the same screen, it does it automatically in Assist mode.

### Elderly & Non-Technical Users
A family member sets up the phone, uses it normally for a few days. The app builds a map of "what to do on every screen." The actual user then gets guided through their phone without needing to remember which button does what.

---

## How It Works

The app runs as an Android Accessibility Service with two modes:

### Observe Mode (Learning)
Silently watches everything you do across all apps:
- Every tap, scroll, and text input is recorded
- Every screen layout is fingerprinted by its structure (not its text, so dynamic content doesn't break recognition)
- Every system setting change (volume, brightness, etc.) is tagged with what was on screen at the time

All data stays on your device. Nothing is uploaded anywhere.

### Assist Mode (Acting)
Replays your patterns when it recognizes a screen:
1. Identifies the current screen by its structural fingerprint
2. Looks up what you usually do on this screen
3. Checks safety rules (won't click "Purchase", "Delete", "Pay", etc.)
4. Acts — taps, scrolls, swipes, types, or adjusts system settings

If it hasn't seen you on this screen enough times (< 3), it does nothing. It never guesses.

---

## What Makes This Different

| Feature | This App | Tasker / MacroDroid |
|---------|----------|--------------------|
| Setup | Use your phone normally | Write rules/scripts manually |
| Triggers | Screen content + layout | App launch, time, location |
| Learning | Automatic from observation | None — fully manual |
| System correlation | "Lower volume when ad keywords appear" | "Lower volume when app opens" |
| Screen recognition | Structural fingerprinting | Not available |

The key difference: existing automation tools trigger on **app-level events** (app opened, time of day). This app triggers on **screen-level content** — it knows *which screen within an app* you're on, and *what text is visible*.

---

## Safety

The assistant will not:
- Click anything containing "purchase", "buy", "pay", "delete", "uninstall", "reset", or similar dangerous keywords
- Act on apps you've excluded in Safety Settings
- Take any action it hasn't observed you perform at least 3 times
- Fire actions faster than every 800ms
- Act at all in Observe mode

Every action (taken or blocked) is logged with timestamps so you can review exactly what happened.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                 AutoAccessibilityService                     │
│                                                              │
│  ┌─────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │  Observer    │  │  AssistEngine    │  │ ScreenReactor  │  │
│  │  (always on) │  │  (ASSIST only)   │  │ (ASSIST only)  │  │
│  │             │  │                  │  │                │  │
│  │  User taps, │  │  SafetyChecker   │  │ Matches system │  │
│  │  scrolls,   │  │  PatternMatcher  │  │ patterns to    │  │
│  │  text input │  │  ActionExecutor  │  │ screen context │  │
│  └──────┬──────┘  └────────┬─────────┘  └───────┬────────┘  │
│         │                  │                    │            │
│  ┌──────┴──────────────────┴────────────────────┴────────┐  │
│  │  SystemStateMonitor                                    │  │
│  │  Watches volume, brightness, ringer, rotation changes  │  │
│  │  Tags each with current screen context                 │  │
│  └───────────────────────────┬────────────────────────────┘  │
│                              │                               │
│  ┌───────────────────────────┴────────────────────────────┐  │
│  │                    Room Database                        │  │
│  │  UserPatternEntity  SystemPatternEntity  LogEntity      │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

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
2. Tap **Open Accessibility Settings** and enable "AI Assistant"
3. Grant overlay permission if you want the floating status bubble
4. Leave it in **Observe Mode** — use your phone normally for a day or two
5. Switch to **Assist Mode** when you're ready for hands-free automation

---

## Permissions

| Permission | What it does |
|------------|-------------|
| `BIND_ACCESSIBILITY_SERVICE` | Observes and interacts with UI across apps |
| `SYSTEM_ALERT_WINDOW` | Shows the floating status overlay |
| `FOREGROUND_SERVICE` | Keeps the overlay service running |
| `MODIFY_AUDIO_SETTINGS` | Adjusts volume when replaying learned system patterns |

Optional: `WRITE_SETTINGS` (for brightness/rotation control) — the app prompts for this and works without it.

---

## Privacy

- All data is stored locally in a Room (SQLite) database on-device
- No network requests, no analytics, no telemetry
- No data leaves your phone, ever
- You can view and delete all learned patterns from within the app

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
