package com.odbplus.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice // Corrected import
import android.bluetooth.BluetoothManager // Corrected import
import android.content.Context
import com.odbplus.core.transport.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.*
import javax.inject.Inject

@SuppressLint("MissingPermission")
class BluetoothTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val externalScope: CoroutineScope
) : ObdTransport {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var socket: android.bluetooth.BluetoothSocket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    private val inboundChan = Channel<String>(Channel.UNLIMITED)
    override val inbound = inboundChan.receiveAsFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private var readerJob: Job? = null
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        if (_isConnected.value) return@withContext
        val adapter = bluetoothAdapter ?: throw IOException("Bluetooth not supported")
        if (!adapter.isEnabled) throw IOException("Bluetooth is not enabled")

        // --- FIX: Use the correct platform BluetoothDevice class ---
        val device: BluetoothDevice = adapter.getRemoteDevice(host)
        val s = device.createRfcommSocketToServiceRecord(sppUuid)
        s.connect()

        socket = s
        // --- FIX: Use the correct inputStream from the socket ---
        input = BufferedInputStream(s.inputStream)
        output = BufferedOutputStream(s.outputStream)
        _isConnected.value = true

        readerJob = externalScope.launch { readerLoop() }
    }

    // This reader logic is correct
    private suspend fun CoroutineScope.readerLoop() {
        val sb = StringBuilder()
        while (isActive && _isConnected.value) {
            val b: Int? = try { input?.read() } catch (_: Throwable) { -1 }
            if (b == null || b < 0) {
                if (isActive) delay(10)
                continue
            }

            val ch = b.toChar()

            if (ch == '>') {
                val pendingLine = sb.toString().replace("\r", "").trim()
                if (pendingLine.isNotEmpty()) inboundChan.trySend(pendingLine)
                inboundChan.trySend(">")
                sb.clear()
            } else {
                sb.append(ch)
                if (ch == '\n') {
                    val line = sb.toString().replace("\r", "").trim()
                    if (line.isNotEmpty()) inboundChan.trySend(line)
                    sb.clear()
                }
            }
        }
    }

    override suspend fun writeLine(line: String) = withContext(Dispatchers.IO) {
        val out = output ?: error("Not connected")
        out.write((line + "\r").toByteArray())
        out.flush()
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        try {
            withTimeout(timeoutMs) {
                while (true) {
                    val next = inboundChan.receive()
                    if (next == ">") break
                    sb.appendLine(next)
                }
            }
        } catch (e: TimeoutCancellationException) {
            // Expected if the device doesn't respond in time.
        }
        return@withContext sb.toString().trim()
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        _isConnected.value = false
        readerJob?.cancelAndJoin()
        input?.close(); output?.close(); socket?.close()
        input = null; output = null; socket = null
    }
}
