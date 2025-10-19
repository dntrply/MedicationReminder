# Medication Reminder App - MVP Specification

## Overview
A simple Android app to help patients (starting with elderly users) remember to take their medications through visual reminders and gentle notifications.

## Target User
- Primary: Elderly patients taking daily medications (e.g., your mom)
- Secondary (future): Caregivers who want to monitor adherence

## Core Problem
Traditional alarms lack context - users may forget which medication to take, especially when managing multiple prescriptions.

## MVP Features (Version 1.0)

### 1. Medication Management
- **Add Medication**
  - Medication name (text input)
  - Photo of the medication (camera or gallery)
  - Optional notes (e.g., "take with food", "2 tablets")
  - Dosage/quantity (simple text field)

- **Edit/Delete Medication**
  - Modify medication details
  - Remove medications no longer needed

### 2. Reminder Scheduling
- **Set reminder time(s)** for each medication
  - Simple time picker
  - Support multiple times per day (e.g., morning and evening)
  - Days of week selection (for medications not taken daily)

- **Notification**
  - Clear notification with medication name
  - Tapping notification opens full reminder screen

### 3. Reminder Screen (When notification arrives)
- **Large, clear display showing:**
  - Medication photo (prominent, full-width)
  - Medication name (large font)
  - Time scheduled
  - Dosage/instructions
  - Two action buttons:
    - ✓ "I've taken it" (green)
    - "Remind me in 10 minutes" (yellow)

### 4. Simple Home Screen
- **List of all medications** with:
  - Small thumbnail photo
  - Medication name
  - Next scheduled time
  - Last taken timestamp (checkmark if taken today)

- **Add button** (floating action button)

### 5. Basic History
- **Today's View**
  - Which medications were taken today
  - Which were missed
  - Simple checkmark/X indicator

## Out of Scope for MVP (Future Versions)
- Caregiver alerts/monitoring
- Medication refill reminders
- Integration with pharmacy systems
- Medication interaction warnings
- Cloud sync/backup
- Multiple user profiles
- Advanced analytics/reports
- Doctor portal
- Prescription scanning

## Technical Requirements

### Must Have
- Works offline (no internet required)
- Reliable notifications (even when app is closed)
- Persistent storage (data survives app restart)
- Simple, large UI elements (accessibility for elderly)

### Platform
- Android 8.0+ (API level 26+)
- Phone and tablet support

### Key Technologies (Suggested)
- Kotlin
- Jetpack Compose (modern UI)
- Room Database (local storage)
- WorkManager (reliable notifications)
- CameraX (photo capture)

## User Experience Principles
1. **Large, readable text** - minimum 18sp for body text
2. **High contrast** - easy to see in various lighting
3. **Simple navigation** - minimal taps to complete tasks
4. **Forgiving** - easy to undo/correct mistakes
5. **Reassuring** - clear confirmation of actions

## Success Metrics (After 2 weeks of use)
- Mom takes correct medication 95%+ of the time
- No confusion about which medication to take
- App notifications are reliably received
- User can add/modify medications independently

## Privacy & Security
- All data stored locally on device
- No data sent to external servers (MVP)
- Photos stored in app-private directory
- No login required

## Timeline Estimate
- Design & prototyping: 1 week
- Core development: 3-4 weeks
- Testing with real user (mom): 1-2 weeks
- Refinements: 1 week

**Total: ~6-8 weeks for a solid MVP**
