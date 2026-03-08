package com.odbplus.app.session

import com.odbplus.app.data.db.dao.SensorLogDao
import com.odbplus.app.data.db.entity.SensorLogEntity
import com.odbplus.core.transport.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorLoggingService @Inject constructor(
    private val sensorLogDao: SensorLogDao,
    @AppScope private val scope: CoroutineScope
) {
    private val buffer = mutableListOf<SensorLogEntity>()
    private var sessionId: String? = null
    private var flushJob: Job? = null

    fun startLogging(sid: String) {
        sessionId = sid
        flushJob = scope.launch {
            while (isActive) {
                delay(5_000)
                flush()
            }
        }
    }

    fun record(pid: String, value: Double) {
        val sid = sessionId ?: return
        synchronized(buffer) {
            buffer.add(
                SensorLogEntity(
                    sessionId = sid,
                    pid = pid,
                    value = value,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun stopLogging() {
        flushJob?.cancel()
        sessionId = null
        scope.launch { flush() }
    }

    private suspend fun flush() {
        val batch = synchronized(buffer) { buffer.toList().also { buffer.clear() } }
        if (batch.isNotEmpty()) sensorLogDao.insertAll(batch)
    }
}
