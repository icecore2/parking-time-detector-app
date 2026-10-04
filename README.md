# Parking Time Detector

Android app that monitors parking session notifications and on-screen parking information, sets automated countdown timers, and delivers advance warnings before your parking expires.

## Features

- **Automated Detection**: Detects parking sessions from supported apps (such as ParkedIn and MyParking) via Notification Listener and Screen Inspection Accessibility services.
- **Dynamic Countdown & Warnings**: Automatically calculates expiration time, provides advance notification warnings (e.g. 5–10 minutes before expiry), and triggers expiration alarms.
- **Testing Playground & Simulator**: Built-in simulator with quick presets and custom session builder to test notification parsing, alarms, and countdowns without needing an active parking session.
- **Automated CI/CD**: GitHub Actions workflow that automatically tests, builds, and publishes installable release APKs on pushes to `main` and on tag pushes (`v*`).

## CI/CD & Automated Releases

This repository includes a GitHub Actions workflow (`.github/workflows/release.yml`) that:
- Runs unit tests (`./gradlew testDebugUnitTest`).
- Builds both release and debug APKs (`assembleRelease` and `assembleDebug`).
- Uploads the generated APKs as workflow artifacts.
- Publishes a new GitHub Release with attached APKs:
  - Automatically triggered on push to `main` (auto-versioned: `v<version>.<run_number>`).
  - Triggered on tag push (e.g., `git tag v1.0.0 && git push origin v1.0.0`).
  - Triggered manually via `workflow_dispatch` with custom tag options.

### Signing Configuration (Optional)
By default, the workflow signs release builds using the standard Android debug keystore so generated APKs can be installed directly. To sign with your production keystore, configure the following GitHub Repository Secrets:
- `KEYSTORE_BASE64`: Base64-encoded `.jks` / `.keystore` file.
- `KEYSTORE_PASSWORD`: Keystore password.
- `KEY_ALIAS`: Key alias.
- `KEY_PASSWORD`: Key password.

## Local Development & Testing

See [docs/LOCAL_SIMULATION_GUIDE.md](docs/LOCAL_SIMULATION_GUIDE.md) for details on setting up emulator permissions and running the interactive simulator.
