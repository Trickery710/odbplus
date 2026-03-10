package com.odbplus.app.connect

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.VehicleInfoRepository
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.app.data.ConnectionProfileRepository
import com.odbplus.app.vin.coordinator.VinDecodeCoordinator
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.protocol.PidDiscoveryState
import com.odbplus.core.protocol.adapter.ProtocolSessionState
import com.odbplus.core.protocol.diagnostic.DiagnosticLogger
import com.odbplus.core.transport.ConnectionState
import com.odbplus.core.transport.TransportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    application: Application,
    private val repo: TransportRepository,
    private val obdService: ObdService,
    private val vehicleInfoRepository: VehicleInfoRepository,
    private val connectionProfileRepository: ConnectionProfileRepository,
    private val vinDecodeCoordinator: VinDecodeCoordinator
) : AndroidViewModel(application) {

    val connectionState: StateFlow<ConnectionState> = repo.connectionState
    val logLines: StateFlow<List<String>> = repo.logLines
    val sessionState: StateFlow<ProtocolSessionState> = obdService.sessionState
    val discoveryState: StateFlow<PidDiscoveryState> = obdService.discoveryState
    val currentVehicle: StateFlow<VehicleInfo?> = vehicleInfoRepository.currentVehicle

    val lastBtMac:  StateFlow<String?> = connectionProfileRepository.lastBtMac
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val lastBtName: StateFlow<String?> = connectionProfileRepository.lastBtName
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val lastWifiHost: StateFlow<String?> = connectionProfileRepository.lastWifiHost
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val lastWifiPort: StateFlow<Int> = connectionProfileRepository.lastWifiPort
        .stateIn(viewModelScope, SharingStarted.Eagerly, 35000)

    private val _isAcquiring = MutableStateFlow(false)
    val isAcquiring: StateFlow<Boolean> = _isAcquiring.asStateFlow()

    private val _acquireStatus = MutableStateFlow<String?>(null)
    val acquireStatus: StateFlow<String?> = _acquireStatus.asStateFlow()

    /** Connect via TCP/IP (ELM327 Wi-Fi adapters). */
    fun connectTcp(host: String, port: Int) {
        viewModelScope.launch {
            repo.connect(host, port, isBluetooth = false)
            if (repo.connectionState.value == ConnectionState.CONNECTED) {
                obdService.onTransportReady("tcp:$host:$port")
                obdService.runPidDiscovery()
                connectionProfileRepository.saveWifiProfile(host, port)
            }
        }
    }

    /** Connect via Bluetooth SPP (ELM327 BT adapters). */
    @SuppressLint("MissingPermission")
    fun connectBluetooth(macAddress: String) {
        viewModelScope.launch {
            repo.connect(macAddress, 0, isBluetooth = true)
            if (repo.connectionState.value == ConnectionState.CONNECTED) {
                obdService.onTransportReady("bt:$macAddress")
                obdService.runPidDiscovery()
                // Look up device name from system paired devices
                val btManager = getApplication<Application>()
                    .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val deviceName = btManager?.adapter?.bondedDevices
                    ?.firstOrNull { it.address == macAddress }?.name ?: macAddress
                connectionProfileRepository.saveBluetoothProfile(macAddress, deviceName)
            }
        }
    }

    /**
     * Read VIN from the ECU. If new, also fetches calibration ID, CVN, and ECU name.
     * Saves the result to [VehicleInfoRepository] and starts a session + DTC monitoring.
     */
    fun acquireVehicleInfo() {
        viewModelScope.launch {
            _isAcquiring.value = true
            _acquireStatus.value = null
            try {
                val vin = obdService.readVin()
                if (!vin.isNullOrBlank()) {
                    val isNew = vehicleInfoRepository.isFirstTimeVehicle(vin)
                    val info = if (isNew) {
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
                    vehicleInfoRepository.saveVehicle(info)
                    _acquireStatus.value = "VIN acquired: $vin"

                    // Fire VIN decode as a background sidecar — non-blocking
                    vinDecodeCoordinator.onVinDiscovered(vin)
                } else {
                    _acquireStatus.value = "VIN not available from vehicle"
                }
            } catch (e: Exception) {
                _acquireStatus.value = "Error: ${e.message}"
            } finally {
                _isAcquiring.value = false
            }
        }
    }

    /** Save a manually entered VIN to the vehicle database. */
    fun saveManualVin(vin: String) {
        viewModelScope.launch {
            val trimmed = vin.trim().uppercase()
            val existing = vehicleInfoRepository.getVehicle(trimmed)
            val info = existing?.copy(lastSeenTimestamp = System.currentTimeMillis())
                ?: VehicleInfo(vin = trimmed)
            vehicleInfoRepository.saveVehicle(info)
            _acquireStatus.value = "VIN saved: $trimmed"
        }
    }

    /** Send a raw AT/OBD command and discard the reply (for legacy terminal use). */
    fun sendCustomCommand(cmd: String) {
        viewModelScope.launch {
            repo.sendAndAwait(cmd)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            obdService.onTransportDisconnected()
            repo.disconnect()
            vinDecodeCoordinator.reset()
        }
    }

    fun clearLogs() {
        repo.clearLogs()
    }

    /**
     * Writes a combined diagnostic log to the app's cache dir and returns a shareable Uri.
     */
    fun exportDiagnosticLog(): Uri? = try {
        val app = getApplication<Application>()
        val diagText  = DiagnosticLogger.exportText()
        val transport = repo.logLines.value
        val combined  = buildString {
            append(diagText)
            appendLine()
            appendLine("=".repeat(64))
            appendLine("Transport Log (${transport.size} lines)")
            appendLine("=".repeat(64))
            transport.forEach { appendLine(it) }
        }
        val file = File(app.cacheDir, "odbplus_diagnostic.txt")
        file.writeText(combined)
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    } catch (_: Exception) {
        null
    }

    override fun onCleared() {
        if (repo.connectionState.value == ConnectionState.CONNECTED) {
            disconnect()
        }
    }
}
