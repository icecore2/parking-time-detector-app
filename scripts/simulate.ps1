<#
.SYNOPSIS
    CLI testing & simulation harness for ParkingTimeDetector on local emulators/devices.

.DESCRIPTION
    Injects mock parking sessions, accessibility screen captures, stop receipts, and alarms
    directly into ParkingTimeDetector without needing real parking sessions or physical location changes.

.EXAMPLE
    .\scripts\simulate.ps1 start -Minutes 45
    .\scripts\simulate.ps1 start -Seconds 60 -Zone "Zone 999"
    .\scripts\simulate.ps1 screen -App MyParking -Zone "1205"
    .\scripts\simulate.ps1 stop -Duration "1 hr 15m" -Cost "$3.75"
    .\scripts\simulate.ps1 overlay
    .\scripts\simulate.ps1 alarm -Type advance
    .\scripts\simulate.ps1 clear
#>

[CmdletBinding()]
param (
    [Parameter(Position = 0)]
    [ValidateSet("start", "stop", "screen", "stop-screen", "overlay", "alarm", "clear", "logs", "status", "help")]
    [string]$Action = "help",

    [string]$App = "ParkedIn",
    [string]$Zone = "",
    [int]$Minutes = 0,
    [int]$Seconds = 0,
    [string]$Duration = "",
    [string]$Cost = "",
    [string]$Text = "",
    [string]$Nodes = "",
    [ValidateSet("advance", "expiry")]
    [string]$Type = "advance",
    [string]$DeviceId = ""
)

# 1. Locate ADB
$AdbPath = ""
if (Get-Command adb -ErrorAction SilentlyContinue) {
    $AdbPath = "adb"
} else {
    $LocalPropsPath = Join-Path $PSScriptRoot "..\local.properties"
    if (Test-Path $LocalPropsPath) {
        $SdkLine = Get-Content $LocalPropsPath | Where-Object { $_ -match "^sdk\.dir\s*=" }
        if ($SdkLine) {
            $RawSdk = ($SdkLine -split "=", 2)[1].Trim().Replace("\:", ":").Replace("\\", "\")
            $CandidateAdb = Join-Path $RawSdk "platform-tools\adb.exe"
            if (Test-Path $CandidateAdb) {
                $AdbPath = $CandidateAdb
            }
        }
    }
    if (-not $AdbPath) {
        $StandardAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
        if (Test-Path $StandardAdb) {
            $AdbPath = $StandardAdb
        }
    }
}

if (-not $AdbPath) {
    Write-Error "Could not find adb.exe. Please ensure the Android SDK is installed."
    exit 1
}

function Show-Help {
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "   ParkingTimeDetector - Local Simulation CLI Harness     " -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "Usage: .\scripts\simulate.ps1 <command> [options]`n" -ForegroundColor White
    
    Write-Host "Commands:" -ForegroundColor Yellow
    Write-Host "  start        Simulate a parking session start (notification)"
    Write-Host "  stop         Simulate a parking session stop (with duration & cost)"
    Write-Host "  screen       Simulate on-screen accessibility text capture"
    Write-Host "  stop-screen  Simulate on-screen parking summary/ended receipt"
    Write-Host "  overlay      Instantly trigger the floating/lockscreen overlay"
    Write-Host "  alarm        Trigger immediate advance or expiry alarm"
    Write-Host "  clear        Cancel active session and clear simulation state"
    Write-Host "  logs         Stream real-time app logs from logcat"
    Write-Host "  status       Check emulator connection and service status"
    Write-Host "  help         Display this help guide`n"

    Write-Host "Quick Examples:" -ForegroundColor Green
    Write-Host "  # 1-minute quick test (watch countdown & alarm trigger):"
    Write-Host "  .\scripts\simulate.ps1 start -Seconds 60`n"

    Write-Host "  # 45-minute ParkedIn session in Zone 4022:"
    Write-Host "  .\scripts\simulate.ps1 start -App ParkedIn -Zone 'Zone 4022' -Minutes 45`n"

    Write-Host "  # 30-minute MyParking session via screen capture:"
    Write-Host "  .\scripts\simulate.ps1 screen -App MyParking -Zone '1205' -Minutes 30`n"

    Write-Host "  # Session stop receipt with cost and duration:"
    Write-Host "  .\scripts\simulate.ps1 stop -Zone 'Lot 58' -Duration '45 mins' -Cost '`$2.50'`n"

    Write-Host "  # Test lock screen overlay dialog appearance immediately:"
    Write-Host "  .\scripts\simulate.ps1 overlay`n"

    Write-Host "  # Test the 15-minute advance warning alert immediately:"
    Write-Host "  .\scripts\simulate.ps1 alarm -Type advance`n"
}

if ($Action -eq "help" -or -not $Action) {
    Show-Help
    exit 0
}

function Run-AdbBroadcast ([hashtable]$extras) {
    $argsList = @()
    if ($DeviceId) {
        $argsList += "-s", $DeviceId
    }
    $argsList += "shell", "am", "broadcast", "-a", "com.parktimedetector.action.SIMULATE"

    foreach ($key in $extras.Keys) {
        $val = $extras[$key]
        if ($val -is [int]) {
            $argsList += "--ei", $key, $val
        } elseif ($val -is [bool]) {
            $argsList += "--ez", $key, ($val.ToString().ToLower())
        } else {
            $argsList += "--es", $key, "$val"
        }
    }

    Write-Host "[ADB] Executing: adb $( $argsList -join ' ' )" -ForegroundColor DarkGray
    & $AdbPath @argsList
}

switch ($Action) {
    "start" {
        $extras = @{
            "type" = "start"
            "app" = $App
        }
        if ($Zone) { $extras["zone"] = $Zone }
        if ($Minutes -gt 0) { $extras["minutes"] = $Minutes }
        if ($Seconds -gt 0) { $extras["seconds"] = $Seconds }
        if ($Text) { $extras["text"] = $Text }

        Write-Host "Simulating session start ($App, $(if ($Seconds) { "$Seconds sec" } elseif ($Minutes) { "$Minutes min" } else { "45 min" }))..." -ForegroundColor Cyan
        Run-AdbBroadcast $extras
        Write-Host "[OK] Start simulation dispatched to app." -ForegroundColor Green
    }

    "stop" {
        $extras = @{
            "type" = "stop"
            "app" = $App
        }
        if ($Zone) { $extras["zone"] = $Zone }
        if ($Duration) { $extras["duration"] = $Duration }
        if ($Cost) { $extras["cost"] = $Cost }
        if ($Text) { $extras["text"] = $Text }

        Write-Host "Simulating session stop ($App, cost: $(if ($Cost) { $Cost } else { '$2.50' }))..." -ForegroundColor Cyan
        Run-AdbBroadcast $extras
        Write-Host "[OK] Stop simulation dispatched to app." -ForegroundColor Green
    }

    "screen" {
        $extras = @{
            "type" = "screen"
            "app" = $App
        }
        if ($Zone) { $extras["zone"] = $Zone }
        if ($Minutes -gt 0) { $extras["minutes"] = $Minutes }
        if ($Nodes) { $extras["nodes"] = $Nodes }

        Write-Host "Simulating on-screen text capture ($App)..." -ForegroundColor Cyan
        Run-AdbBroadcast $extras
        Write-Host "[OK] Screen capture simulation dispatched." -ForegroundColor Green
    }

    "stop-screen" {
        $extras = @{
            "type" = "stop_screen"
            "app" = $App
        }
        if ($Zone) { $extras["zone"] = $Zone }
        if ($Cost) { $extras["cost"] = $Cost }
        if ($Nodes) { $extras["nodes"] = $Nodes }

        Write-Host "Simulating stop screen receipt ($App)..." -ForegroundColor Cyan
        Run-AdbBroadcast $extras
        Write-Host "[OK] Screen stop simulation dispatched." -ForegroundColor Green
    }

    "overlay" {
        $extras = @{
            "type" = "overlay"
            "app" = $App
        }
        Write-Host "Triggering instant overlay dialog..." -ForegroundColor Cyan
        Run-AdbBroadcast $extras
        Write-Host "[OK] Overlay test event dispatched. Check device screen!" -ForegroundColor Green
    }

    "alarm" {
        $alarmType = if ($Type -eq "expiry") { "expiry_alarm" } else { "advance_alarm" }
        $extras = @{
            "type" = $alarmType
        }
        Write-Host "Triggering immediate $Type alert..." -ForegroundColor Cyan
        Run-AdbBroadcast $extras
        Write-Host "[OK] $Type alert triggered." -ForegroundColor Green
    }

    "clear" {
        $extras = @{
            "type" = "clear"
        }
        Write-Host "Clearing all active sessions and simulator state..." -ForegroundColor Cyan
        Run-AdbBroadcast $extras
        Write-Host "[OK] Simulator reset complete." -ForegroundColor Green
    }

    "logs" {
        Write-Host "Streaming live logs for ParkingTimeDetector (Ctrl+C to stop)..." -ForegroundColor Cyan
        $logArgs = @()
        if ($DeviceId) { $logArgs += "-s", $DeviceId }
        $logArgs += "logcat", "-v", "time", "-s", "ParkingApp:V", "ParkingNotifListener:V", "ParkingAccessibility:V", "DetectionApprovalMgr:V", "SimulationReceiver:V", "AlarmScheduler:V", "NotificationHelper:V"
        & $AdbPath @logArgs
    }

    "status" {
        Write-Host "Checking target device status..." -ForegroundColor Cyan
        $devArgs = @()
        if ($DeviceId) { $devArgs += "-s", $DeviceId }
        & $AdbPath @devArgs devices
        Write-Host "`nApp connection status:" -ForegroundColor Cyan
        & $AdbPath @devArgs shell "dumpsys activity services com.parktimedetector" | Select-String "ParkingNotificationListenerService|ParkingAccessibilityService"
    }
}
