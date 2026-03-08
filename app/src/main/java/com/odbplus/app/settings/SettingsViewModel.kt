package com.odbplus.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.data.ConnectionProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val defaultPollIntervalMs: Long = 500L,
    val autoAcquireVin: Boolean = true,
    val lastBtMac: String? = null,
    val lastBtName: String? = null,
    val lastWifiHost: String? = null,
    val lastWifiPort: Int = 35000
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val connectionProfileRepository: ConnectionProfileRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(settingsRepository.defaultPollIntervalMs, settingsRepository.autoAcquireVin) { interval, autoVin -> interval to autoVin },
        combine(connectionProfileRepository.lastBtMac, connectionProfileRepository.lastBtName) { mac, name -> mac to name },
        combine(connectionProfileRepository.lastWifiHost, connectionProfileRepository.lastWifiPort) { host, port -> host to port }
    ) { (interval, autoVin), (btMac, btName), (wifiHost, wifiPort) ->
        SettingsUiState(
            defaultPollIntervalMs = interval,
            autoAcquireVin        = autoVin,
            lastBtMac             = btMac,
            lastBtName            = btName,
            lastWifiHost          = wifiHost,
            lastWifiPort          = wifiPort
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setPollInterval(ms: Long) {
        viewModelScope.launch { settingsRepository.setDefaultPollInterval(ms) }
    }

    fun setAutoAcquireVin(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoAcquireVin(enabled) }
    }

    fun clearConnectionProfile() {
        viewModelScope.launch {
            connectionProfileRepository.saveBluetoothProfile("", "")
            connectionProfileRepository.saveWifiProfile("", 35000)
        }
    }
}
