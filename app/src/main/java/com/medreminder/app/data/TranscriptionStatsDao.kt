package com.medreminder.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for transcription statistics.
 */
@Dao
interface TranscriptionStatsDao {

    @Insert
    suspend fun insertStats(stats: TranscriptionStats): Long

    @Update
    suspend fun updateStats(stats: TranscriptionStats)

    @Query("SELECT * FROM transcription_stats ORDER BY startTime DESC")
    fun getAllStats(): Flow<List<TranscriptionStats>>

    @Query("SELECT * FROM transcription_stats WHERE status = :status ORDER BY startTime DESC")
    fun getStatsByStatus(status: String): Flow<List<TranscriptionStats>>

    @Query("SELECT * FROM transcription_stats WHERE medicationId = :medicationId ORDER BY startTime DESC")
    fun getStatsForMedication(medicationId: Long): Flow<List<TranscriptionStats>>

    @Query("SELECT * FROM transcription_stats WHERE medicationId = :medicationId AND audioFilePath = :audioPath LIMIT 1")
    suspend fun getStatsByMedicationIdAndPath(medicationId: Long, audioPath: String): TranscriptionStats?

    // Summary statistics
    @Query("SELECT COUNT(*) FROM transcription_stats WHERE status = 'success'")
    suspend fun getSuccessCount(): Int

    @Query("SELECT COUNT(*) FROM transcription_stats WHERE status = 'failed'")
    suspend fun getFailureCount(): Int

    @Query("SELECT COUNT(*) FROM transcription_stats WHERE status = 'pending'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM transcription_stats")
    suspend fun getTotalCount(): Int

    @Query("""
        SELECT * FROM transcription_stats
        WHERE status = 'success'
        ORDER BY transcriptionLength DESC
        LIMIT 1
    """)
    suspend fun getLongestTranscription(): TranscriptionStats?

    @Query("""
        SELECT * FROM transcription_stats
        WHERE status = 'success'
        ORDER BY durationMs DESC
        LIMIT 1
    """)
    suspend fun getSlowestTranscription(): TranscriptionStats?

    @Query("""
        SELECT * FROM transcription_stats
        WHERE status = 'success' AND durationMs IS NOT NULL
        ORDER BY durationMs ASC
        LIMIT 1
    """)
    suspend fun getFastestTranscription(): TranscriptionStats?

    @Query("""
        SELECT AVG(durationMs) FROM transcription_stats
        WHERE status = 'success' AND durationMs IS NOT NULL
    """)
    suspend fun getAverageDuration(): Float?

    @Query("""
        SELECT AVG(transcriptionLength) FROM transcription_stats
        WHERE status = 'success' AND transcriptionLength IS NOT NULL
    """)
    suspend fun getAverageTranscriptionLength(): Float?

    @Query("""
        SELECT AVG(processingSpeedRatio) FROM transcription_stats
        WHERE status = 'success' AND processingSpeedRatio IS NOT NULL
    """)
    suspend fun getAverageSpeedRatio(): Float?

    @Query("DELETE FROM transcription_stats")
    suspend fun deleteAllStats()

    @Query("DELETE FROM transcription_stats WHERE medicationId = :medicationId")
    suspend fun deleteStatsForMedication(medicationId: Long)
}
