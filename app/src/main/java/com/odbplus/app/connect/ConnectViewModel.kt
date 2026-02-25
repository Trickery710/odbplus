package com.odbplus.app.connect

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.core.protocol.diagnostic.DiagnosticLogger
import com.odbplus.core.transport.ConnectionState
import com.odbplus.core.transport.TransportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    application: Application,
    private val repo: TransportRepository
) : AndroidViewModel(application) {

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

    /**
     * Writes a combined diagnostic log (transport >>/<< lines + UOAPL events)
     * to the app's cache dir and returns a shareable [Uri] via FileProvider.
     *
     * Returns null if the file could not be written.
     */
    fun exportDiagnosticLog(): Uri? = try {
        val app = getApplication<Application>()

        // Build the export text: UOAPL events first, then transport log
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
