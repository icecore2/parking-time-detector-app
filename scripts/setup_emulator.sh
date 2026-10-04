#!/usr/bin/env bash
# ==========================================================
#  ParkingTimeDetector - Local Emulator / Device Setup (Bash)
# ==========================================================
set -e

PACKAGE_NAME="com.parktimedetector"
LISTENER_SERVICE="${PACKAGE_NAME}/${PACKAGE_NAME}.service.ParkingNotificationListenerService"
ACCESSIBILITY_SERVICE="${PACKAGE_NAME}/${PACKAGE_NAME}.service.ParkingAccessibilityService"

ADB_BIN="adb"
if ! command -v adb &> /dev/null; then
    if [ -n "$ANDROID_HOME" ]; then
        ADB_BIN="$ANDROID_HOME/platform-tools/adb"
    elif [ -n "$ANDROID_SDK_ROOT" ]; then
        ADB_BIN="$ANDROID_SDK_ROOT/platform-tools/adb"
    else
        echo "Error: adb not found in PATH and ANDROID_HOME is not set."
        exit 1
    fi
fi

DEVICE_ID="${1:-}"
ADB_CMD="$ADB_BIN"
if [ -n "$DEVICE_ID" ]; then
    ADB_CMD="$ADB_BIN -s $DEVICE_ID"
fi

echo "Using ADB: $ADB_CMD"
echo "Granting permissions for $PACKAGE_NAME..."

# Post Notifications (Android 13+)
$ADB_CMD shell pm grant "$PACKAGE_NAME" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# Notification Listener Service
$ADB_CMD shell cmd notification set_notification_listener_access "$LISTENER_SERVICE" 1 2>/dev/null || true

# Accessibility Service
EXISTING=$($ADB_CMD shell settings get secure enabled_accessibility_services 2>/dev/null || echo "")
if [[ "$EXISTING" != *"$ACCESSIBILITY_SERVICE"* ]]; then
    NEW_SERVICES="${EXISTING:+${EXISTING}:}${ACCESSIBILITY_SERVICE}"
    $ADB_CMD shell settings put secure enabled_accessibility_services "$NEW_SERVICES" 2>/dev/null || true
fi
$ADB_CMD shell settings put secure accessibility_enabled 1 2>/dev/null || true

# Exact Alarms & System Alert Window
$ADB_CMD shell appops set "$PACKAGE_NAME" SCHEDULE_EXACT_ALARM allow 2>/dev/null || true
$ADB_CMD shell appops set "$PACKAGE_NAME" SYSTEM_ALERT_WINDOW allow 2>/dev/null || true

# Battery Optimization Whitelist
$ADB_CMD shell dumpsys deviceidle whitelist +"$PACKAGE_NAME" 2>/dev/null || true

echo "Setup complete! Local emulator environment is ready for ParkingTimeDetector."
