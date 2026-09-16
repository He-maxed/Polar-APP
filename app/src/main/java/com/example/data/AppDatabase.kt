package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Metadata table storing overall clinical session parameters, duration,
 * calculated metrics (SDNN, extrasystole counts, heart rates), and notes.
 */
@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val title: String,
    val startTimestampMs: Long,
    val durationSeconds: Long,
    val totalBeats: Int,
    val svebCount: Int,
    val vebCount: Int,
    val coupletCount: Int,
    val meanHr: Int,
    val minHr: Int,
    val maxHr: Int,
    val sdnnMs: Float,
    val longestPauseMs: Float,
    val sampleRateHz: Float = 130f,
    val rawSamplesCsvPath: String? = null,
    val notes: String = ""
)

/**
 * High-resolution ECG sample storage ensuring zero data loss during recording.
 * Persists raw sensor microvolts and filtered millivolts with precise microsecond/millisecond timestamps.
 */
@Entity(
    tableName = "ecg_data_points",
    indices = [
        Index(value = ["sessionId", "sequenceIndex"]),
        Index(value = ["sessionId", "timestampMs"])
    ]
)
data class EcgDataPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val sequenceIndex: Long,
    val timestampMs: Long,
    val rawMicrovolts: Float,
    val filteredMv: Float
)

/**
 * Diagnostic and rhythm event detection logs.
 * Stores every categorized beat (Normal, SVEB, VEB, Couplet, Pause, Tachycardia, Bradycardia)
 * alongside R-peak amplitudes, RR intervals, and clinical severity.
 */
@Entity(
    tableName = "detection_logs",
    indices = [
        Index(value = ["sessionId", "timestampMs"]),
        Index(value = ["eventType"])
    ]
)
data class DetectionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val timestampMs: Long,
    val eventType: String,
    val hrBpm: Int,
    val rrIntervalMs: Float,
    val rAmplitudeMv: Float,
    val description: String,
    val severity: String = "INFO" // INFO, WARNING, CRITICAL
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM recording_sessions ORDER BY startTimestampMs DESC")
    fun getAllSessions(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE sessionId = :id LIMIT 1")
    suspend fun getSessionById(id: String): RecordingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSessionEntity)

    @Update
    suspend fun updateSession(session: RecordingSessionEntity)

    @Delete
    suspend fun deleteSession(session: RecordingSessionEntity)

    @Query("DELETE FROM recording_sessions WHERE sessionId = :id")
    suspend fun deleteSessionById(id: String)
}

@Dao
interface EcgDataPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(points: List<EcgDataPointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: EcgDataPointEntity)

    @Query("SELECT * FROM ecg_data_points WHERE sessionId = :sessionId ORDER BY sequenceIndex ASC")
    fun getDataPointsFlow(sessionId: String): Flow<List<EcgDataPointEntity>>

    @Query("SELECT * FROM ecg_data_points WHERE sessionId = :sessionId ORDER BY sequenceIndex ASC")
    suspend fun getDataPointsList(sessionId: String): List<EcgDataPointEntity>

    @Query("SELECT * FROM ecg_data_points WHERE sessionId = :sessionId AND timestampMs BETWEEN :fromMs AND :toMs ORDER BY sequenceIndex ASC")
    suspend fun getDataPointsRange(sessionId: String, fromMs: Long, toMs: Long): List<EcgDataPointEntity>

    @Query("SELECT MIN(timestampMs) FROM ecg_data_points WHERE sessionId = :sessionId")
    suspend fun getMinTimestamp(sessionId: String): Long?

    @Query("SELECT MAX(timestampMs) FROM ecg_data_points WHERE sessionId = :sessionId")
    suspend fun getMaxTimestamp(sessionId: String): Long?

    @Query("SELECT COUNT(*) FROM ecg_data_points WHERE sessionId = :sessionId")
    suspend fun getDataPointCount(sessionId: String): Long

    @Query("DELETE FROM ecg_data_points WHERE sessionId = :sessionId")
    suspend fun deleteDataPointsForSession(sessionId: String)
}

@Dao
interface DetectionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DetectionLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(logs: List<DetectionLogEntity>)

    @Query("SELECT * FROM detection_logs WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getLogsForSession(sessionId: String): Flow<List<DetectionLogEntity>>

    @Query("SELECT * FROM detection_logs WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getLogsList(sessionId: String): List<DetectionLogEntity>

    @Query("SELECT * FROM detection_logs WHERE sessionId = :sessionId AND timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC")
    suspend fun getLogsRange(sessionId: String, fromMs: Long, toMs: Long): List<DetectionLogEntity>

    @Query("SELECT * FROM detection_logs ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<DetectionLogEntity>>

    @Query("DELETE FROM detection_logs WHERE sessionId = :sessionId")
    suspend fun deleteLogsForSession(sessionId: String)
}

@Database(
    entities = [
        RecordingSessionEntity::class,
        EcgDataPointEntity::class,
        DetectionLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun ecgDataPointDao(): EcgDataPointDao
    abstract fun detectionLogDao(): DetectionLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "polar_ecg_clinical.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
