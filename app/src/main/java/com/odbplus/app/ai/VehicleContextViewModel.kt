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
                if (!vin.isNullOrBlank()) {
                    fetchByVin(vin)
                } else {
                    Timber.w("VIN not available — trying Mode 09 fallback")
                    fetchWithoutVin()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error fetching vehicle info")
            } finally {
                ctx = ctx.copy(isFetchingVehicleInfo = false)
            }
        }
    }

    private suspend fun fetchByVin(vin: String) {
        Timber.d("Read VIN: $vin")
        val isFirstTime = vehicleInfoRepository.isFirstTimeVehicle(vin)
        val vehicleInfo = if (isFirstTime) {
            Timber.d("First time vehicle, reading additional info...")
            VehicleInfo(
                vin = vin,
                calibrationId = obdService.readCalibrationId(),
                calibrationVerificationNumber = obdService.readCalibrationVerificationNumber(),
                ecuName = obdService.readEcuName()
            )
        } else {
            vehicleInfoRepository.getVehicle(vin)
                ?.copy(lastSeenTimestamp = System.currentTimeMillis())
                ?: VehicleInfo(vin = vin)
        }
        vehicleInfoRepository.saveVehicle(vehicleInfo)
    }

    /**
     * Fallback when Mode 09 VIN (PID 02) is unavailable.
     * Reads calibration ID, CVN, and ECU name and uses the calibration ID
     * (or ECU name if CalID is also absent) as a stable synthetic key.
     */
    private suspend fun fetchWithoutVin() {
        val calibId = obdService.readCalibrationId()
        val cvn     = obdService.readCalibrationVerificationNumber()
        val ecuName = obdService.readEcuName()

        val syntheticKey = calibId ?: ecuName
        if (syntheticKey.isNullOrBlank()) {
            Timber.w("No vehicle identifiers available — cannot identify vehicle")
            return
        }

        Timber.d("No VIN — identified by: $syntheticKey")
        val vehicleInfo = vehicleInfoRepository.getVehicle(syntheticKey)
            ?.copy(lastSeenTimestamp = System.currentTimeMillis())
            ?: VehicleInfo(
                vin = syntheticKey,
                calibrationId = calibId,
                calibrationVerificationNumber = cvn,
                ecuName = ecuName
            )
        vehicleInfoRepository.saveVehicle(vehicleInfo)
    }
}
