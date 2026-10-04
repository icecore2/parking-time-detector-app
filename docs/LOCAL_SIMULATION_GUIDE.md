# Local Simulator Setup & Testing Guide

This guide describes how to run and test **ParkingTimeDetector** locally using an Android Virtual Device (AVD Emulator) or physical test device without requiring real parking tickets or active parking sessions.

---

## 1. Quick Start: One-Click Environment Setup

To test background listeners, accessibility scraping, and lock screen overlays, Android requires several specialized permissions (`POST_NOTIFICATIONS`, `NotificationListenerService`, `AccessibilityService`, `SCHEDULE_EXACT_ALARM`, and `SYSTEM_ALERT_WINDOW`).

Instead of navigating through multiple settings submenus, run the automated provisioning script:

### Windows (PowerShell)
```powershell
.\scripts\setup_emulator.ps1
```

### macOS / Linux (Bash)
```bash
chmod +x ./scripts/*.sh
./scripts/setup_emulator.sh
```

### What the Setup Script Automates:
1. Detects attached ADB devices / emulators (and can launch your default AVD automatically if none is running).
2. Pre-grants notification permissions (`pm grant android.permission.POST_NOTIFICATIONS`).
3. Enables the **Notification Listener Service** (`cmd notification set_notification_listener_access`).
4. Enables the **Screen Inspection Accessibility Service** (`settings put secure enabled_accessibility_services`).
5. Grants **Exact Alarm** scheduling rights (`appops set SCHEDULE_EXACT_ALARM allow`).
6. Grants **Draw Over Other Apps / Floating Window** permissions (`appops set SYSTEM_ALERT_WINDOW allow`).
7. Whitelists the app from Android Doze battery optimizations for reliable alarm testing.

---

## 2. In-App Simulator Playground

A full-featured testing playground is built into the application:

1. **Top Bar Quick Launch**:
   - Tap the **"🧪 Simulator"** button on the top right of the navigation bar from any screen in the app.
   - A modal bottom sheet opens with all simulator controls.
2. **Settings Tab**:
   - Scroll to the **"Test & Detection Simulator"** card under Settings.

### Playground Features:
- **⚡ 1-Minute Expiry Test**:
  - Starts a real session expiring in 65 seconds.
  - Allows watching the live countdown, advance warning alarm, and expiration alarm trigger within 1 minute.
- **🅿️ One-Click Presets**:
  - ParkedIn Zone 4022 (30 min).
  - MyParking Zone 1205 (45 min).
  - Accessibility split-node screen capture.
  - Stop receipts with duration and dollar amount.
- **🎛️ Custom Session Builder**:
  - Select between ParkedIn or MyParking.
  - Enter custom Zone / Lot identifiers.
  - Adjust duration with interactive slider (1 to 180 mins) or quick preset chips (`1m`, `15m`, `30m`, `45m`, `60m`, `120m`).
- **🔍 Live Regex Diagnostic Tester**:
  - Type or paste arbitrary notification or OCR text.
  - Inspect extracted End Time, Zone, and Rule matching live as you type.
  - Dispatch the parsed payload directly into the active session pipeline.
- **🚨 Instant Alert & Overlay Triggers**:
  - **15m Advance Alert**: Immediately shows the advance renewal notification with action buttons.
  - **Expiry Alarm**: Immediately shows the parking expired alarm notification.
  - **Test Lock Screen Overlay**: Instantly displays the floating/lockscreen confirmation dialog to verify screen privacy policies.
- **🗑️ Reset Simulator**:
  - Cancels all active timers, alarms, and pending approval prompts in one tap.

---

## 3. Headless ADB Simulation CLI (`simulate.ps1` / `simulate.sh`)

You can inject parking events, screen scrapes, and alarm triggers directly from your workstation terminal or CI pipeline.

### PowerShell Usage:
```powershell
# Display help & examples
.\scripts\simulate.ps1 help

# 1. Quick 1-minute expiration test (watch countdown and alarm ring)
.\scripts\simulate.ps1 start -Seconds 60

# 2. Start a 45-minute ParkedIn session in Zone 4022
.\scripts\simulate.ps1 start -App ParkedIn -Zone "Zone 4022" -Minutes 45

# 3. Start a 30-minute MyParking session in Zone 1205
.\scripts\simulate.ps1 start -App MyParking -Zone "Zone 1205" -Minutes 30

# 4. Simulate Accessibility screen text nodes (MyParking)
.\scripts\simulate.ps1 screen -App MyParking -Zone "1205" -Minutes 30

# 5. Simulate session ended receipt with duration and cost
.\scripts\simulate.ps1 stop -App MyParking -Zone "Lot 58" -Duration "45 mins" -Cost "$2.50"

# 6. Test lock screen overlay dialog appearance immediately
.\scripts\simulate.ps1 overlay

# 7. Test the 15-minute advance renewal alert immediately
.\scripts\simulate.ps1 alarm -Type advance

# 8. Test parking expired alarm alert immediately
.\scripts\simulate.ps1 alarm -Type expiry

# 9. Clear all active sessions and reset simulator
.\scripts\simulate.ps1 clear

# 10. Stream live application logs filtered by relevant tags
.\scripts\simulate.ps1 logs
```

### Bash Usage (macOS / Linux):
```bash
./scripts/simulate.sh start --app ParkedIn --zone "Zone 4022" --minutes 45
./scripts/simulate.sh screen --app MyParking --zone "1205" --minutes 30
./scripts/simulate.sh stop --app MyParking --zone "Lot 58" --duration "45 mins" --cost "$2.50"
./scripts/simulate.sh overlay
./scripts/simulate.sh alarm
./scripts/simulate.sh clear
./scripts/simulate.sh logs
```

---

## 4. ADB Command Reference (Direct Invocation)

The simulator is powered by an exported `SimulationReceiver` registered for `com.parktimedetector.action.SIMULATE`:

```bash
# Start session
adb shell am broadcast -a com.parktimedetector.action.SIMULATE \
  --es type start \
  --es app parkedin \
  --es zone "Zone 4022" \
  --ei minutes 45

# Stop session
adb shell am broadcast -a com.parktimedetector.action.SIMULATE \
  --es type stop \
  --es app myparking \
  --es zone "Lot 58" \
  --es duration "45 mins" \
  --es cost "$2.50"

# Screen text inspection
adb shell am broadcast -a com.parktimedetector.action.SIMULATE \
  --es type screen \
  --es app myparking \
  --es nodes "Calgary Parking Authority;Active Session;Zone;1205;Valid Until;4:30 PM"

# Overlay test
adb shell am broadcast -a com.parktimedetector.action.SIMULATE \
  --es type overlay

# Reset
adb shell am broadcast -a com.parktimedetector.action.SIMULATE \
  --es type clear
```

---

## 5. Local Automated Testing

Run the full local unit test suite (including all parser and simulator tests):

```bash
# Windows
.\gradlew.bat test

# macOS / Linux
./gradlew test
```

All 39 unit tests in `SimulatorTest`, `SessionNotificationParserTest`, and `OverlayPrivacyTest` verify:
- Accurate extraction of hours, minutes, countdown clocks, and relative durations.
- Split node inspection across accessibility views.
- Stop screen receipts with duration and monetary cost parsing.
- Lock screen keyguard redaction policies.
