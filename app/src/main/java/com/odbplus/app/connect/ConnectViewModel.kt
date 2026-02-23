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

    /** Connect via TCP/IP (ELM327 Wi-Fi adapters). */
    fun connectTcp(host: String, port: Int) {
        viewModelScope.launch {
            repo.connect(host, port, isBluetooth = false)
        }
    }

    /** Connect via Bluetooth SPP (ELM327 BT adapters). */
    fun connectBluetooth(macAddress: String) {
        viewModelScope.launch {
            repo.connect(macAddress, 0, isBluetooth = true)
        }
    }

    /** Send a raw AT/OBD command and discard the reply (terminal use). */
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
