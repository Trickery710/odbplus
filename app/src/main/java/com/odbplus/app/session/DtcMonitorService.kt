package com.odbplus.app.session

import com.odbplus.app.data.db.dao.DtcLogDao
import com.odbplus.app.data.db.dao.FreezeFrameDao
import com.odbplus.app.data.db.entity.DtcLogEntity
import com.odbplus.app.data.db.entity.FreezeFrameEntity
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.transport.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DtcMonitorService @Inject constructor(
    private val obdService: ObdService,
    private val dtcLogDao: DtcLogDao,
    private val freezeFrameDao: FreezeFrameDao,
    @AppScope private val scope: CoroutineScope
) {
    private val knownCodes = mutableSetOf<String>()
    private var monitorJob: Job? = null

    fun startMonitoring(vin: String, sessionId: String) {
        monitorJob = scope.launch {
            while (isActive) {
                delay(30_000)
                try {
                    val dtcs = obdService.readStoredDtcs()
                    val newCodes = dtcs.map { it.code }.toSet() - knownCodes
                    for (code in newCodes) {
                        val dtc = dtcs.first { it.code == code }
                        dtcLogDao.insert(
                            DtcLogEntity(
                                vin = vin,
                                dtcCode = code,
                                description = dtc.description,
                                sessionId = sessionId
                            )
                        )
                        captureFreezeFrame(vin, code)
                    }
                    knownCodes.addAll(newCodes)
                } catch (_: Exception) {
                    // Don't crash monitoring on transient read errors
                }
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        knownCodes.clear()
    }

    private suspend fun captureFreezeFrame(vin: String, dtcCode: String) {
        try {
            val rpm = obdService.getValue(ObdPid.ENGINE_RPM)
            val speed = obdService.getValue(ObdPid.VEHICLE_SPEED)
            val coolant = obdService.getValue(ObdPid.ENGINE_COOLANT_TEMP)
            val fuelTrim = obdService.getValue(ObdPid.SHORT_TERM_FUEL_TRIM_BANK1)
            val throttle = obdService.getValue(ObdPid.THROTTLE_POSITION)
            val load = obdService.getValue(ObdPid.ENGINE_LOAD)
            freezeFrameDao.insert(
                FreezeFrameEntity(
                    vin = vin,
                    dtcCode = dtcCode,
                    rpm = rpm,
                    speed = speed,
                    coolantTemp = coolant,
                    fuelTrimShortBank1 = fuelTrim,
                    throttle = throttle,
                    engineLoad = load
                )
            )
        } catch (_: Exception) {
            // Best-effort capture — don't fail DTC logging on freeze frame error
        }
    }
}
