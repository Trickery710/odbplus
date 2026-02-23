package com.odbplus.core.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

interface TransportRepository {
    val connectionState: StateFlow<ConnectionState>
    val logLines: StateFlow<List<String>>
    suspend fun connect(address: String, port: Int, isBluetooth: Boolean)
    suspend fun sendAndAwait(cmd: String, timeoutMs: Long = TransportConstants.DEFAULT_RESPONSE_TIMEOUT_MS)
    suspend fun disconnect()
    fun clearLogs()

    /**
     * Returns the currently active [ObdTransport], or null if not connected.
     * Used by higher-level protocol layers (e.g. AdapterSession) that need
     * direct access to the raw transport for fingerprinting and command routing.
     */
    fun getActiveTransport(): ObdTransport?
}

@Singleton
class TransportRepositoryImpl @Inject constructor(
    @Named("tcp") private val tcpTransport: ObdTransport,
    @Named("bt") private val bluetoothTransport: ObdTransport
) : TransportRepository {

    private var activeTransport: ObdTransport? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    override val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    private fun addLog(line: String) {
        _logLines.update { (it + line).takeLast(TransportConstants.MAX_LOG_LINES) }
    }

    override fun clearLogs() {
        _logLines.value = emptyList()
    }

    override suspend fun connect(address: String, port: Int, isBluetooth: Boolean) {
        if (_connectionState.value != ConnectionState.DISCONNECTED) return

        activeTransport = if (isBluetooth) bluetoothTransport else tcpTransport
        val transport = activeTransport ?: return

        _connectionState.value = ConnectionState.CONNECTING
        addLog("Connecting to $address...")
        try {
            transport.connect(address, port)
            _connectionState.value = ConnectionState.CONNECTED
            addLog("Connection successful.")
            initElmSession()
        } catch (e: BluetoothUnavailableException) {
            _connectionState.value = ConnectionState.ERROR
            addLog("!! Bluetooth Error: ${e.message}")
            activeTransport = null
        } catch (e: TransportException) {
            _connectionState.value = ConnectionState.ERROR
            addLog("!! Connection Failed: ${e.message}")
            activeTransport = null
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            addLog("!! Unexpected Error: ${e.message}")
            activeTransport = null
        }
    }

    override suspend fun disconnect() {
        activeTransport?.let {
            addLog("Disconnecting...")
            it.close()
            _connectionState.value = ConnectionState.DISCONNECTED
            addLog("Disconnected.")
        }
        activeTransport = null
    }

    override fun getActiveTransport(): ObdTransport? = activeTransport

    private suspend fun initElmSession() {
        addLog("Initializing ELM327 session...")
        sendAndAwait(ElmCommands.RESET, timeoutMs = TransportConstants.INIT_TIMEOUT_MS)
        sendAndAwait(ElmCommands.ECHO_OFF)
        sendAndAwait(ElmCommands.LINEFEEDS_OFF)
        addLog("Session initialized.")
    }

    override suspend fun sendAndAwait(cmd: String, timeoutMs: Long) {
        val transport = activeTransport ?: run {
            addLog("!! Error: Not connected.")
            return
        }
        transport.drainChannel()
        addLog(">> $cmd")

        try {
            transport.writeLine(cmd)
            val response = transport.readUntilPrompt(timeoutMs)

            if (response.isNotBlank()) {
                response.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        addLog("<< $line")
                    }
                }
            }
        } catch (e: NotConnectedException) {
            addLog("!! Error: Connection lost.")
            _connectionState.value = ConnectionState.ERROR
        } catch (e: TransportException) {
            addLog("!! Transport Error: ${e.message}")
        } catch (e: Exception) {
            addLog("!! Error during send/receive: ${e.message}")
        }
    }
}
