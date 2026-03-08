package com.odbplus.app.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.data.db.dao.VehicleDao
import com.odbplus.app.data.db.dao.VehicleSessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SessionDisplay(
    val sessionId: String,
    val vin: String,
    val displayName: String,
    val timestampStart: Long,
    val timestampEnd: Long?
) {
    val durationMs: Long get() = (timestampEnd ?: System.currentTimeMillis()) - timestampStart
    val isOngoing: Boolean get() = timestampEnd == null
    val durationStr: String get() {
        if (isOngoing) return "Ongoing"
        val min = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val sec = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        return if (min > 0) "${min}m ${sec}s" else "${sec}s"
    }
}

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val sessionDao: VehicleSessionDao,
    private val vehicleDao: VehicleDao
) : ViewModel() {

    val sessions: StateFlow<List<SessionDisplay>> = combine(
        sessionDao.getAllSessionsFlow(),
        vehicleDao.getAllFlow()
    ) { sessions, vehicles ->
        val nameByVin = vehicles.associate { v ->
            v.vin to (v.ecuName?.trim()?.takeIf { it.isNotBlank() } ?: v.vin)
        }
        sessions.map { s ->
            SessionDisplay(
                sessionId = s.sessionId,
                vin = s.vin,
                displayName = nameByVin[s.vin] ?: s.vin,
                timestampStart = s.timestampStart,
                timestampEnd = s.timestampEnd
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSession(sessionId: String) {
        viewModelScope.launch { sessionDao.deleteBySessionId(sessionId) }
    }

    fun clearAllSessions() {
        viewModelScope.launch { sessionDao.deleteAll() }
    }
}
