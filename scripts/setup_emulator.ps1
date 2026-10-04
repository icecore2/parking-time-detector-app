<#
.SYNOPSIS
    Automates local Android Emulator / device environment configuration for ParkingTimeDetector.

.DESCRIPTION
    Detects running emulator/device, launches an AVD if needed, and grants all required permissions
    (Notification Listener, Accessibility Service, Exact Alarms, Overlay Windows, and Post Notifications)
    without manual navigation through Android Settings.

.EXAMPLE
    .\scripts\setup_emulator.ps1
#>

[CmdletBinding()]
param (
    [string]$DeviceId = "",
    [switch]$LaunchAvdIfNone = $true
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  ParkingTimeDetector - Local Emulator / Device Setup     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Locate Android SDK and ADB
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
    Write-Error "Could not locate adb.exe. Please ensure Android SDK is installed or ADB is in your PATH."
    exit 1
}

Write-Host "[OK] ADB located: $AdbPath" -ForegroundColor Green

# 2. Check for running devices/emulators
function Get-AttachedDevices {
    $devicesOutput = & $AdbPath devices
    $attached = @()
    foreach ($line in ($devicesOutput -split "`r?`n")) {
        if ($line -match "^([^\s]+)\s+device$") {
            $attached += $matches[1]
        }
    }
    return $attached
}

$devices = Get-AttachedDevices

if ($devices.Count -eq 0) {
    Write-Host "[!] No active Android devices or emulators found." -ForegroundColor Yellow
    
    # Try finding emulator executable
    $SdkDir = Split-Path (Split-Path $AdbPath -Parent) -Parent
    $EmulatorExe = Join-Path $SdkDir "emulator\emulator.exe"
    
    if (Test-Path $EmulatorExe) {
        $avds = & $EmulatorExe -list-avds
        if ($avds) {
            $selectedAvd = ($avds -split "`r?`n")[0].Trim()
            if ($selectedAvd -and $LaunchAvdIfNone) {
                Write-Host "Starting emulator AVD '$selectedAvd'..." -ForegroundColor Cyan
                Start-Process -FilePath $EmulatorExe -ArgumentList "-avd $selectedAvd"
                Write-Host "Waiting for emulator to boot up (this may take 30-60 seconds)..." -ForegroundColor Yellow
                & $AdbPath wait-for-device
                Start-Sleep -Seconds 10
                $devices = Get-AttachedDevices
            }
        } else {
            Write-Host "No AVDs found. Please create an Android Virtual Device (AVD) in Android Studio Device Manager." -ForegroundColor Yellow
            Write-Host "Recommended: Pixel 8 or Pixel 7 running API 34 or 35." -ForegroundColor Yellow
            exit 0
        }
    } else {
        Write-Host "Please start an Android emulator or connect a device with USB debugging enabled, then rerun this script." -ForegroundColor Yellow
        exit 0
    }
}

$targetDevice = if ($DeviceId) { $DeviceId } else { $devices[0] }
Write-Host "[OK] Target Device: $targetDevice" -ForegroundColor Green

function Exec-AdbShell ([string]$command) {
    if ($DeviceId) {
        & $AdbPath -s $targetDevice shell $command
    } else {
        & $AdbPath shell $command
    }
}

$PackageName = "com.parktimedetector"
$ListenerService = "$PackageName/$PackageName.service.ParkingNotificationListenerService"
$AccessibilityService = "$PackageName/$PackageName.service.ParkingAccessibilityService"

Write-Host "`nConfiguring permissions for $PackageName on $targetDevice..." -ForegroundColor Cyan

# 3. Post Notifications (Android 13+)
Write-Host "-> Granting POST_NOTIFICATIONS permission..." -NoNewline
try {
    Exec-AdbShell "pm grant $PackageName android.permission.POST_NOTIFICATIONS" 2>$null
    Write-Host " [DONE]" -ForegroundColor Green
} catch {
    Write-Host " [SKIPPED / PRE-TIRAMISU]" -ForegroundColor DarkGray
}

# 4. Notification Listener Service
Write-Host "-> Enabling Notification Listener Service access..." -NoNewline
try {
    Exec-AdbShell "cmd notification set_notification_listener_access $ListenerService 1" 2>$null
    Write-Host " [DONE]" -ForegroundColor Green
} catch {
    Write-Host " [WARN: check device API level]" -ForegroundColor Yellow
}

# 5. Accessibility Service
Write-Host "-> Enabling Accessibility Service..." -NoNewline
try {
    $existing = Exec-AdbShell "settings get secure enabled_accessibility_services"
    $trimmed = if ($existing) { $existing.Trim() } else { "" }
    if ($trimmed -and $trimmed -notmatch [regex]::Escape($AccessibilityService)) {
        $newServices = "$trimmed:$AccessibilityService"
    } else {
        $newServices = $AccessibilityService
    }
    Exec-AdbShell "settings put secure enabled_accessibility_services $newServices" 2>$null
    Exec-AdbShell "settings put secure accessibility_enabled 1" 2>$null
    Write-Host " [DONE]" -ForegroundColor Green
} catch {
    Write-Host " [WARN: Failed to set accessibility]" -ForegroundColor Yellow
}

# 6. Schedule Exact Alarms (Android 12+)
Write-Host "-> Enabling SCHEDULE_EXACT_ALARM..." -NoNewline
try {
    Exec-AdbShell "appops set $PackageName SCHEDULE_EXACT_ALARM allow" 2>$null
    Write-Host " [DONE]" -ForegroundColor Green
} catch {
    Write-Host " [SKIPPED]" -ForegroundColor DarkGray
}

# 7. System Alert Window (Draw Over Other Apps / Lockscreen Overlay)
Write-Host "-> Enabling SYSTEM_ALERT_WINDOW (Overlay)..." -NoNewline
try {
    Exec-AdbShell "appops set $PackageName SYSTEM_ALERT_WINDOW allow" 2>$null
    Write-Host " [DONE]" -ForegroundColor Green
} catch {
    Write-Host " [SKIPPED]" -ForegroundColor DarkGray
}

# 8. Ignore Battery Optimizations (Avoid Doze alarm drops in testing)
Write-Host "-> Whitelisting from Doze / Battery Optimization..." -NoNewline
try {
    Exec-AdbShell "dumpsys deviceidle whitelist +$PackageName" 2>$null
    Write-Host " [DONE]" -ForegroundColor Green
} catch {
    Write-Host " [SKIPPED]" -ForegroundColor DarkGray
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "  Setup Complete! Local testing environment is ready.      " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "All permissions and system listeners are enabled for ParkingTimeDetector."
Write-Host "You can now run .\scripts\simulate.ps1 to inject parking sessions and events." -ForegroundColor Cyan
