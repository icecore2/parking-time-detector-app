#!/usr/bin/env bash
# ==========================================================
#  ParkingTimeDetector - Local Simulation CLI Harness (Bash)
# ==========================================================
set -e

ADB_BIN="adb"
if ! command -v adb &> /dev/null; then
    if [ -n "$ANDROID_HOME" ]; then
        ADB_BIN="$ANDROID_HOME/platform-tools/adb"
    elif [ -n "$ANDROID_SDK_ROOT" ]; then
        ADB_BIN="$ANDROID_SDK_ROOT/platform-tools/adb"
    fi
fi

ACTION="${1:-help}"
shift || true

function show_help {
    echo "Usage: ./scripts/simulate.sh <command> [options]"
    echo ""
    echo "Commands:"
    echo "  start [--app ParkedIn] [--zone 4022] [--minutes 45] [--seconds 60]"
    echo "  stop  [--app ParkedIn] [--zone 4022] [--duration '45m'] [--cost '\$2.50']"
    echo "  screen [--app MyParking] [--zone 1205] [--minutes 30]"
    echo "  overlay"
    echo "  alarm [--type advance|expiry]"
    echo "  clear"
    echo "  logs"
}

case "$ACTION" in
    start)
        $ADB_BIN shell am broadcast -a com.parktimedetector.action.SIMULATE --es type start "$@"
        ;;
    stop)
        $ADB_BIN shell am broadcast -a com.parktimedetector.action.SIMULATE --es type stop "$@"
        ;;
    screen)
        $ADB_BIN shell am broadcast -a com.parktimedetector.action.SIMULATE --es type screen "$@"
        ;;
    overlay)
        $ADB_BIN shell am broadcast -a com.parktimedetector.action.SIMULATE --es type overlay
        ;;
    alarm)
        $ADB_BIN shell am broadcast -a com.parktimedetector.action.SIMULATE --es type advance_alarm
        ;;
    clear)
        $ADB_BIN shell am broadcast -a com.parktimedetector.action.SIMULATE --es type clear
        ;;
    logs)
        $ADB_BIN logcat -v time -s ParkingApp:V ParkingNotifListener:V ParkingAccessibility:V DetectionApprovalMgr:V SimulationReceiver:V AlarmScheduler:V
        ;;
    *)
        show_help
        ;;
esac
