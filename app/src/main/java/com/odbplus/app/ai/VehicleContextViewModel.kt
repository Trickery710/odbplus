package com.odbplus.app.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.data.VehicleContext
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.app.live.LogSessionRepository
import com.odbplus.core.protocol.DiagnosticTroubleCode
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.transport.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Owns all vehicle-data collection: connection awareness, VIN/vehicle info fetching,
 * live PID values and log sessions.  Writes the aggregated [VehicleContext] to
 * [VehicleContextProvider] so that other components (e.g. [AiChatViewModel]) can
 * read it without direct ViewModel-to-ViewModel injection.
 */
@HiltViewModel
class VehicleContextViewModel @Inject constructor(
    private val obdService: ObdService,
    private val vehicleInfoRepository: VehicleInfoRepository,
    private val logSessionRepository: LogSessionRepository,
    private val provider: VehicleContextProvider
) : ViewModel() {

    /** Exposes the shared provider's state directly for any UI that observes it. */
    val context: StateFlow<VehicleContext> = provider.context

    private var ctx: VehicleContext = VehicleContext()
        set(value) { field = value; provider.update(value) }

    private var wasConnected = false

    init {
        viewModelScope.launch { vehicleInfoRepository.initialize() }

        viewModelScope.launch {
            obdService.connectionState.collect { state ->
                val connected = state == ConnectionState.CONNECTED
                ctx = ctx.copy(isConnected = connected)

                if (connected && !wasConnected) fetchVehicleInfo()
                else if (!connected && wasConnected) {
                    vehicleInfoRepository.clearCurrentVehicle()
                    ctx = ctx.copy(vehicleInfo = null)
                }
                wasConnected = connected
            }
        }

        viewModelScope.launch {
            vehicleInfoRepository.currentVehicle.collect { info ->
                ctx = ctx.copy(vehicleInfo = info)
            }
        }

        viewModelScope.launch {
            logSessionRepository.currentPidValues.collect { pidValues ->
                ctx = ctx.copy(livePidValues = pidValues)
            }
        }

        viewModelScope.launch {
            logSessionRepository.sessions.collect { sessions ->
                ctx = ctx.copy(recentSessions = sessions)
            }
        }
    }

    fun updateDtcs(
        storedDtcs: List<DiagnosticTroubleCode>,
        pendingDtcs: List<DiagnosticTroubleCode>
    ) {
        ctx = ctx.copy(storedDtcs = storedDtcs, pendingDtcs = pendingDtcs)
    }

    private fun fetchVehicleInfo() {
        viewModelScope.launch {
            ctx = ctx.copy(isFetchingVehicleInfo = true)

            try {
                val vin = obdService.readVin()
                if (vin.isNullOrBlank()) {
                    Timber.w("Could not read VIN from vehicle")
                    return@launch
                }

                Timber.d("Read VIN: $vin")
                val isFirstTime = vehicleInfoRepository.isFirstTimeVehicle(vin)
                var vehicleInfo = VehicleInfo(vin = vin)

                if (isFirstTime) {
                    Timber.d("First time vehicle, reading additional info...")
                    vehicleInfo = vehicleInfo.copy(
                        calibrationId = obdService.readCalibrationId(),
                        calibrationVerificationNumber = obdService.readCalibrationVerificationNumber(),
                        ecuName = obdService.readEcuName()
                    )
                } else {
                    val existing = vehicleInfoRepository.getVehicle(vin)
                    if (existing != null) {
                        vehicleInfo = existing.copy(lastSeenTimestamp = System.currentTimeMillis())
                    }
                }

                vehicleInfoRepository.saveVehicle(vehicleInfo)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching vehicle info")
            } finally {
                ctx = ctx.copy(isFetchingVehicleInfo = false)
            }
        }
    }
}
