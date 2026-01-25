package com.odbplus.app.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.core.transport.ConnectionState
import com.odbplus.core.transport.TransportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val repo: TransportRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repo.connectionState
    val logLines: StateFlow<List<String>> = repo.logLines

    // --- FIX: Renamed the old 'connect' to be specific to TCP ---
    fun connectTcp(host: String, port: Int) {
        viewModelScope.launch {
            repo.connect(host, port, isBluetooth = false)
        }
    }

    // --- FIX: Added a new function specifically for Bluetooth ---
    fun connectBluetooth(macAddress: String) {
        viewModelScope.launch {
            repo.connect(macAddress, 0, isBluetooth = true)
        }
    }

    // --- FIX: Added the missing function for sending custom commands ---
    fun sendCustomCommand(cmd: String) {
        viewModelScope.launch {
            repo.sendAndAwait(cmd)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repo.disconnect()
        }
    }

    fun clearLogs() {
        repo.clearLogs()
    }

    override fun onCleared() {
        if (repo.connectionState.value == ConnectionState.CONNECTED) {
            disconnect()
        }
    }
}
