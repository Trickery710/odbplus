package com.odbplus.app.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.data.db.dao.SensorLogDao
import com.odbplus.app.data.db.dao.VehicleSessionDao
import com.odbplus.app.data.db.entity.SensorLogEntity
import com.odbplus.app.data.db.entity.VehicleSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDetailUiState(
    val session: VehicleSessionEntity? = null,
    val logsByPid: Map<String, List<SensorLogEntity>> = emptyMap(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val sensorLogDao: SensorLogDao,
    private val sessionDao: VehicleSessionDao,
    savedState: SavedStateHandle
) : ViewModel() {

    val sessionId: String = savedState.get<String>("sessionId") ?: ""

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessionDao.getBySessionId(sessionId)
            val logs = sensorLogDao.getLogsForSession(sessionId, limit = 2000)
            _uiState.value = SessionDetailUiState(
                session = session,
                logsByPid = logs.groupBy { it.pid },
                isLoading = false
            )
        }
    }
}
