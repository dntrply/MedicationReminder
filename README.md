# Medication Reminder App

A simple Android app to help patients (especially elderly users) remember to take their medications through visual reminders and gentle notifications.

## Overview

This app provides medication reminders with visual aids (photos of actual medications) to improve medication adherence. Built with a focus on simplicity and accessibility for elderly users.

## Features (MVP)

- **Add Medications** with photo, name, dosage, and notes
- **Schedule Reminders** for specific times and days with customizable preset times
- **Visual Notifications** showing medication photo and details with action buttons
- **Multiple Ways to Mark Medications Taken**:
  - From notification actions (Taken/Snooze/Skip)
  - Dedicated "Medications to Take" screen with large buttons
  - Quick actions by tapping medication icons in timeline view
- **Daily Timeline View** showing all scheduled medications with visual status indicators
- **Multi-language Support** - English, Hindi, and Gujarati
- **Medication History Tracking** to see which medications were taken and when
- **Developer Tools** - Debug data viewer with export functionality
- **Offline-First** - works without internet connection

### Settings & Personalization
- Central Settings screen for preferences
  - Language selection (English, हिन्दी, ગુજરાતી) — applies immediately
  - Preset meal times (Morning/Lunch/Evening/Bedtime) — auto-saved
- Quick Select buttons on the "When to Take" screen reflect these presets
- The older per-screen "Customize" dialog has been removed to keep preferences in one place

### Medication Cards
- Clear state when unscheduled: shows "No schedule set"
- Compact audio control: small play/stop icon in a slim column; name and schedules align consistently

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + Material 3
- **Database**: Room (SQLite)
- **Notifications**: WorkManager
- **Camera**: CameraX
- **Image Loading**: Coil
- **Architecture**: MVVM

## Requirements

- Android 8.0 (API 26) or higher
- Camera (optional - can use gallery instead)
- Notification permissions

## Project Structure

```
app/
├── src/main/
│   ├── java/com/medreminder/app/
│   │   ├── data/              # Data layer
│   │   │   ├── local/         # Room database entities & DAOs
│   │   │   └── repository/    # Repository pattern
│   │   ├── ui/                # UI layer (Compose)
│   │   │   ├── theme/         # Theme & styling
│   │   │   ├── home/          # Home screen
│   │   │   ├── addmedication/ # Add/Edit medication
│   │   │   └── reminder/      # Reminder screen
│   │   ├── notifications/     # Notification handling
│   │   └── utils/             # Utilities
│   └── res/                   # Resources
docs/                          # Documentation
```

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK with API 34

### Building the Project

1. Clone this repository
2. Open in Android Studio
3. Sync Gradle files
4. Run on emulator or physical device

```bash
./gradlew build
```

### Install/Run on emulator quickly

```bash
./gradlew assembleDebug installDebug
adb shell am start -n com.medreminder.app/.MainActivity
```

### Running Tests

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Documentation

See the [docs](./docs) folder for detailed specifications:

- [MVP Specification](./docs/medication-reminder-spec.md) - Feature list, requirements, timeline
- [User Flow & Wireframes](./docs/medication-reminder-userflow.md) - User journeys and screen designs

## Design Principles

1. **Large, readable text** - minimum 18sp for body text
2. **High contrast** - easy to see in various lighting conditions
3. **Simple navigation** - minimal taps to complete tasks
4. **Forgiving** - easy to undo/correct mistakes
5. **Reassuring** - clear confirmation of actions

## Privacy & Security

- All data stored locally on device
- No data sent to external servers
- Photos stored in app-private directory
- No login or account required

## Roadmap

### Version 1.0 (Current - MVP)
- ✓ Basic medication management
- ✓ Photo reminders with camera/gallery support
- ✓ Flexible scheduling with custom and preset times
- ✓ Daily timeline view with visual status indicators
- ✓ Multiple ways to mark medications as taken (notifications, dedicated screen, timeline tap)
- ✓ Medication history tracking
- ✓ Multi-language support (English, Hindi, Gujarati)
- ✓ Developer debug tools

### Version 2.0 (Future)
- Caregiver monitoring/alerts
- Medication refill reminders
- Export history reports (CSV/PDF)
- Multiple user profiles
- Cloud backup
- Voice reminders

## Contributing

This is currently a personal project. Contributions welcome after initial release.

## License

TBD

## Contact

For questions or feedback, please open an issue.

---

Built with focus on helping elderly patients manage their medications safely and independently.
