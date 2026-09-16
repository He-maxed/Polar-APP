package com.example.data

import com.example.model.EcgSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Clinical ECG Repository providing atomic, buffered local persistence
 * for sessions, continuous data points, and rhythm detection logs.
 */
class EcgRepository(
    private val sessionDao: SessionDao,
    private val ecgDataPointDao: EcgDataPointDao,
    private val detectionLogDao: DetectionLogDao
) {
    val allSessions: Flow<List<RecordingSessionEntity>> = sessionDao.getAllSessions()

    suspend fun saveSession(session: RecordingSessionEntity) = withContext(Dispatchers.IO) {
        sessionDao.insertSession(session)
    }

    suspend fun updateSession(session: RecordingSessionEntity) = withContext(Dispatchers.IO) {
        sessionDao.updateSession(session)
    }

    suspend fun getSessionById(sessionId: String): RecordingSessionEntity? = withContext(Dispatchers.IO) {
        sessionDao.getSessionById(sessionId)
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        sessionDao.deleteSessionById(sessionId)
        ecgDataPointDao.deleteDataPointsForSession(sessionId)
        detectionLogDao.deleteLogsForSession(sessionId)
    }

    /**
     * Persists a chunk of incoming ECG samples to Room in a single batch transaction.
     */
    suspend fun insertDataPointsBatch(
        sessionId: String,
        samples: List<EcgSample>,
        startSeqIndex: Long
    ) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext
        val entities = samples.mapIndexed { index, sample ->
            EcgDataPointEntity(
                sessionId = sessionId,
                sequenceIndex = startSeqIndex + index,
                timestampMs = sample.timestampMs,
                rawMicrovolts = sample.microvolts,
                filteredMv = sample.filteredMv
            )
        }
        ecgDataPointDao.insertBatch(entities)
    }

    suspend fun getDataPointsForSession(sessionId: String): List<EcgDataPointEntity> = withContext(Dispatchers.IO) {
        ecgDataPointDao.getDataPointsList(sessionId)
    }

    suspend fun getDataPointsRange(sessionId: String, fromMs: Long, toMs: Long): List<EcgDataPointEntity> = withContext(Dispatchers.IO) {
        ecgDataPointDao.getDataPointsRange(sessionId, fromMs, toMs)
    }

    suspend fun getSessionTimeBounds(sessionId: String): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        val min = ecgDataPointDao.getMinTimestamp(sessionId) ?: return@withContext null
        val max = ecgDataPointDao.getMaxTimestamp(sessionId) ?: return@withContext null
        Pair(min, max)
    }

    suspend fun getDataPointsCount(sessionId: String): Long = withContext(Dispatchers.IO) {
        ecgDataPointDao.getDataPointCount(sessionId)
    }

    suspend fun logDetectionEvent(log: DetectionLogEntity) = withContext(Dispatchers.IO) {
        detectionLogDao.insert(log)
    }

    suspend fun logDetectionEvents(logs: List<DetectionLogEntity>) = withContext(Dispatchers.IO) {
        if (logs.isNotEmpty()) {
            detectionLogDao.insertBatch(logs)
        }
    }

    fun getDetectionLogsFlow(sessionId: String): Flow<List<DetectionLogEntity>> {
        return detectionLogDao.getLogsForSession(sessionId)
    }

    suspend fun getDetectionLogsList(sessionId: String): List<DetectionLogEntity> = withContext(Dispatchers.IO) {
        detectionLogDao.getLogsList(sessionId)
    }

    suspend fun getDetectionLogsRange(sessionId: String, fromMs: Long, toMs: Long): List<DetectionLogEntity> = withContext(Dispatchers.IO) {
        detectionLogDao.getLogsRange(sessionId, fromMs, toMs)
    }
}
