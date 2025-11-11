package com.odbplus.core.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.cancellation.CancellationException

// --- Connection types the UI toggles between
sealed interface ConnType {
    object TCP : ConnType
    object BluetoothSPP : ConnType
}

// --- Observable connection state
data class ConnectionState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val type: ConnType = ConnType.TCP,
    val host: String = "10.0.2.2",
    val port: Int = 35000,
    val btDeviceName: String? = null
)

// --- Simple append-only log the UI can observe
class CommandLog {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()
    fun append(line: String) {
        val ts = System.currentTimeMillis() % 100000
        _lines.value = _lines.value + "$ts: $line"
    }
}

// --- Main repository used by the Connect screen
class TransportRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    val log = CommandLog()

    // TCP internals
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null

    fun updateTcpEndpoint(host: String, port: Int) {
        _state.value = _state.value.copy(host = host, port = port, type = ConnType.TCP)
    }
    fun selectBluetoothDevice(name: String) {
        _state.value = _state.value.copy(type = ConnType.BluetoothSPP, btDeviceName = name)
    }
    fun setType(type: ConnType) {
        _state.value = _state.value.copy(type = type)
    }

    fun connect() {
        val st = _state.value
        if (st.connecting || st.connected) return
        when (st.type) {
            is ConnType.TCP -> connectTcp(st.host, st.port)
            is ConnType.BluetoothSPP -> log.append("TODO: Bluetooth connect to ${st.btDeviceName ?: "(none)"}")
        }
    }

    private fun connectTcp(host: String, port: Int) {
        _state.value = _state.value.copy(connecting = true)
        log.append("Connecting TCP to $host:$port ...")
        scope.launch {
            try {
                val s = Socket()
                s.soTimeout = 10000
                s.connect(InetSocketAddress(host, port), 5000)
                socket = s
                writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.US_ASCII))
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.US_ASCII))

                readerJob = launch {
                    try {
                        // Use a functional approach with `useLines` for safer resource handling
                        reader.useLines { lines ->
                            lines.forEach { line ->
                                // Check if the coroutine is still active before processing
                                if (!isActive) return@forEach
                                log.append("<< $line")
                            }
                        }
                    } catch (_: CancellationException) {
                        // This is expected when the job is cancelled, so we can ignore it.
                    } catch (e: Exception) {
                        log.append("Reader error: ${e.message}")
                    }
                }


                _state.value = _state.value.copy(connected = true, connecting = false)
                log.append("Connected.")
            } catch (e: Exception) {
                _state.value = _state.value.copy(connected = false, connecting = false)
                log.append("Connect failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        readerJob?.cancel(); readerJob = null
        try { writer?.flush() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null; socket = null

        if (_state.value.connected || _state.value.connecting) {
            _state.value = _state.value.copy(connected = false, connecting = false)
            log.append("Disconnected.")
        }
    }

    fun send(cmd: String) {
        val w = writer ?: run { log.append("Not connected."); return }
        scope.launch {
            try {
                log.append(">> $cmd")
                w.write(cmd); w.write("\r"); w.flush()
            } catch (e: Exception) {
                log.append("Write error: ${e.message}")
            }
        }
    }

    fun close() {
        disconnect()
        scope.cancel()
    }
}
