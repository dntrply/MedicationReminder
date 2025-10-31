# Audio Transcription Troubleshooting Guide

This guide helps diagnose and resolve issues with the audio transcription feature in the Medication Reminder app.

## Table of Contents
1. [Understanding the Transcription Workflow](#understanding-the-transcription-workflow)
2. [Emulator Troubleshooting](#emulator-troubleshooting)
3. [Real Device Troubleshooting](#real-device-troubleshooting)
4. [Common Issues and Solutions](#common-issues-and-solutions)
5. [Debug Data Screen Reference](#debug-data-screen-reference)

---

## Understanding the Transcription Workflow

The audio transcription feature works in several stages:

### Stage 1: User Consent
- **First time only**: When you record audio for the first time, the app shows a consent dialog
- User must accept to enable transcription
- Declining means audio notes won't be transcribed (but still saved)
- Consent decision is saved permanently

### Stage 2: Audio Recording & Pending Stats
- User records audio note for a medication
- Audio is saved to device storage
- A **"pending" stats entry** is created immediately in the database
- WorkManager task is scheduled for background transcription

### Stage 3: Background Processing (Requires Charging)
- WorkManager waits for specific conditions:
  - Device must be **charging** (plugged in)
  - Battery must not be low
- When conditions are met, the transcription worker starts

### Stage 4: Model Download (First Time Only)
- Whisper Tiny model (~75MB) is downloaded from GitHub
- This is a **one-time** download
- Requires WiFi connection
- Takes 2-5 minutes depending on connection speed

### Stage 5: Transcription Processing
- Audio is processed on-device using Whisper Tiny
- CPU-intensive: can take 30-60 seconds per audio clip
- Stats entry is updated from "pending" to "success" or "failed"
- Transcribed text is saved to the medication record

---

## Emulator Troubleshooting

### Step 1: Check if Audio File Exists

```bash
# List all audio files
adb shell "run-as com.medreminder.app ls -lh files/audio/"
```

**Expected output:**
```
-rw------- 1 u0_a202 u0_a202 69K 2025-10-30 23:39 audio_note_1761847770136.m4a
```

**If no files**: User hasn't recorded any audio notes yet.

---

### Step 2: Check Database for Transcription Stats

```bash
# Get total count
adb shell "run-as com.medreminder.app sqlite3 databases/medication_database 'SELECT COUNT(*) FROM transcription_stats;'"

# Get all stats entries
adb shell "run-as com.medreminder.app sqlite3 databases/medication_database 'SELECT id, medicationId, status, startTime FROM transcription_stats;'"

# Count by status
adb shell "run-as com.medreminder.app sqlite3 databases/medication_database 'SELECT status, COUNT(*) FROM transcription_stats GROUP BY status;'"
```

**Expected output** (if transcription is pending):
```
pending|1
```

**If empty**: Either no audio recorded, or stats creation failed.

---

### Step 3: Check User Consent

```bash
# Check DataStore preferences
adb shell "run-as com.medreminder.app cat files/datastore/user_prefs.preferences_pb" | strings | grep -i transcription
```

Look for:
- `transcription_consent` - should be true if user accepted
- `transcription_consent_asked` - should be true if dialog was shown

---

### Step 4: Check WorkManager Status

```bash
# Check scheduled jobs
adb shell "dumpsys jobscheduler | grep -A 30 'com.medreminder.app'"
```

**Look for:**
- `Required constraints: CHARGING BATTERY_NOT_LOW`
- `Satisfied constraints:` vs `Unsatisfied constraints:`
- If `CHARGING` is in unsatisfied, the device needs to be plugged in

**To simulate charging on emulator:**
```bash
# Enable AC charging
adb shell dumpsys battery set ac 1

# Verify charging status
adb shell dumpsys battery | grep "AC powered"
```

**Expected output:**
```
AC powered: true
```

**To reset battery (stop simulating):**
```bash
adb shell dumpsys battery reset
```

---

### Step 5: Monitor Transcription Process

```bash
# Clear logs and monitor transcription
adb logcat -c
adb logcat -s "AudioTranscriptionWorker:*" "AudioTranscriptionService:*" "WhisperTinyEngine:*" "TranscriptionScheduler:*"
```

**Expected log sequence:**

1. **Worker starts:**
   ```
   D AudioTranscriptionWorker: Starting transcription for medication X
   ```

2. **Stats entry created/found:**
   ```
   D AudioTranscriptionWorker: Created new stats entry Y for medication X
   ```
   OR
   ```
   D AudioTranscriptionWorker: Found existing stats entry Y for medication X
   ```

3. **Service initialization:**
   ```
   D AudioTranscriptionService: Initializing transcription engine...
   ```

4. **Model download (first time):**
   ```
   D WhisperTinyEngine: Downloading Whisper Tiny model from GitHub...
   D WhisperTinyEngine: Download progress: 10%
   D WhisperTinyEngine: Download progress: 50%
   D WhisperTinyEngine: Model downloaded successfully
   ```

5. **Model loading:**
   ```
   D WhisperTinyEngine: Loading model from: /path/to/model
   D WhisperTinyEngine: Model loaded successfully
   ```

6. **Transcription:**
   ```
   D WhisperTinyEngine: Starting transcription...
   D WhisperTinyEngine: Transcription completed in XXXXms
   ```

7. **Success:**
   ```
   D AudioTranscriptionWorker: Transcription completed: [transcribed text]
   D AudioTranscriptionWorker: Updated medication with transcription
   ```

---

### Step 6: Check Transcription Results

After transcription completes:

```bash
# Check stats in database
adb shell "run-as com.medreminder.app sqlite3 databases/medication_database 'SELECT medicationName, status, transcriptionText FROM transcription_stats;'"

# Check medication record
adb shell "run-as com.medreminder.app sqlite3 databases/medication_database 'SELECT name, audioTranscription, audioTranscriptionLanguage FROM medications WHERE audioNotePath IS NOT NULL;'"
```

---

### Step 7: Check Downloaded Model

```bash
# Check if Whisper model exists
adb shell "run-as com.medreminder.app ls -lh files/"

# Look for files like:
# ggml-tiny.bin or similar Whisper model files
```

Model should be ~75MB.

---

## Real Device Troubleshooting

### Prerequisites

On a real device, you need **USB debugging** enabled. If not available, use these alternative methods:

### Method 1: Debug Data Screen (In-App)

The app has a built-in debug screen accessible from Settings:

1. Open the app
2. Go to **Settings** → **Debug Data**
3. Scroll to **Transcription Statistics** section

**What to check:**
- **Total**: Total number of transcription attempts
- **Success**: Successfully transcribed audio notes
- **Failed**: Failed transcription attempts
- **Pending**: Audio notes waiting to be transcribed

**Expected values:**
- If you just recorded audio: **Pending = 1+**
- If device has been charging: Check if **Success** increased

---

### Method 2: Using ADB over WiFi (If USB Debugging Available)

If the device initially has USB debugging:

```bash
# 1. Connect device via USB first
adb devices

# 2. Enable TCP/IP mode (on port 5555)
adb tcpip 5555

# 3. Find device IP address
adb shell ip addr show wlan0 | grep "inet "

# 4. Disconnect USB cable

# 5. Connect over WiFi (replace with your device IP)
adb connect 192.168.1.XXX:5555

# 6. Now use all the emulator troubleshooting commands
```

---

### Method 3: Checking Logs Without ADB (Release Build)

For release builds on real devices without USB debugging:

#### A. Check Notification History
1. Long-press on home screen
2. **Widgets** → **Settings Shortcut**
3. Add **Notification Log** widget
4. Look for notifications from "Medication Reminder"
5. Check for any error messages

#### B. Use Debug Data Screen
The in-app Debug Data screen shows:
- Transcription statistics (success/failure counts)
- Last transcription attempt details
- Error messages (if any)

#### C. Check Battery Optimization
Android may prevent background work:

1. **Settings** → **Apps** → **Medication Reminder**
2. **Battery** → **Battery optimization**
3. Set to **Don't optimize** or **Unrestricted**

#### D. Check Storage Space
Transcription requires ~75MB for the model:

1. **Settings** → **Storage**
2. Check available space (need at least 100MB free)

---

## Common Issues and Solutions

### Issue 1: "Pending" Count Stays at 0

**Symptoms:**
- Recorded audio but Debug Data shows "0 Pending"

**Possible Causes:**
1. App version doesn't create pending stats (older version)
2. Transcription consent was declined
3. Stats creation failed

**Solution:**
```bash
# Check logs for errors
adb logcat -d | grep -i "error.*transcription"

# Check if consent was given
adb shell "run-as com.medreminder.app cat files/datastore/user_prefs.preferences_pb" | strings | grep transcription

# Manually check database
adb shell "run-as com.medreminder.app sqlite3 databases/medication_database 'SELECT * FROM transcription_stats;'"
```

**If stats are missing**, re-record audio on the updated app version.

---

### Issue 2: Stats Stuck on "Pending"

**Symptoms:**
- Audio recorded
- Stats show "X Pending" but never completes

**Possible Causes:**
1. Device not charging
2. Battery optimization blocking background work
3. WorkManager not running

**Solution:**

**On Emulator:**
```bash
# Simulate charging
adb shell dumpsys battery set ac 1

# Check if worker starts
adb logcat -s "AudioTranscriptionWorker:*"
```

**On Real Device:**
1. Plug in the device (actually charge it)
2. Keep the device plugged in for 10-15 minutes
3. Ensure WiFi is connected
4. Check Battery Optimization settings (disable for this app)

---

### Issue 3: Transcription Fails (Status = "failed")

**Symptoms:**
- Stats show "X Failed"

**Possible Causes:**
1. Model download failed
2. Audio file corrupted or missing
3. Insufficient storage space
4. Whisper model loading error

**Solution:**
```bash
# Check error messages
adb logcat -d | grep -E "AudioTranscription.*error|AudioTranscription.*failed|WhisperTiny.*error"

# Check audio file
adb shell "run-as com.medreminder.app ls -lh files/audio/"

# Check available storage
adb shell "df -h /data/data/com.medreminder.app"

# Check model file
adb shell "run-as com.medreminder.app ls -lh files/" | grep -i whisper
```

**Common error messages:**
- `Model download failed: No space left on device` → Free up storage
- `Audio file not found` → Audio was deleted, re-record
- `Model loading failed` → Delete model file and let it re-download

---

### Issue 4: Model Download Stuck

**Symptoms:**
- Log shows download progress but never completes
- Progress stuck at same percentage

**Solution:**
```bash
# Check network connectivity
adb shell ping -c 3 github.com

# Cancel and restart
# Force stop the app
adb shell am force-stop com.medreminder.app

# Delete incomplete download
adb shell "run-as com.medreminder.app rm files/ggml-tiny.bin*"

# Restart the app and trigger transcription again (with charging)
```

---

### Issue 5: No Consent Dialog Shown

**Symptoms:**
- First time recording audio
- No consent dialog appears

**Solution:**
```bash
# Check if consent was already asked
adb shell "run-as com.medreminder.app cat files/datastore/user_prefs.preferences_pb" | strings | grep consent

# If it says "asked=true", the dialog was already shown
# To reset (for testing):
adb shell "run-as com.medreminder.app rm files/datastore/user_prefs.preferences_pb"

# Restart app
adb shell am force-stop com.medreminder.app
```

---

## Debug Data Screen Reference

The in-app **Debug Data** screen (Settings → Debug Data) shows:

### Transcription Statistics Section

| Field | Meaning |
|-------|---------|
| **Total** | Total transcription attempts (pending + success + failed) |
| **Success** | Successfully transcribed audio notes |
| **Failed** | Failed transcription attempts |
| **Pending** | Audio notes waiting to be transcribed (requires charging) |

### Expected Values

| Scenario | Total | Success | Failed | Pending |
|----------|-------|---------|--------|---------|
| Just recorded audio (not charging) | 1 | 0 | 0 | 1 |
| After charging & successful transcription | 1 | 1 | 0 | 0 |
| After charging but transcription failed | 1 | 0 | 1 | 0 |
| Multiple recordings, some pending | 5 | 2 | 0 | 3 |
| All transcribed successfully | 10 | 10 | 0 | 0 |

---

## Advanced Debugging

### Force Transcription to Run Immediately (Testing Only)

For testing, you can temporarily remove the charging constraint:

**⚠️ WARNING**: This will drain battery quickly on real devices!

1. Edit `TranscriptionScheduler.kt`
2. Comment out the charging constraint:
   ```kotlin
   val constraints = Constraints.Builder()
       // .setRequiresCharging(true)  // Comment this out
       .setRequiresBatteryNotLow(true)
       .build()
   ```
3. Rebuild and install
4. Transcription will now run without charging

**Remember to restore the constraint for production!**

---

### Clear All Transcription Data (Reset)

To completely reset transcription state:

```bash
# Clear stats database
adb shell "run-as com.medreminder.app sqlite3 databases/medication_database 'DELETE FROM transcription_stats;'"

# Clear consent preference
adb shell "run-as com.medreminder.app rm files/datastore/user_prefs.preferences_pb"

# Delete downloaded model
adb shell "run-as com.medreminder.app rm files/ggml-tiny.bin"

# Cancel pending work
adb shell "run-as com.medreminder.app pm clear com.medreminder.app"
```

---

## Getting Help

If issues persist after troubleshooting:

1. Collect logs:
   ```bash
   adb logcat -d > transcription_logs.txt
   ```

2. Check database state:
   ```bash
   adb shell "run-as com.medreminder.app sqlite3 databases/medication_database '.dump transcription_stats'" > stats_dump.txt
   ```

3. Report issue with:
   - Device type (emulator/real device)
   - Android version
   - App version
   - Steps to reproduce
   - Logs and database dump

---

## Quick Reference Commands

### Emulator Setup
```bash
# Simulate charging
adb shell dumpsys battery set ac 1

# Reset battery
adb shell dumpsys battery reset
```

### Check Status
```bash
# Audio files
adb shell "run-as com.medreminder.app ls -lh files/audio/"

# Stats count
adb shell "run-as com.medreminder.app sqlite3 databases/medication_database 'SELECT status, COUNT(*) FROM transcription_stats GROUP BY status;'"

# WorkManager jobs
adb shell "dumpsys jobscheduler | grep medreminder"
```

### Monitor Logs
```bash
# All transcription activity
adb logcat -s "AudioTranscriptionWorker:*" "AudioTranscriptionService:*" "WhisperTinyEngine:*" "TranscriptionScheduler:*"

# Errors only
adb logcat -s "AudioTranscriptionWorker:E" "AudioTranscriptionService:E" "WhisperTinyEngine:E"
```

### Force Actions
```bash
# Force stop app
adb shell am force-stop com.medreminder.app

# Clear app data (⚠️ deletes everything)
adb shell pm clear com.medreminder.app
```
