# Build and Deployment Commands

This document contains the essential commands for building and deploying the Medication Reminder app.

## Prerequisites

- Android SDK installed
- Gradle configured
- ADB (Android Debug Bridge) available in PATH
- Emulator running or physical device connected

## Build Commands

### Debug Build

Build the debug APK:
```bash
./gradlew assembleDebug
```

The debug APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

Build the release APK:
```bash
./gradlew assembleRelease
```

The release APK will be located at:
```
app/build/outputs/apk/release/app-release.apk
```

Note: Release builds require signing configuration in `app/build.gradle.kts`

### Clean Build

Clean all build artifacts before building:
```bash
./gradlew clean assembleDebug
```

or for release:
```bash
./gradlew clean assembleRelease
```

## Deployment Commands

### Install Debug Build on Emulator/Device

Install (or reinstall) the debug APK:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `-r` flag reinstalls the app while keeping existing data.

### Build and Install in One Command

Build debug and immediately install:
```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Uninstall App

Remove the app completely from the device:
```bash
adb uninstall com.medreminder.app
```

### Launch App After Installation

Start the app after installation:
```bash
adb shell am start -n com.medreminder.app/.MainActivity
```

### Complete Workflow (Uninstall, Build, Install, Launch)

For a fresh installation:
```bash
adb uninstall com.medreminder.app && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.medreminder.app/.MainActivity
```

## Device Management Commands

### List Connected Devices

```bash
adb devices
```

### Check if Emulator is Running

```bash
adb shell getprop ro.product.model
```

### View App Logs

Real-time logs:
```bash
adb logcat | grep "medreminder"
```

Recent logs:
```bash
adb logcat -d | grep "medreminder"
```

Crash logs:
```bash
adb logcat -d | grep -E "AndroidRuntime|FATAL|Exception"
```

## Build Variants

The app supports different build variants. To see all available tasks:
```bash
./gradlew tasks
```

Common build tasks:
- `assembleDebug` - Build debug APK
- `assembleRelease` - Build release APK
- `installDebug` - Build and install debug APK (alternative to manual install)
- `installRelease` - Build and install release APK
- `bundleDebug` - Build debug Android App Bundle (AAB)
- `bundleRelease` - Build release Android App Bundle (AAB)

## Troubleshooting

### Gradle Daemon Issues

If builds are slow or failing, try stopping and restarting the Gradle daemon:
```bash
./gradlew --stop
```

### Clear Build Cache

If experiencing strange build errors:
```bash
./gradlew clean
./gradlew cleanBuildCache
```

### ADB Connection Issues

Restart ADB server:
```bash
adb kill-server
adb start-server
```

### Check App is Installed

```bash
adb shell pm list packages | grep medreminder
```

Expected output: `package:com.medreminder.app`

## Quick Reference

| Task | Command |
|------|---------|
| Build Debug | `./gradlew assembleDebug` |
| Build Release | `./gradlew assembleRelease` |
| Install Debug | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Uninstall | `adb uninstall com.medreminder.app` |
| Launch App | `adb shell am start -n com.medreminder.app/.MainActivity` |
| View Logs | `adb logcat -d \| grep "medreminder"` |
| Build + Install | `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk` |

## Development Workflow

Typical development cycle:

1. Make code changes
2. Build debug APK: `./gradlew assembleDebug`
3. Install to emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. Launch app: `adb shell am start -n com.medreminder.app/.MainActivity`
5. Monitor logs: `adb logcat -d | grep "medreminder"`

For a clean installation (fresh start):
1. Uninstall: `adb uninstall com.medreminder.app`
2. Clean build: `./gradlew clean assembleDebug`
3. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. Launch: `adb shell am start -n com.medreminder.app/.MainActivity`
