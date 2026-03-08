package com.odbplus.app.session

import com.odbplus.app.data.db.dao.VehicleSessionDao
import com.odbplus.app.data.db.entity.VehicleSessionEntity
import com.odbplus.core.transport.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleSessionManager @Inject constructor(
    private val sessionDao: VehicleSessionDao,
    @AppScope private val scope: CoroutineScope
) {
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    suspend fun startSession(vin: String): String {
        val id = UUID.randomUUID().toString()
        sessionDao.insert(VehicleSessionEntity(id, vin, System.currentTimeMillis()))
        _activeSessionId.value = id
        return id
    }

    fun endSession() {
        val id = _activeSessionId.value ?: return
        scope.launch {
            sessionDao.closeSession(id, System.currentTimeMillis())
            _activeSessionId.value = null
        }
    }
}
