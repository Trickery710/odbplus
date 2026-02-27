package com.odbplus.core.transport

import kotlinx.coroutines.CancellationException
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

        // ATZ is intentionally NOT sent here.
        //
        // AdapterFingerprinter.fingerprint() performs its own ATZ + settle delay
        // as the very first step of UOAPL initialisation.  Sending a second ATZ
        // here (immediately before fingerprinting) causes two full resets in
        // rapid succession, which confuses cheap ELM clone adapters: many enter
        // a state where they emit the banner repeatedly and stop responding to
        // subsequent commands until they are power-cycled.
        //
        // ATE0 + ATL0 are still sent so the fingerprinter receives clean, echo-
        // free output right from its first command.
        sendAndAwait(ElmCommands.ECHO_OFF)
        sendAndAwait(ElmCommands.LINEFEEDS_OFF)
        addLog("Session pre-init complete.")
    }

    override suspend fun sendAndAwait(cmd: String, timeoutMs: Long) {
        val transport = activeTransport ?: run {
            addLog("!! Error: Not connected.")
            return
        }
        addLog(">> $cmd")

        try {
            // Use the atomic sendCommand (drain → write → read under commandMutex)
            // rather than calling writeLine + readUntilPrompt separately.
            //
            // Calling them separately bypasses commandMutex, making it possible
            // for a concurrent caller (e.g. a keepalive job or a second coroutine
            // that also uses sendAndAwait) to inject its write between our write
            // and our read, causing both sides to receive each other's responses.
            val response = transport.sendCommand(cmd, timeoutMs)
            if (response.isNotBlank()) {
                response.lines().forEach { line ->
                    if (line.isNotBlank()) addLog("<< $line")
                }
            }
        } catch (e: CancellationException) {
            throw e
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
