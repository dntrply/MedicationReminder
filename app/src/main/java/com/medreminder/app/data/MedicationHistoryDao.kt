package com.medreminder.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: MedicationHistory): Long

    @Query("SELECT * FROM medication_history ORDER BY takenTime DESC")
    fun getAllHistory(): Flow<List<MedicationHistory>>

    @Query("SELECT * FROM medication_history ORDER BY takenTime DESC")
    fun getAllHistorySync(): List<MedicationHistory>

    @Query("SELECT * FROM medication_history WHERE medicationId = :medicationId ORDER BY takenTime DESC")
    fun getHistoryForMedication(medicationId: Long): Flow<List<MedicationHistory>>

    @Query("""
        SELECT * FROM medication_history
        WHERE takenTime >= :startOfDay AND takenTime < :endOfDay
        ORDER BY takenTime DESC
    """)
    fun getHistoryForDay(startOfDay: Long, endOfDay: Long): Flow<List<MedicationHistory>>

    @Query("""
        SELECT * FROM medication_history
        WHERE medicationId = :medicationId
        AND takenTime >= :startOfDay AND takenTime < :endOfDay
    """)
    suspend fun getHistoryForMedicationToday(medicationId: Long, startOfDay: Long, endOfDay: Long): List<MedicationHistory>

    @Query("""
        SELECT COUNT(*) FROM medication_history
        WHERE takenTime >= :startOfDay AND takenTime < :endOfDay
    """)
    suspend fun getTakenCountForDay(startOfDay: Long, endOfDay: Long): Int

    @Query("DELETE FROM medication_history WHERE medicationId = :medicationId")
    suspend fun deleteHistoryForMedication(medicationId: Long)
}
