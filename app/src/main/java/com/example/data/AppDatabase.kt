package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

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
    val rawSamplesCsvPath: String? = null
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM recording_sessions ORDER BY startTimestampMs DESC")
    fun getAllSessions(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE sessionId = :id LIMIT 1")
    suspend fun getSessionById(id: String): RecordingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSessionEntity)

    @Delete
    suspend fun deleteSession(session: RecordingSessionEntity)

    @Query("DELETE FROM recording_sessions WHERE sessionId = :id")
    suspend fun deleteSessionById(id: String)
}

@Database(entities = [RecordingSessionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "polar_ecg_clinical.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
