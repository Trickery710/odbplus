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
    suspend fun sendAndAwait(cmd: String, timeoutMs: Long = 3000L)
    suspend fun disconnect()
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
        _logLines.update { (it + line).takeLast(200) } // Increased log buffer
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
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            addLog("!! Connection Failed: ${e.message}")
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

    private suspend fun initElmSession() {
        addLog("Initializing ELM327 session...")
        sendAndAwait("ATZ", timeoutMs = 5000L)
        sendAndAwait("ATE0") // Echo off
        sendAndAwait("ATL0") // Linefeeds off
        addLog("Session initialized.")
    }

    override suspend fun sendAndAwait(cmd: String, timeoutMs: Long) {
        val transport = activeTransport ?: run {
            addLog("!! Error: Not connected.")
            return
        }

        addLog(">> $cmd")

        try {
            transport.writeLine(cmd)
            val response = transport.readUntilPrompt(timeoutMs)

            // Only log if the response is not blank. ATE0 returns nothing, which is correct.
            if (response.isNotBlank()) {
                response.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        addLog("<< $line")
                    }
                }
            }
        } catch (e: Exception) {
            addLog("!! Error during send/receive: ${e.message}")
        }
    }
}
