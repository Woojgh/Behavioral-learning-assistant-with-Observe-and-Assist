# Project Explanation
## What this project is
Behavioral Learning Assistant is an on-device automation assistant focused on reducing repetitive phone interactions.

The main Android app learns from repeated user behavior in **Observe** mode and can replay safe, learned actions in **Assist** mode.

## Core idea
Instead of rule scripting, this project learns patterns from what the user actually does on specific screens:
- observe taps, scrolls, text input, and certain device-setting changes
- fingerprint screen structure to recognize repeat contexts
- require repeated observations before any automatic replay
- apply safety checks before executing actions

## Main components
- `app/`: Android implementation (Kotlin, Accessibility Service, Room, coroutine-based engine)
- `BehavioralAssistant-iOS/`: iOS port of models/business logic with platform-safe adaptations
- `README.md`: full product overview, setup, architecture, safety, and privacy details

## Safety and privacy model
- actions are blocked for dangerous intents (purchase/delete/pay/reset-style patterns)
- assist actions only trigger after confidence thresholds are met
- app exclusions and cooldowns reduce accidental automation
- learning/action logs are stored locally for review
- no cloud dependency is required for core behavior

## How to run
### Android
1. Open the repo in Android Studio.
2. Sync Gradle and run on API 26+.
3. Enable the accessibility service from the app’s onboarding flow.
4. Start in Observe mode, then switch to Assist mode when enough patterns are learned.

### iOS
1. Open `BehavioralAssistant-iOS/BehavioralAssistant.xcodeproj` in Xcode.
2. Build and run on iOS 16+.
3. Use it as an in-app behavioral engine (iOS does not allow Android-style cross-app accessibility automation).

## Standalone branch snapshot note
This repository was created as a standalone snapshot of the `feature/remote-skip-watch-earbuds` branch, so it can be developed and shared independently from the original parent repository.
