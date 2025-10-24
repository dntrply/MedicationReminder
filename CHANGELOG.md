# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.9.2] - 2025-01-24

Patch release enhancing Debug Data screen with version display and history management.

### Added
- **Version display in Debug Data** - App version (name and code) now shown in Debug Data screen title
- **Delete all history button** - Red delete icon in Debug Data allows clearing all medication history with confirmation dialog
- `deleteAllHistory()` function in MedicationHistoryDao for bulk history deletion

### Changed
- Improved Debug Data layout: Delete button positioned between section title and expand/collapse arrow
- All expand/collapse arrows consistently positioned on extreme right for better UX

### Technical
- Updated app version: versionCode = 3, versionName = "0.9.2"

## [0.9.1] - 2025-01-24

Patch release with important fixes for timeline display, notification behavior, and small screen support.

### Added
- **Smart full-screen intent** - App only surfaces automatically when device is locked or screen is off; doesn't interrupt active phone usage
- **Action field in Debug Data** - History entries now show whether medication was TAKEN, MISSED, or SKIPPED

### Fixed
- **Timeline green checkmark bug** - Fixed issue where medications with MISSED or SKIPPED history incorrectly showed green checkmark instead of pending status
- **Medication entry screens scrollability** - Added vertical scrolling to "What is your medication?" and "Add voice instructions?" screens to ensure buttons are visible on small screens (Pixel 3a)
- **Full-screen notification behavior** - Notifications now intelligently detect device state and only launch app when appropriate (locked/screen off)

### Changed
- Timeline now correctly distinguishes between TAKEN medications (green checkmark) and other actions (no checkmark)
- Full-screen intents only trigger when screen is off or device is locked, providing less disruptive notification experience

## [0.9.0] - 2025-01-24

Beta release with complete MVP features for medication management, history tracking, and overdue medication alerts.

### Added
- Initial medication management features
- Photo capture and gallery selection for medications
- Multi-language support (English, Hindi, Gujarati)
- Daily timeline view showing scheduled medications
- Notification system with repeat reminders
- **History Screen** - Complete medication history viewer with filtering by date or medication, includes missed dose tracking
- **Overdue Medication Tracking** - Visual alert system for medications from previous days with dedicated collapsible section
- **Missed Dose Calculator** - Automatically identifies scheduled doses that weren't taken
- **Reaction palette on timeline** - Quick action popup when tapping medication icons in timeline view
- **Hybrid approach for medication management** - Both dedicated screen (via menu) and quick actions (via timeline tap)
- Outstanding medication indicators on timeline (red border for pending, green checkmark for taken)
- Auto-cleanup of stale pending medication data
- Developer Debug Data viewer with export to JSON functionality
- Settings screen to manage Language, Preset Times, Notification Repeat Interval, and Privacy settings in one place
- Audio control on medication cards (play/stop recorded note)
- **Custom app icon** - Professional medical-themed icon with red/green capsule pill and blue medical cross
- Privacy settings for lock screen notification visibility control
- Floating Action Button (FAB) for quick medication addition

### Changed
- Language changes are performed from Settings and are auto-saved and applied immediately
- Preset time changes are auto-saved from Settings and used across the app
- Timeline scrolls to actual pending reminders first; otherwise to next scheduled time
- Add flow uses a draft/upsert model; changes auto-save from first screen (name/photo/audio) through schedules
- Medication card layout: reserved slim audio column (consistent name alignment), stacked name + schedules
- Reduced spacing between photo and audio column for tighter layout
- **Notification repeat interval slider** - Now updates value in real-time as you move the slider for immediate feedback
- **Timeline view is now the default view** on home screen
- Home screen with collapsible lower section to maximize overdue medication visibility
- Section headers distinguish "Today's Schedule" from "Your Medications"

### Removed
- "Change Language" menu item from Home (now under Settings)
- "Customize" button/dialog on the "When to Take" screen (preset times are edited in Settings)
- "Set Times" quick action on card (use Edit)
- Outstanding Medications standalone screen (functionality integrated into home screen)

### Fixed
- Data inconsistency between notifications and home screen after app restart
- Pending medication tracker now validates against database on app start
- **Duplicate notification issue** - Fixed duplicate entries in pending medication tracker by deduplicating on medication ID + hour + minute
- **Instant UI updates** - Outstanding Medications screen now removes medications immediately when marked as taken
- **Outstanding medication logic** - Timeline only shows red alerts for medications that actually had notifications sent (not retroactively for late-added reminder times)
- **Vertical digit stacking** - Fixed hour/minute input fields to always display digits horizontally on smaller screens (Pixel 3a)
- **Add flow duplicates** - Prevented two rows from being created when adding multiple times; upserts into the same draft row
- **Time dialog keyboard** - Tab/Shift+Tab moves between hour/minute and commits values; stabilized focus to avoid loops

### Technical
- Database version bumped to 3 with migration support
- Added medication history queries for date range and adherence tracking
- Enhanced notification privacy controls
- Improved pending medication validation and synchronization

## [1.0.0] - TBD

Planned stable release after beta testing period.
