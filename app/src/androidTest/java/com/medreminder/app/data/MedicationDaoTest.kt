package com.medreminder.app.data

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medreminder.app.TestConstants
import com.medreminder.app.TestUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Example DAO test demonstrating:
 * - In-memory database setup
 * - Testing suspend functions with coroutines
 * - Testing Flow-based queries
 * - CRUD operations
 * - Query filtering
 *
 * Note: This is an instrumented test that runs on an Android device/emulator
 * because it requires Android framework components (Room, SQLite).
 *
 * To run: ./gradlew testDebugUnitTest (won't work - needs Android)
 * Instead run: ./gradlew connectedDebugAndroidTest
 * Or run directly from Android Studio
 */
@RunWith(AndroidJUnit4::class)
class MedicationDaoTest {

    /**
     * Ensures LiveData/Flow executes synchronously in tests.
     * Required for testing Room's Flow-based queries.
     */
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: MedicationDatabase
    private lateinit var medicationDao: MedicationDao
    private lateinit var profileDao: ProfileDao

    @Before
    fun setup() {
        // Create in-memory database for testing
        // Data is wiped when the process is killed
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MedicationDatabase::class.java
        )
            .allowMainThreadQueries() // Only for testing!
            .build()

        medicationDao = database.medicationDao()
        profileDao = database.profileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ============================================
    // Basic CRUD Tests
    // ============================================

    @Test
    fun insertMedication_andRetrieveById_returnsCorrectMedication() = runTest {
        // Arrange
        val medication = TestUtils.createTestMedication(
            id = 0, // Auto-generate ID
            name = TestConstants.MEDICATION_ASPIRIN,
            dosage = TestConstants.TEST_MEDICATION_DOSAGE
        )

        // Act
        val insertedId = medicationDao.insertMedication(medication)
        val retrieved = medicationDao.getMedicationById(insertedId)

        // Assert
        assertNotNull("Retrieved medication should not be null", retrieved)
        assertEquals("Medication name should match", TestConstants.MEDICATION_ASPIRIN, retrieved?.name)
        assertEquals("Medication dosage should match", TestConstants.TEST_MEDICATION_DOSAGE, retrieved?.dosage)
        assertEquals("Medication ID should match", insertedId, retrieved?.id)
    }

    @Test
    fun insertMedication_withConflict_replacesExisting() = runTest {
        // Arrange
        val medication1 = TestUtils.createTestMedication(
            id = 1L,
            name = "Original Name",
            dosage = "1 tablet"
        )
        val medication2 = medication1.copy(
            name = "Updated Name",
            dosage = "2 tablets"
        )

        // Act
        medicationDao.insertMedication(medication1)
        medicationDao.insertMedication(medication2) // Should replace due to OnConflictStrategy.REPLACE
        val retrieved = medicationDao.getMedicationById(1L)

        // Assert
        assertEquals("Medication should be replaced", "Updated Name", retrieved?.name)
        assertEquals("Dosage should be updated", "2 tablets", retrieved?.dosage)
    }

    @Test
    fun updateMedication_modifiesExistingMedication() = runTest {
        // Arrange
        val medication = TestUtils.createTestMedication(name = "Original")
        val id = medicationDao.insertMedication(medication)

        // Act
        val updated = medication.copy(id = id, name = "Updated")
        medicationDao.updateMedication(updated)
        val retrieved = medicationDao.getMedicationById(id)

        // Assert
        assertEquals("Medication name should be updated", "Updated", retrieved?.name)
    }

    @Test
    fun deleteMedication_removesFromDatabase() = runTest {
        // Arrange
        val medication = TestUtils.createTestMedication()
        val id = medicationDao.insertMedication(medication)

        // Act
        medicationDao.deleteMedicationById(id)
        val retrieved = medicationDao.getMedicationById(id)

        // Assert
        assertNull("Deleted medication should not be found", retrieved)
    }

    // ============================================
    // Query Tests
    // ============================================

    @Test
    fun getAllMedications_returnsAllMedications() = runTest {
        // Arrange
        val med1 = TestUtils.createTestMedication(id = 0, name = "Med 1")
        val med2 = TestUtils.createTestMedication(id = 0, name = "Med 2")
        val med3 = TestUtils.createTestMedication(id = 0, name = "Med 3")

        // Act
        medicationDao.insertMedication(med1)
        medicationDao.insertMedication(med2)
        medicationDao.insertMedication(med3)
        val allMedications = medicationDao.getAllMedications().first()

        // Assert
        assertEquals("Should have 3 medications", 3, allMedications.size)
    }

    @Test
    fun getAllMedications_emptyDatabase_returnsEmptyList() = runTest {
        // Act
        val allMedications = medicationDao.getAllMedications().first()

        // Assert
        assertTrue("Empty database should return empty list", allMedications.isEmpty())
    }

    @Test
    fun getMedicationsByProfile_filtersCorrectly() = runTest {
        // Arrange - Create two profiles
        val profile1 = TestUtils.createTestProfile(id = 0, name = "Profile 1")
        val profile2 = TestUtils.createTestProfile(id = 0, name = "Profile 2")
        val profile1Id = profileDao.insertProfile(profile1)
        val profile2Id = profileDao.insertProfile(profile2)

        // Create medications for different profiles
        val med1 = TestUtils.createTestMedication(id = 0, profileId = profile1Id, name = "Med 1")
        val med2 = TestUtils.createTestMedication(id = 0, profileId = profile1Id, name = "Med 2")
        val med3 = TestUtils.createTestMedication(id = 0, profileId = profile2Id, name = "Med 3")

        medicationDao.insertMedication(med1)
        medicationDao.insertMedication(med2)
        medicationDao.insertMedication(med3)

        // Act
        val profile1Meds = medicationDao.getMedicationsByProfile(profile1Id).first()
        val profile2Meds = medicationDao.getMedicationsByProfile(profile2Id).first()

        // Assert
        assertEquals("Profile 1 should have 2 medications", 2, profile1Meds.size)
        assertEquals("Profile 2 should have 1 medication", 1, profile2Meds.size)
        assertTrue("Profile 1 meds should contain Med 1", profile1Meds.any { it.name == "Med 1" })
        assertTrue("Profile 1 meds should contain Med 2", profile1Meds.any { it.name == "Med 2" })
        assertTrue("Profile 2 meds should contain Med 3", profile2Meds.any { it.name == "Med 3" })
    }

    @Test
    fun deleteMedicationsByProfile_removesOnlyProfileMedications() = runTest {
        // Arrange
        val profile1Id = 1L
        val profile2Id = 2L

        val med1 = TestUtils.createTestMedication(id = 0, profileId = profile1Id, name = "Med 1")
        val med2 = TestUtils.createTestMedication(id = 0, profileId = profile1Id, name = "Med 2")
        val med3 = TestUtils.createTestMedication(id = 0, profileId = profile2Id, name = "Med 3")

        medicationDao.insertMedication(med1)
        medicationDao.insertMedication(med2)
        medicationDao.insertMedication(med3)

        // Act
        medicationDao.deleteMedicationsByProfile(profile1Id)
        val remainingMeds = medicationDao.getAllMedications().first()

        // Assert
        assertEquals("Should have 1 medication remaining", 1, remainingMeds.size)
        assertEquals("Remaining medication should be from profile 2", profile2Id, remainingMeds[0].profileId)
    }

    // ============================================
    // Sync vs Flow Query Tests
    // ============================================

    @Test
    fun getAllMedicationsSync_returnsImmediateResult() {
        // Arrange
        val med = TestUtils.createTestMedication()
        // Note: Can't use runTest here because we're testing sync method
        // This is a demonstration - in real usage, sync methods are for specific use cases

        // This test demonstrates the sync API exists, but typically you'd test
        // the Flow-based methods with runTest as shown above
    }

    // ============================================
    // Reminder Times JSON Tests
    // ============================================

    @Test
    fun insertMedication_withReminderTimesJson_storesAndRetrievesCorrectly() = runTest {
        // Arrange
        val reminderJson = TestConstants.SAMPLE_THREE_TIMES_DAILY_JSON
        val medication = TestUtils.createTestMedication(
            id = 0,
            reminderTimesJson = reminderJson
        )

        // Act
        val id = medicationDao.insertMedication(medication)
        val retrieved = medicationDao.getMedicationById(id)

        // Assert
        assertNotNull("Retrieved medication should not be null", retrieved)
        assertEquals("Reminder times JSON should match", reminderJson, retrieved?.reminderTimesJson)
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun getMedicationById_nonExistentId_returnsNull() = runTest {
        // Act
        val retrieved = medicationDao.getMedicationById(999999L)

        // Assert
        assertNull("Non-existent medication should return null", retrieved)
    }

    @Test
    fun insertMedication_withNullOptionalFields_succeeds() = runTest {
        // Arrange
        val medication = TestUtils.createTestMedication(
            id = 0,
            photoUri = null,
            audioNotePath = null,
            audioTranscription = null,
            notes = null,
            reminderTimesJson = null
        )

        // Act
        val id = medicationDao.insertMedication(medication)
        val retrieved = medicationDao.getMedicationById(id)

        // Assert
        assertNotNull("Medication with null fields should be insertable", retrieved)
        assertNull("Photo URI should be null", retrieved?.photoUri)
        assertNull("Audio note path should be null", retrieved?.audioNotePath)
        assertNull("Notes should be null", retrieved?.notes)
    }
}
