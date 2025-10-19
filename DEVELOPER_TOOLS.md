continue# Developer Tools for Database Inspection

This document describes how to inspect the app's databases and data stores during development.

## Quick Reference

### Data Storage Locations

The app stores data in three places:

1. **Room Database** (`medication_database`)
   - Location: `/data/data/com.medreminder.app/databases/medication_database`
   - Tables: `medications`, `medication_history`
   
2. **Pending Medications** (SharedPreferences)
   - Location: `/data/data/com.medreminder.app/shared_prefs/pending_medications.xml`
   - Stores: Active notification state
   
3. **App Preferences** (SharedPreferences)
   - Location: `/data/data/com.medreminder.app/shared_prefs/app_prefs.xml`
   - Stores: User settings (language, etc.)

---

## Method 1: Android Studio Database Inspector (Recommended)

**Best for:** Active development, real-time inspection

**Steps:**
1. Run the app in debug mode from Android Studio
2. View → Tool Windows → App Inspection
3. Select "Database Inspector" tab
4. Browse tables visually or run SQL queries

**Pros:** Visual, live updates, no setup
**Cons:** Requires debuggable build and connected device

---

## Method 2: Pull Database + SQLite Browser

**Best for:** Detailed analysis, exporting data

**Steps:**

1. **Pull the database:**
   ```bash
   adb exec-out run-as com.medreminder.app cat databases/medication_database > medication_database.db
   ```

2. **Download [DB Browser for SQLite](https://sqlitebrowser.org/)**

3. **Open the .db file** in DB Browser

4. **Browse tables**, execute queries, export as CSV/JSON

---

## Method 3: ADB Commands (Quick Checks)

**Best for:** Quick verification, CI/CD, scripts

### View SharedPreferences

```bash
# Pending medications
adb shell "run-as com.medreminder.app cat shared_prefs/pending_medications.xml"

# App settings
adb shell "run-as com.medreminder.app cat shared_prefs/app_prefs.xml"
```

### List Database Files

```bash
adb shell "run-as com.medreminder.app ls -lh databases/"
```

### Use the Helper Script

```bash
./debug_db.sh
```

This script shows:
- Pending medications (SharedPreferences)
- App preferences
- Database file sizes
- Instructions to pull database

---

## Method 4: Logcat + Manual Logging

Add temporary logging to your code:

```kotlin
// In any DAO or ViewModel
lifecycleScope.launch(Dispatchers.IO) {
    val meds = medicationDao.getAllMedicationsSync()
    Log.d("DEBUG_DB", "Medications count: ${meds.size}")
    meds.forEach {
        Log.d("DEBUG_DB", "Med: id=${it.id}, name=${it.name}, times=${it.reminderTimesJson}")
    }
}
```

Then view logs:
```bash
adb logcat -s DEBUG_DB:D
```

---

## Common Queries

### Check if medications table is empty
```sql
SELECT COUNT(*) FROM medications;
```

### View all medications with details
```sql
SELECT id, name, reminderTimesJson, createdAt FROM medications;
```

### Check medication history
```sql
SELECT 
  medicationId,
  medicationName,
  datetime(scheduledTime/1000, 'unixepoch') as scheduled,
  datetime(takenTime/1000, 'unixepoch') as taken,
  status
FROM medication_history
ORDER BY scheduledTime DESC
LIMIT 20;
```

### Find stale pending medications
```sql
-- This would need to be done in code, but shows the concept:
SELECT p.* FROM pending_medications p
LEFT JOIN medications m ON p.medicationId = m.id
WHERE m.id IS NULL;
```

---

## Troubleshooting

### "Permission denied" when accessing database
- Ensure app is debuggable (`android:debuggable="true"` or debug build type)
- Use `run-as` command prefix
- Check device has root or app is debuggable

### Database is locked
- Close Android Studio Database Inspector
- Stop all app processes: `adb shell am force-stop com.medreminder.app`
- Try again

### Empty or missing tables
- Check database version in `MedicationDatabase.kt`
- Verify migrations or `fallbackToDestructiveMigration()` usage
- Clear app data and reinstall if needed

---

## Production Considerations

**IMPORTANT:** These tools only work on:
- Debug builds
- Rooted devices
- Apps with `android:debuggable="true"`

For production:
- Remove debug screens/menus
- Add proper analytics/crash reporting
- Consider admin-only export features with authentication

