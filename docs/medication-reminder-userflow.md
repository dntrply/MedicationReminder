# Medication Reminder App - User Flow & Wireframes

## User Journey Map

### First Time Setup Flow

```
[App Launch (First Time)]
    ↓
[Welcome Screen]
"Never forget your medication again"
[Get Started Button]
    ↓
[Empty Home Screen]
"No medications yet"
[+ Add Your First Medication]
    ↓
[Add Medication Screen]
```

### Primary User Flows

## Flow 1: Adding a Medication (First Time User)

```
Step 1: Home Screen (Empty State)
┌─────────────────────────────────┐
│  Medication Reminder            │
│                                 │
│  ┌───────────────────────────┐ │
│  │                           │ │
│  │   📋 No medications yet   │ │
│  │                           │ │
│  │   Add your first          │ │
│  │   medication to get       │ │
│  │   started                 │ │
│  │                           │ │
│  └───────────────────────────┘ │
│                                 │
│              [+]                │
│         Add Medication          │
└─────────────────────────────────┘
        ↓ (Tap +)

Step 2: Add Medication Screen
┌─────────────────────────────────┐
│  ← Add Medication               │
│                                 │
│  Medication Name *              │
│  ┌───────────────────────────┐ │
│  │ e.g., Aspirin             │ │
│  └───────────────────────────┘ │
│                                 │
│  Photo                          │
│  ┌───────────────────────────┐ │
│  │                           │ │
│  │      📷 Add Photo         │ │
│  │   (Tap to take or select) │ │
│  │                           │ │
│  └───────────────────────────┘ │
│                                 │
│  Dosage / Instructions          │
│  ┌───────────────────────────┐ │
│  │ e.g., 1 tablet            │ │
│  └───────────────────────────┘ │
│                                 │
│  Notes (Optional)               │
│  ┌───────────────────────────┐ │
│  │ Take with food            │ │
│  └───────────────────────────┘ │
│                                 │
│           [Next]                │
└─────────────────────────────────┘
        ↓ (Tap Next)

Step 3: Photo Capture Options
┌─────────────────────────────────┐
│  Choose Photo Source            │
│                                 │
│  ┌───────────────────────────┐ │
│  │  📷 Take Photo            │ │
│  └───────────────────────────┘ │
│                                 │
│  ┌───────────────────────────┐ │
│  │  🖼️  Choose from Gallery  │ │
│  └───────────────────────────┘ │
│                                 │
│  [Cancel]                       │
└─────────────────────────────────┘
        ↓ (After photo selected)

Step 4: Set Reminder Times
┌─────────────────────────────────┐
│  ← Set Reminder Times           │
│                                 │
│  When should we remind you?     │
│                                 │
│  ┌───────────────────────────┐ │
│  │  ⏰ 8:00 AM          [×]  │ │
│  └───────────────────────────┘ │
│                                 │
│  [+ Add Another Time]           │
│                                 │
│  Repeat on:                     │
│  ┌───┬───┬───┬───┬───┬───┬───┐ │
│  │ M │ T │ W │ T │ F │ S │ S │ │
│  │ ✓ │ ✓ │ ✓ │ ✓ │ ✓ │ ✓ │ ✓ │ │
│  └───┴───┴───┴───┴───┴───┴───┘ │
│                                 │
│  [Cancel]       [Save]          │
└─────────────────────────────────┘
```

## Flow 2: Receiving & Responding to Reminder

```
Step 1: Notification Arrives
┌─────────────────────────────────┐
│  🔔 Medication Reminder    8:00 │
│                                 │
│  Time to take Aspirin           │
│  1 tablet - Take with food      │
│                                 │
│          [View]                 │
└─────────────────────────────────┘
        ↓ (Tap notification or View)

Step 2: Full Reminder Screen
┌─────────────────────────────────┐
│  Medication Reminder            │
│                                 │
│  ╔═══════════════════════════╗ │
│  ║                           ║ │
│  ║   [Photo of Aspirin]      ║ │
│  ║   (Large, clear image)    ║ │
│  ║                           ║ │
│  ╚═══════════════════════════╝ │
│                                 │
│  Aspirin                        │
│  1 tablet                       │
│  Take with food                 │
│                                 │
│  Scheduled: 8:00 AM             │
│                                 │
│  ┌───────────────────────────┐ │
│  │   ✓ I've Taken It         │ │
│  └───────────────────────────┘ │
│                                 │
│  ┌───────────────────────────┐ │
│  │  ⏰ Remind Me in 10 Min    │ │
│  └───────────────────────────┘ │
│                                 │
│  [Dismiss]                      │
└─────────────────────────────────┘
        ↓ (Tap "I've Taken It")

Step 3: Confirmation
┌─────────────────────────────────┐
│                                 │
│      ✓                          │
│  Marked as Taken                │
│  8:05 AM                        │
│                                 │
│  (Auto-closes after 2 seconds)  │
└─────────────────────────────────┘
```

## Flow 3: Daily Usage - Home Screen

```
Home Screen (With Medications)
┌─────────────────────────────────┐
│  Medication Reminder     ⚙️      │
│                                 │
│  Today - October 18             │
│                                 │
│  ┌───────────────────────────┐ │
│  │ [thumb] Aspirin      ✓    │ │
│  │ 1 tablet                  │ │
│  │ Next: Tomorrow 8:00 AM    │ │
│  │ Taken: Today 8:05 AM      │ │
│  └───────────────────────────┘ │
│                                 │
│  ┌───────────────────────────┐ │
│  │ [thumb] Vitamin D         │ │
│  │ 1 capsule                 │ │
│  │ Next: Today 8:00 PM       │ │
│  │ Last taken: Yesterday     │ │
│  └───────────────────────────┘ │
│                                 │
│  ┌───────────────────────────┐ │
│  │ [thumb] Blood Pressure    │ │
│  │ 1 tablet - with food      │ │
│  │ Next: Tomorrow 8:00 AM    │ │
│  │ Taken: Today 8:10 AM  ✓   │ │
│  └───────────────────────────┘ │
│                                 │
│              [+]                │
└─────────────────────────────────┘

(Tap on any medication to view/edit details)
```

## Key Interaction States

### Medication Card States
1. **Upcoming** - Default state, shows next scheduled time
2. **Due Now** - Highlighted border, time shows "Now"
3. **Taken** - Checkmark visible, shows time taken
4. **Missed** - Red indicator if more than 1 hour past scheduled time

### Snooze Flow
```
[Remind Me in 10 Min]
    ↓
Notification dismissed
    ↓
10 minutes later
    ↓
Same notification appears again
(Can snooze up to 3 times, then shows "Missed" warning)
```

## Navigation Structure

```
Home Screen
├── [+] → Add/Edit Medication Flow
│   ├── Enter Details
│   ├── Add Photo
│   └── Set Reminders
│
├── [Medication Card] → Medication Detail
│   ├── View full info
│   ├── Edit
│   ├── Delete
│   └── View history for this med
│
├── [⚙️ Settings] → Settings Screen
│   ├── Notification sound
│   ├── Snooze duration
│   └── About
│
└── [Notification] → Reminder Screen
    ├── I've Taken It
    ├── Remind Me Later
    └── Dismiss
```

## Edge Cases Handled

1. **No photo added**: Show medication icon placeholder
2. **Notification while app open**: Show in-app alert + notification
3. **Multiple medications at same time**: Combined notification "Time for 2 medications"
4. **Phone restart**: Reminders persist and reschedule
5. **Missed dose**: Show gentle reminder in app, allow marking as "skipped" vs "taken late"

## Visual Design Notes

### Color Scheme
- **Primary**: Soft blue (#4A90E2) - calming, medical
- **Success**: Green (#5CB85C) - for "taken" actions
- **Warning**: Amber (#F0AD4E) - for snooze
- **Error**: Soft red (#D9534F) - for missed doses
- **Background**: White/very light gray (#FAFAFA)

### Typography
- **Headers**: 24sp, bold
- **Medication names**: 20sp, semibold
- **Body text**: 18sp, regular
- **Small text**: 16sp (minimum)

### Accessibility
- All touch targets minimum 48dp × 48dp
- High contrast ratios (4.5:1 minimum)
- Support for system font scaling
- Haptic feedback on important actions

## Prototype Testing Questions

When testing with your mom:
1. Can she add a medication without help?
2. Can she understand what the notification means?
3. Can she find the "I've taken it" button easily?
4. Is the photo helpful for identification?
5. Can she see/read all text comfortably?
6. Does she understand which medications were taken today?

## Next Steps for Implementation

1. Create detailed mockups in Figma (optional but helpful)
2. Set up Android project with Kotlin + Jetpack Compose
3. Implement data models (Medication, Reminder, History)
4. Build home screen with medication list
5. Implement add/edit medication flow
6. Set up notification system
7. Create reminder response screen
8. Add basic history tracking
9. User testing with your mom
10. Iterate based on feedback
