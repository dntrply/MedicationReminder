# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial medication management features
- Photo capture and gallery selection for medications
- Multi-language support (English, Hindi, Gujarati)
- Daily timeline view showing scheduled medications
- Notification system with repeat reminders
- Medication history tracking
- **Outstanding Medications screen** - Dedicated full-screen view to manage all pending medications with large, elderly-friendly action buttons
- **Reaction palette on timeline** - Quick action popup when tapping medication icons in timeline view
- **Hybrid approach for medication management** - Both dedicated screen (via menu) and quick actions (via timeline tap)
- Outstanding medication indicators on timeline (red border for pending, green checkmark for taken)
- Auto-cleanup of stale pending medication data
- Developer Debug Data viewer with export to JSON functionality

### Fixed
- Data inconsistency between notifications and home screen after app restart
- Pending medication tracker now validates against database on app start
- **Duplicate notification issue** - Fixed duplicate entries in pending medication tracker by deduplicating on medication ID + hour + minute
- **Instant UI updates** - Outstanding Medications screen now removes medications immediately when marked as taken
- **Outstanding medication logic** - Timeline only shows red alerts for medications that actually had notifications sent (not retroactively for late-added reminder times)
- **Vertical digit stacking** - Fixed hour/minute input fields to always display digits horizontally on smaller screens (Pixel 3a)

## [1.0.0] - TBD

Initial release - MVP for medication reminder app focused on elderly users.
