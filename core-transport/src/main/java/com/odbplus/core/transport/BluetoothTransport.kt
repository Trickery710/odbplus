package com.odbplus.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
    override fun drainChannel() {
        while (inboundChan.tryReceive().isSuccess) { /* Do nothing, just consume */ }
    }
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private var readerJob: Job? = null
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        if (_isConnected.value) return@withContext
        val adapter = bluetoothAdapter ?: throw IOException("Bluetooth not supported")
        if (!adapter.isEnabled) throw IOException("Bluetooth is not enabled")

        val device: BluetoothDevice = adapter.getRemoteDevice(host)
        val s = device.createRfcommSocketToServiceRecord(sppUuid)
        s.connect()

        socket = s
        input = BufferedInputStream(s.inputStream)
        output = BufferedOutputStream(s.outputStream)
        _isConnected.value = true

        readerJob = externalScope.launch { readerLoop() }
    }

    // --- DEFINITIVE FIX: Reverted to a simple, correct line-based parser ---
    private suspend fun CoroutineScope.readerLoop() {
        val sb = StringBuilder()
        while (isActive) {
            val b: Int = try { input?.read() ?: -1 } catch (_: Throwable) { -1 }
            if (b < 0) {
                // If the stream ends, break the loop
                if (socket?.isConnected != true) break
                // If it's just temporarily empty, delay and continue
                delay(10)
                continue
            }

            val ch = b.toChar()

            // The '>' prompt is a terminator for a command response.
            if (ch == '>') {
                // Send any text that came before the prompt as its own line
                if (sb.isNotEmpty()) {
                    val line = sb.toString().replace("\r", "").trim()
                    if (line.isNotEmpty()) inboundChan.trySend(line)
                    sb.clear()
                }
                // Send the prompt itself as a signal
                inboundChan.trySend(">")
            } else {
                // Otherwise, append the character to the buffer
                sb.append(ch)
                // If we hit a newline, send the buffered line
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

    // This logic is correct. It consumes from the channel until the ">" signal.
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
            // This is an expected outcome if the device doesn't respond.
        }
        return@withContext sb.toString().trim()
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        _isConnected.value = false
        readerJob?.cancelAndJoin()
        // Close resources safely
        try { socket?.close() } catch (_: IOException) {}
        try { input?.close() } catch (_: IOException) {}
        try { output?.close() } catch (_: IOException) {}
        input = null; output = null; socket = null
    }
}
