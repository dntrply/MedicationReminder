# Testing Guide for Medication Reminder App

This guide explains how to write and run tests for the Medication Reminder application.

## Table of Contents
- [Test Structure](#test-structure)
- [Running Tests](#running-tests)
- [Writing Tests](#writing-tests)
- [Test Utilities](#test-utilities)
- [Best Practices](#best-practices)
- [Examples](#examples)

---

## Test Structure

Our tests are organized into two main directories:

### 1. Unit Tests (`app/src/test/`)
**Purpose**: Test pure business logic without Android framework dependencies.

**Characteristics**:
- Run on the JVM (fast execution)
- No Android emulator/device required
- Test pure Kotlin/Java logic
- Use mocks for Android dependencies

**What to test here**:
- Utility functions (e.g., `TimeUtils.kt`)
- Business logic (e.g., `MissedDoseCalculator.kt`)
- Data models and transformations
- Non-Android specific algorithms

**Example**:
```kotlin
// app/src/test/java/com/medreminder/app/notifications/PendingMedicationTrackerTest.kt
@Test
fun findMissedDosesInGap_doseBeforeGapStart_isNotBackfilled() {
    // Tests pure logic without Android dependencies
}
```

### 2. Instrumented Tests (`app/src/androidTest/`)
**Purpose**: Test code that requires Android framework components.

**Characteristics**:
- Run on Android emulator or physical device
- Slower execution than unit tests
- Can access Android APIs
- Can test UI components

**What to test here**:
- Database operations (Room DAOs)
- UI components (Compose screens)
- Integration tests
- Android-specific functionality

**Example**:
```kotlin
// app/src/androidTest/java/com/medreminder/app/data/MedicationDaoTest.kt
@Test
fun insertMedication_andRetrieveById_returnsCorrectMedication() = runTest {
    // Requires Android Room database
}
```

---

## Running Tests

### Run Unit Tests
```bash
# All unit tests
./gradlew test

# Specific test class
./gradlew test --tests com.medreminder.app.notifications.PendingMedicationTrackerTest

# Specific test method
./gradlew test --tests com.medreminder.app.notifications.PendingMedicationTrackerTest.findMissedDosesInGap_doseBeforeGapStart_isNotBackfilled
```

### Run Instrumented Tests
```bash
# All instrumented tests (requires connected device/emulator)
./gradlew connectedDebugAndroidTest

# Specific test class
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.medreminder.app.data.MedicationDaoTest
```

### Run from Android Studio
1. Right-click on test file or test method
2. Select "Run 'TestName'"

---

## Writing Tests

### Test Naming Convention

Use descriptive names that explain what is being tested:

```kotlin
// ✅ Good - Clear and descriptive
@Test
fun insertMedication_withValidData_returnsGeneratedId()

@Test
fun calculateMissedDoses_whenAppRestartedAfterWeek_backfillsMissedDoses()

@Test
fun reminderNotification_whenMedicationDeleted_cancelsNotification()

// ❌ Bad - Unclear purpose
@Test
fun test1()

@Test
fun testInsert()
```

**Pattern**: `methodUnderTest_stateOrInput_expectedBehavior`

### Test Structure (AAA Pattern)

Follow the **Arrange-Act-Assert** pattern:

```kotlin
@Test
fun exampleTest() {
    // Arrange - Set up test data and dependencies
    val medication = TestUtils.createTestMedication(
        name = "Aspirin",
        dosage = "1 tablet"
    )

    // Act - Execute the code under test
    val result = medicationDao.insertMedication(medication)

    // Assert - Verify the expected outcome
    assertTrue(result > 0)
}
```

### Testing Coroutines

Use `runTest` from kotlinx-coroutines-test:

```kotlin
@Test
fun suspendFunction_test() = runTest {
    // Arrange
    val medication = TestUtils.createTestMedication()

    // Act
    val id = medicationDao.insertMedication(medication)
    val retrieved = medicationDao.getMedicationById(id)

    // Assert
    assertEquals(medication.name, retrieved?.name)
}
```

### Testing Flow

Use `.first()` to get the first emission:

```kotlin
@Test
fun flowBasedQuery_test() = runTest {
    // Arrange
    medicationDao.insertMedication(testMedication)

    // Act
    val medications = medicationDao.getAllMedications().first()

    // Assert
    assertEquals(1, medications.size)
}
```

### Mocking with MockK

```kotlin
@Test
fun testWithMock() = runTest {
    // Create a mock
    val mockDao = mockk<MedicationDao>()

    // Define behavior
    coEvery { mockDao.getMedicationById(1L) } returns testMedication

    // Use the mock
    val result = mockDao.getMedicationById(1L)

    // Verify
    assertEquals(testMedication, result)
    coVerify { mockDao.getMedicationById(1L) }
}
```

---

## Test Utilities

We provide helper utilities to reduce boilerplate in tests.

### TestUtils.kt

Located at `app/src/test/java/com/medreminder/app/TestUtils.kt`

**Creating test data**:
```kotlin
// Create a medication with default values
val medication = TestUtils.createTestMedication()

// Create with custom values
val customMed = TestUtils.createTestMedication(
    name = "Custom Med",
    dosage = "2 tablets",
    profileId = 5L
)

// Create profiles
val profile = TestUtils.createTestProfile(name = "John Doe")

// Create medication history
val history = TestUtils.createTestHistory(
    medicationName = "Aspirin",
    action = TestConstants.ACTION_TAKEN
)
```

**Time utilities**:
```kotlin
// Create a calendar for today at specific time
val cal = TestUtils.createCalendarToday(hour = 8, minute = 30)

// Add time to timestamps
val tomorrow = TestUtils.addDays(System.currentTimeMillis(), 1)
val inTwoHours = TestUtils.addHours(System.currentTimeMillis(), 2)

// Format timestamps for debugging
val formatted = TestUtils.formatTimestamp(System.currentTimeMillis())
// Output: "2025-11-02 14:30:00"
```

**Reminder time JSON**:
```kotlin
val reminderTimes = listOf(
    TestUtils.createReminderTime(hour = 8, minute = 0),
    TestUtils.createReminderTime(hour = 14, minute = 0),
    TestUtils.createReminderTime(hour = 20, minute = 0)
)
val json = TestUtils.reminderTimesToJson(reminderTimes)
```

### TestConstants.kt

Located at `app/src/test/java/com/medreminder/app/TestConstants.kt`

**Common constants**:
```kotlin
// IDs
TestConstants.TEST_PROFILE_ID
TestConstants.TEST_MEDICATION_ID

// Names
TestConstants.MEDICATION_ASPIRIN
TestConstants.MEDICATION_INSULIN

// Time constants
TestConstants.MORNING_HOUR  // 8
TestConstants.EVENING_HOUR  // 18
TestConstants.ONE_DAY_MS
TestConstants.ONE_WEEK_MS

// Days of week
TestConstants.MONDAY
TestConstants.WEEKDAYS
TestConstants.ALL_DAYS

// Actions
TestConstants.ACTION_TAKEN
TestConstants.ACTION_SKIPPED
TestConstants.ACTION_MISSED

// Sample JSON
TestConstants.SAMPLE_THREE_TIMES_DAILY_JSON
```

---

## Best Practices

### 1. Test One Thing at a Time
```kotlin
// ✅ Good - Tests one behavior
@Test
fun insertMedication_returnsGeneratedId()

@Test
fun insertMedication_savesCorrectData()

// ❌ Bad - Tests multiple behaviors
@Test
fun insertMedication_doesEverything() {
    // Asserts ID generation, data saving, and retrieval
}
```

### 2. Use Descriptive Assertions
```kotlin
// ✅ Good - Clear failure message
assertEquals("Medication name should match", "Aspirin", medication.name)

// ❌ Bad - Generic failure message
assertEquals("Aspirin", medication.name)
```

### 3. Test Edge Cases
```kotlin
@Test
fun getMedicationById_nonExistentId_returnsNull()

@Test
fun calculateMissedDoses_emptyReminderTimes_returnsEmpty()

@Test
fun parseReminderJson_invalidJson_handlesGracefully()
```

### 4. Keep Tests Fast
- Use in-memory databases for DAO tests
- Mock external dependencies
- Avoid Thread.sleep() - use coroutine test helpers instead

### 5. Make Tests Independent
```kotlin
@Before
fun setup() {
    // Each test gets fresh database
    database = Room.inMemoryDatabaseBuilder(context, MedicationDatabase::class.java).build()
}

@After
fun tearDown() {
    database.close()
}
```

### 6. Use Truth for Readable Assertions (Optional)
```kotlin
// With Truth library
import com.google.common.truth.Truth.assertThat

@Test
fun exampleWithTruth() {
    val medications = listOf(med1, med2, med3)

    assertThat(medications).hasSize(3)
    assertThat(medications).contains(med1)
    assertThat(med1.name).isEqualTo("Aspirin")
}
```

---

## Examples

### Example 1: Testing Pure Logic (Unit Test)

```kotlin
// app/src/test/java/com/medreminder/app/utils/TimeUtilsTest.kt
class TimeUtilsTest {

    @Test
    fun isSameDay_sameDayDifferentTime_returnsTrue() {
        // Arrange
        val morning = TestUtils.createCalendarToday(8, 0).timeInMillis
        val evening = TestUtils.createCalendarToday(18, 0).timeInMillis

        // Act
        val result = TimeUtils.isSameDay(morning, evening)

        // Assert
        assertTrue("Times on same day should return true", result)
    }
}
```

### Example 2: Testing Database (Instrumented Test)

```kotlin
// app/src/androidTest/java/com/medreminder/app/data/MedicationDaoTest.kt
@RunWith(AndroidJUnit4::class)
class MedicationDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: MedicationDatabase
    private lateinit var medicationDao: MedicationDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MedicationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        medicationDao = database.medicationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieve_worksCorrectly() = runTest {
        // Arrange
        val medication = TestUtils.createTestMedication()

        // Act
        val id = medicationDao.insertMedication(medication)
        val retrieved = medicationDao.getMedicationById(id)

        // Assert
        assertEquals(medication.name, retrieved?.name)
    }
}
```

### Example 3: Testing with Mocks

```kotlin
// app/src/test/java/com/medreminder/app/ui/MedicationViewModelTest.kt
class MedicationViewModelTest {

    @Test
    fun loadMedications_updatesState() = runTest {
        // Arrange
        val mockDao = mockk<MedicationDao>()
        val testMedications = listOf(TestUtils.createTestMedication())

        coEvery { mockDao.getAllMedications() } returns flowOf(testMedications)

        val viewModel = MedicationViewModel(mockDao)

        // Act
        viewModel.loadMedications()

        // Assert
        coVerify { mockDao.getAllMedications() }
        // Additional state assertions...
    }
}
```

### Example 4: Testing Notification Logic

```kotlin
// app/src/test/java/com/medreminder/app/notifications/PendingMedicationTrackerTest.kt
class PendingMedicationTrackerTest {

    @Test
    fun findMissedDoses_acrossWeekGap_backfillsCorrectly() {
        // Arrange
        val lastCheckTime = TestUtils.addDays(System.currentTimeMillis(), -7)
        val now = System.currentTimeMillis()

        val reminderTimes = listOf(
            TestUtils.createReminderTime(hour = 8, minute = 0, daysOfWeek = TestConstants.ALL_DAYS)
        )
        val medication = TestUtils.createTestMedication(
            reminderTimesJson = TestUtils.reminderTimesToJson(reminderTimes)
        )

        // Act
        val missedDoses = PendingMedicationTracker.findMissedDoses(
            medication, lastCheckTime, now
        )

        // Assert
        assertEquals("Should have 7 missed doses", 7, missedDoses.size)
    }
}
```

---

## Coverage Goals

Aim for the following test coverage:

- **Business Logic (Utilities, Calculators)**: 80-90%
- **DAOs and Database Operations**: 70-80%
- **ViewModels**: 60-70%
- **UI Components**: 40-50% (focus on critical user flows)

Run coverage reports:
```bash
./gradlew testDebugUnitTestCoverage
```

---

## Troubleshooting

### Tests fail with "Method ... not mocked"
**Solution**: Add this to your test class or use Robolectric:
```kotlin
@RunWith(AndroidJUnit4::class) // For instrumented tests
```

### Flow tests never complete
**Solution**: Use `runTest` and `.first()`:
```kotlin
@Test
fun flowTest() = runTest {
    val result = someFlow.first()
}
```

### Database tests fail
**Solution**: Ensure you're using `@RunWith(AndroidJUnit4::class)` and the test is in `androidTest/` directory.

---

## Next Steps

1. **Start Small**: Add tests for `TimeUtils.kt` or expand `PendingMedicationTrackerTest.kt`
2. **Test Bug Fixes**: Write a failing test before fixing a bug
3. **Expand Coverage**: Gradually add tests for DAOs, ViewModels, and UI components
4. **Run Tests Regularly**: Integrate into your development workflow

---

## Resources

- [Android Testing Docs](https://developer.android.com/training/testing)
- [Kotlin Coroutines Testing](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
- [MockK Documentation](https://mockk.io/)
- [Truth Assertions](https://truth.dev/)
- [JUnit 4 Documentation](https://junit.org/junit4/)
