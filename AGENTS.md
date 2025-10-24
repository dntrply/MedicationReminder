Agent Working Agreement — Tests Only

Purpose
- This repository uses this file to guide agents who contribute automated tests. The sole objective is to add and maintain test code that verifies existing behavior. Do not modify production application code.

Scope and Precedence
- Scope: Entire repository unless a more-specific AGENTS.md exists deeper in the tree.
- Precedence: If another AGENTS.md exists in a subdirectory, its rules take precedence for files under that subdirectory.
- These rules override generic agent defaults. When in doubt, favor “ask before changing.”

Hard Rules (Do / Don’t)
- Do: Add unit, integration, and UI tests only.
- Do: Create or modify files under `app/src/test/**` and `app/src/androidTest/**` (and their `resources`/`assets` folders) exclusively.
- Do: Add test-only helpers under those test source sets (e.g., `com.medreminder.app.testutil`).
- Do: Keep tests isolated, deterministic, and fast. Prefer in-memory fakes over real I/O.
- Don’t: Change any source under `app/src/main/**` or any production code, resources, or manifests.
- Don’t: Change public APIs, method signatures, or behavior of production classes.
- Don’t: Move, rename, or delete production files.

Build and Dependencies
- Default to using existing dependencies. If a test requires a new test-only dependency (e.g., `testImplementation` or `androidTestImplementation`):
  - Ask for explicit approval before editing build scripts.
  - Do not add or change non-test (production) dependencies.
- Never change compile SDK, min/target SDK, plugins, or release build configuration.

Test Locations and Naming
- Unit tests: `app/src/test/java/**` using JVM tests (Robolectric optional).
- Instrumented tests: `app/src/androidTest/java/**` for tests that require an emulator/device.
- Resources/fixtures for unit tests: `app/src/test/resources/**`.
- Assets/fixtures for instrumented tests: `app/src/androidTest/assets/**` (or `res/raw` test-only if needed).
- Class naming: `FooBarTest.kt` for unit tests, `FooBarAndroidTest.kt` for instrumented tests when helpful.
- Method naming: `fun doesThing_whenCondition_expectOutcome()` or clear, behavior-focused names.

Frameworks and Libraries (preferred)
- JUnit 4 or 5 (align with project defaults; prefer JUnit 4 if uncertain).
- Kotlin coroutines test: `kotlinx-coroutines-test` for testing `ViewModel` and suspend functions.
- Mocking: `mockk` preferred; use fakes over mocks when practical.
- AndroidX Test: `androidx.test.ext:junit`, `androidx.test:core`, and Espresso for UI.
- Compose UI tests: `androidx.compose.ui:ui-test-junit4`, `androidx.compose.ui:ui-test-manifest`.
- Robolectric: for JVM tests of Android components without a device.
- Room testing: In-memory database via `Room.inMemoryDatabaseBuilder(...)` in tests.

Project-Specific Notes (MedicationReminder)
- Package: `com.medreminder.app`.
- Database/DAO tests: Use in-memory Room DB and clear after each test. Do not touch `medication_database.db` in the repo.
- Coroutines: Use `StandardTestDispatcher` and `runTest {}`; avoid `Thread.sleep`.
- Notifications/alarms/broadcasts: Prefer Robolectric shadows in unit tests; use AndroidX Test with fakes on device tests.
- Compose screens (e.g., `TakeMedicationsScreen`, `SettingsScreen`, etc.): Use `createAndroidComposeRule<MainActivity>()` for `androidTest` and `composeTestRule` appropriately. Interact via semantics and testTags.
- Audio utils: Use test assets or fakes; do not access microphone or external storage.

Flakiness and Isolation
- No sleeps; rely on idling resources, compose test synchronization, or coroutine test dispatchers.
- No network access; tests must run offline.
- No external time dependency; control time via test dispatchers or clocks if needed.

Running Tests (Windows + Git Bash)
- All unit tests: `./gradlew test`
- App unit tests: `./gradlew :app:testDebugUnitTest`
- Instrumented tests (requires emulator/device): `./gradlew :app:connectedDebugAndroidTest`
- Compose UI tests (instrumented): ensure an emulator is running before executing connected tests.

Test Structure Guidelines
- Arrange-Act-Assert or Given-When-Then structure; keep assertions focused.
- Single behavior per test method when reasonable.
- Favor small, composable test helpers in `testutil` packages within test source sets.
- Validate edge cases, error paths, and state restoration where applicable.

Code Style
- Kotlin style consistent with project defaults.
- No inline comments replicating test names; keep tests self-explanatory.
- Keep imports explicit and avoid wildcard imports.

Review and Safety Checks
- If a test requires any change outside test source sets or adding test dependencies, pause and request approval.
- Before submitting, ensure tests pass locally with the commands above.

Examples of Allowed Changes
- Adding `MedicationViewModelTest.kt` under `app/src/test/java/...` using coroutines test APIs.
- Adding Compose UI tests under `app/src/androidTest/java/...` interacting with UI via semantics.
- Adding `Room` in-memory DB tests for `MedicationDao` under `app/src/test/java/...`.
- Adding small fixtures under `app/src/test/resources` or `app/src/androidTest/assets`.

Examples of Disallowed Changes
- Editing files under `app/src/main/**` (Kotlin, XML, resources, manifests).
- Modifying Gradle plugin versions, SDK versions, or production dependencies.
- Updating app logic to make it “more testable” without prior explicit approval.

Checklist Before You Finish
- Tests compile and run deterministically.
- No production files changed, moved, or deleted.
- Any new test-only dependencies were explicitly approved before build file edits.
- Clear test names and coverage for positive, negative, and edge cases.

