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
    val lastWifiPort: Int = 35000,
    val professionalLevel: ProfessionalLevel = ProfessionalLevel.BEGINNER,
    val ownedToolIds: Set<String> = emptySet()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val connectionProfileRepository: ConnectionProfileRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(settingsRepository.defaultPollIntervalMs, settingsRepository.autoAcquireVin) { interval, autoVin ->
            interval to autoVin
        },
        combine(connectionProfileRepository.lastBtMac, connectionProfileRepository.lastBtName) { mac, name ->
            mac to name
        },
        combine(connectionProfileRepository.lastWifiHost, connectionProfileRepository.lastWifiPort) { host, port ->
            host to port
        },
        combine(settingsRepository.professionalLevel, settingsRepository.ownedToolIds) { level, tools ->
            level to tools
        }
    ) { (interval, autoVin), (btMac, btName), (wifiHost, wifiPort), (level, tools) ->
        SettingsUiState(
            defaultPollIntervalMs = interval,
            autoAcquireVin        = autoVin,
            lastBtMac             = btMac,
            lastBtName            = btName,
            lastWifiHost          = wifiHost,
            lastWifiPort          = wifiPort,
            professionalLevel     = level,
            ownedToolIds          = tools
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

    fun setProfessionalLevel(level: ProfessionalLevel) {
        viewModelScope.launch { settingsRepository.setProfessionalLevel(level) }
    }

    fun toggleOwnedTool(toolId: String) {
        viewModelScope.launch {
            val current = uiState.value.ownedToolIds.toMutableSet()
            if (toolId in current) current.remove(toolId) else current.add(toolId)
            settingsRepository.setOwnedToolIds(current)
        }
    }
}
