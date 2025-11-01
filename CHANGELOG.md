# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.13.0] - 2025-11-01

Enhanced timeline visualization with 2-hour intervals and improved medication positioning for better schedule overview.

### Changed
- **Timeline view redesigned with 2-hour intervals** - Displays medications in 12 two-hour blocks (12-1 PM, 2-3 PM, etc.) instead of 24 hourly blocks for better overview
- **Improved medication positioning** - Horizontal position based on hour (left/right half of block), vertical position based on minutes in 15-minute slots
- **Smart vertical staggering** - 20dp offset for medications at different times, 8dp for medications at exact same time
- **Z-order optimization** - Earlier medications appear on top with higher elevation for better visibility
- **UI refinements** - Larger audio buttons (24dp→30dp), improved spacing, increased font sizes for better readability
- **Transcription statistics visibility** - Now hidden in Debug Data screen when transcription feature is disabled

### Technical
- Timeline scroll position calculation updated for 2-hour block navigation
- Medication grouping logic enhanced to handle 8 buckets per 2-hour block
- Drawing order reversed so earlier medications render last (on top)
- Elevation calculation optimized for visual hierarchy

## [0.12.0] - 2025-10-31

Major release adding on-device audio transcription with Whisper Tiny, global feature toggle, and comprehensive statistics tracking.

### Added
- **Global transcription feature toggle** - OFF by default for lightweight experience on low-budget phones
  - New "Features" section in Settings with Audio Transcription toggle
  - Immediate consent dialog when enabling (75MB download, WiFi/charging requirements)
  - Feature disabled = audio saved but never transcribed (no dialog, no download, no processing)
  - Localized strings in all 4 languages (English, Hindi, Gujarati, Marathi)
- **Transcription statistics tracking** - Monitor transcription performance and success rates
  - New TranscriptionStats entity and DAO for tracking pending/success/failed transcriptions
  - Stats tracked: medication name, status, timestamps, audio file info, transcription results, model details
  - Pending stats created immediately when audio is recorded (not waiting for WorkManager)
  - Debug Data screen displays transcription statistics with delete option
  - Summary statistics methods for total/success/failed/pending counts
- **Camera permission runtime request** - Fixed crash when taking medication/profile photos
  - PhotoPicker now requests camera permission before launching camera
  - Shared component ensures fix applies to both medication and profile photos
- **Comprehensive troubleshooting documentation**
  - BUILD_COMMANDS.md - Build, installation, and deployment commands for development
  - TRANSCRIPTION_TROUBLESHOOTING.md - Emulator and real device debugging guide
- **Audio transcription and translation infrastructure** - Foundation for converting medication audio notes to text
  - ML Kit Translation API integration for on-device translation between English, Hindi, Gujarati, and Marathi
  - ML Kit Language Identification for automatic language detection
  - Background transcription worker using WorkManager for non-blocking processing
  - Database schema updated to store transcription text and source language
  - UI displays transcribed text on medication cards with on-demand translation
  - Silent failure mode - transcription errors don't impact user experience

### Technical
- **Plugin Architecture for Transcription Engines:**
  - Created `SpeechToTextEngine` interface - contract for all transcription engines
  - Created `TranscriptionEngineFactory` with auto-detection logic
  - Implemented pluggable design allowing easy engine switching without code changes
  - BuildConfig field for Google Cloud API key (enables cloud engine when set)

- **Whisper Tiny Engine (official whisper.cpp Android JNI):**
  - Integrated official whisper.cpp Android native library via git submodule
  - Created `WhisperTinyEngine` with device capability detection (RAM, storage)
  - Uses ggml format models from Hugging Face (ggerganov/whisper.cpp)
  - Only requires single model file (whisper-tiny.bin, ~75MB) - no separate vocabulary file needed
  - Native C++ compilation via CMake for optimal Android performance
  - Supports English, Hindi, Gujarati, Marathi, and 95+ other languages
  - Architecture-specific optimization (~3-5MB per platform vs 10-15MB multi-platform)

- **Translation & Database:**
  - Added ML Kit dependencies: translate (17.0.2), language-id (17.0.5)
  - Added kotlinx-coroutines-play-services (1.7.3) for ML Kit integration
  - Refactored `AudioTranscriptionService` to use factory pattern
  - Created `AudioTranscriptionWorker` for background processing with charging requirement
  - Created `TranscriptionScheduler` utility for managing background jobs
  - Database migration 6→7 adds `audioTranscription` and `audioTranscriptionLanguage` fields

- **UI & Integration:**
  - Updated `MedicationViewModel` to trigger transcription on audio save/update
  - Updated `MedicationCard` to display translated transcription text
  - Configured WorkManager constraints to require device charging for transcription
  - Created `NoOpEngine` for graceful fallback when no engine available

### Design Benefits
- **Plug-and-play:** Add new transcription engines by creating one file implementing `SpeechToTextEngine`
- **Auto-detection:** Factory selects best engine based on device capabilities and configuration
- **Zero impact switching:** Change from Whisper to Google Cloud requires only API key in build.gradle
- **Future-proof:** Easy to add new engines (cloud services, better models, etc.)
- **Backward compatible:** Existing code unchanged when adding/switching engines

### Implementation Complete: Audio Transcription Pipeline ✅
- **Model Download Manager:** Automatic download from Hugging Face with progress tracking
  - WiFi detection before download (protects mobile data)
  - Storage space verification (requires 100MB free)
  - Auto-retry logic (up to 3 attempts with exponential backoff)
  - Download timeout: 5 minutes for slow connections
  - Fixed RAM requirement from 2048MB to 1900MB to support standard emulators
  - Model file: whisper-tiny.bin (75MB) successfully downloads (no vocabulary file needed)

- **Native Library Integration:**
  - Switched from TensorFlow Lite to official whisper.cpp Android JNI - Resolved ggml format compatibility
  - Added whisper.cpp repository as git submodule in `external/whisper.cpp/`
  - Integrated official whisper.android lib module with native CMake build
  - NDK 25.2.9519653 auto-installed and configured
  - Native libraries successfully compiled for all architectures (arm64-v8a, armeabi-v7a, x86, x86_64)
  - Downgraded Gradle to 8.5 for compatibility with whisper lib module
  - Build successful with ~3-5MB native library per architecture

- **Audio Processing Pipeline:**
  - Created `AudioProcessor` utility for M4A/WAV file conversion
  - MediaCodec-based decoder for M4A files (AAC codec at 44.1kHz)
  - Simple WAV file decoder for future compatibility
  - Linear interpolation resampling from 44.1kHz to 16kHz
  - Stereo to mono conversion with channel averaging
  - Normalization to FloatArray (-1.0 to 1.0 range)

- **Transcription & Language Detection:**
  - Integrated WhisperContext API for transcription execution
  - ML Kit Language Identification for automatic language detection
  - Supports English, Hindi, Gujarati, Marathi, and 95+ other languages
  - Full error handling with graceful fallbacks
  - Performance logging (duration, sample count)

- **User Consent System:**
  - Created `TranscriptionConsentDialog` with multi-language support
  - Consent preferences stored in DataStore (SettingsStore)
  - Dialog shows model size (75MB), requirements (WiFi, charging), and privacy info
  - First-time consent request before downloading model
  - Transcription only proceeds if user grants consent

### Changed
- **Database schema management** - Removed all migration code for development simplicity
  - App now uses fallbackToDestructiveMigration() for faster iteration
  - Fresh installs create clean database at version 8 with all tables and indices
- **Settings UI organization** - Added collapsible sections and new Features category
  - Features section for opt-in advanced capabilities (transcription, future AI features)
  - Better visual hierarchy with expandable/collapsible preset times section

### Fixed
- **Camera permission crash on Android 6.0+** - App no longer crashes when taking photos
  - PhotoPicker now properly requests CAMERA permission at runtime before launching camera
  - Fix applies to both medication photos and profile photos (shared component)
- **Transcription stats SQLite constraint violation** - Fixed duplicate primary key error
  - AudioTranscriptionWorker now updates existing stats instead of inserting duplicates
  - Added @Update method to TranscriptionStatsDao for proper stats updates
  - Worker checks for existing stats entry before creating new one
- **Missing transcription stats for pending jobs** - Stats now created immediately
  - TranscriptionScheduler creates pending stats entry before scheduling WorkManager job
  - No longer waiting for worker to run before stats appear in Debug Data
  - Audio file size and duration calculated immediately when scheduling

### Technical
- **Updated app version:** versionCode = 6, versionName = "0.12.0"
- **Database schema version 8** with TranscriptionStats table
  - TranscriptionStats entity with indices on medicationId and status fields
  - Tracks pending/success/failed transcriptions with detailed metadata
  - Database migration 7→8 adds transcription_stats table
- **New files:**
  - `TranscriptionStats.kt` - Entity for transcription statistics
  - `TranscriptionStatsDao.kt` - DAO with insert, update, query, and summary methods
  - `TranscriptionConsentDialog.kt` - Multi-language consent dialog
  - `AudioProcessor.kt` - M4A/WAV to 16kHz mono float array conversion
  - `AudioTranscriptionService.kt` - Whisper integration service
  - `TranscriptionScheduler.kt` - WorkManager job scheduling utility
  - `AudioTranscriptionWorker.kt` - Background transcription worker
  - `BUILD_COMMANDS.md` - Development command reference
  - `TRANSCRIPTION_TROUBLESHOOTING.md` - Debugging guide
- **Modified files:**
  - `MedicationDatabase.kt` - Removed migrations, simplified to fallbackToDestructiveMigration()
  - `SettingsStore.kt` - Added transcriptionEnabled preference (default: false)
  - `MedicationViewModel.kt` - Added consent dialog state and scheduling with feature check
  - `SettingsScreen.kt` - Added Features section with transcription toggle and consent dialog
  - `DebugDataScreen.kt` - Added transcription statistics section with delete confirmation
  - `PhotoPicker.kt` - Added camera permission launcher
  - `app/build.gradle.kts` - Added whisper.cpp native library dependency
  - `proguard-rules.pro` - Added rules for Whisper native library
- **String resources:** Added features_label, audio_transcription, audio_transcription_description in all 4 languages
- **Build configuration:**
  - Whisper.cpp added as git submodule in `external/whisper.cpp/`
  - NDK 25.2.9519653 configured for native compilation
  - Release builds target ARM64 only (28MB APK)
  - Debug builds include all architectures for emulator support

### Note
- **Implementation Status:** Audio transcription pipeline complete with user consent - ready for production
- **Next Steps:** On-device testing → Performance optimization → User feedback
- **Privacy-first design:** User must explicitly consent before 75MB model downloads
- Transcription runs only when device is charging (protects battery on low-end phones)
- All translation happens on-device using compact ML Kit models (~30-40MB per language)
- Architecture supports future Google Cloud Speech-to-Text integration (just add API key)
- Using whisper.cpp JNI bindings for native C++ performance with ggml models
- Consent dialog available in all 4 languages (English, Hindi, Gujarati, Marathi)

## [0.11.0] - 2025-10-27

Minor release adding Marathi language support and fixing localization issues.

### Added
- **Marathi language support** - Complete translation with 187 strings in values-mr/strings.xml
- Marathi (मराठी) now available alongside English, Hindi (हिंदी), and Gujarati (ગુજરાતી)
- `late_label` string resource for "LATE" badge in all 4 languages

### Changed
- **Refactored MainActivity.kt for better i18n** - Replaced 27 hardcoded `when(currentLanguage)` blocks with `stringResource()` calls
- **Refactored SettingsScreen.kt for better i18n** - Replaced 12 hardcoded blocks with `stringResource()` calls
- Added 45+ new string resources across all language files for better maintainability
- Updated HistoryScreen.kt and SetReminderTimesScreen.kt to include Marathi translations in remaining hardcoded blocks

### Fixed
- **LATE badge now displays in user's selected language** - Previously showed "LATE" in English regardless of language setting
- Added locale initialization in `onCreate()` to ensure `stringResource()` picks up correct language
- Locale properly set via both `attachBaseContext()` and `onCreate()` for consistent behavior

### Technical
- Updated app version: versionCode = 5, versionName = "0.11.0"
- Created values-mr/strings.xml with complete Marathi translations
- MainActivity.kt: Significantly reduced code with stringResource() refactoring (168 deletions)
- SettingsScreen.kt: Simplified with stringResource() refactoring (79 deletions)
- Adding future languages now only requires creating new values-{locale}/strings.xml files

## [0.10.0] - 2025-01-26

Minor release adding multi-profile support with profile-specific medication management, notifications, and history.

### Added
- **Multi-profile support** - Create and manage multiple user profiles for shared devices
- **Profile management screen** - Add, edit, and delete profiles with photos and custom notification messages
- **Profile switcher in top bar** - Quick profile switching via dropdown menu with profile photos
- **Profile-specific notifications** - Custom notification message templates with {profileName} and {medicationName} placeholders
- **Profile photos** - Add profile pictures via camera or gallery with circular display
- **Profile-aware history** - History screen now filters by active profile
- **Common photo picker component** - Reusable PhotoPicker for both profiles and medications
- New database entities: Profile, ProfileDao
- Profile-aware database queries for medications and history
- FileProvider support for internal files (camera photos)

### Changed
- Database migration to version 6 with Profile table
- Medications now linked to profiles via profileId foreign key
- History records now track profileId for filtering
- Active profile stored in SettingsStore using DataStore
- Debug Data screen shows all profiles and cross-profile data (unchanged behavior)
- Home screen displays active profile indicator in top bar
- Notification system uses profile-specific message templates

### Fixed
- FileProvider configuration to support temp camera files in internal storage
- Photo picker state management issue causing dialog not to appear
- Profile context honored throughout the app

### Technical
- Updated app version: versionCode = 4, versionName = "0.10.0"
- Database schema version 6 with migration from v5
- Added `files-path` to FileProvider for temp image support

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
