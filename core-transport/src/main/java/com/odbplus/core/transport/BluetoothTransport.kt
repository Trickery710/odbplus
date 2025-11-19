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

/**
 * An ObdTransport implementation for Bluetooth Classic (RFCOMM) connections.
 * NOTE: This requires BLUETOOTH_CONNECT and BLUETOOTH_SCAN permissions.
 */
@SuppressLint("MissingPermission") // Permissions are handled at the UI layer before calling.
class BluetoothTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val externalScope: CoroutineScope
) : ObdTransport {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var socket: android.bluetooth.BluetoothSocket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    private val inboundChan = Channel<String>(Channel.BUFFERED)
    override val inbound = inboundChan.receiveAsFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private var readerJob: Job? = null

    // Standard SPP UUID for OBD-II adapters
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        if (_isConnected.value) return@withContext
        val adapter = bluetoothAdapter ?: throw IOException("Bluetooth not supported on this device.")
        if (!adapter.isEnabled) throw IOException("Bluetooth is not enabled.")

        val device: BluetoothDevice = adapter.getRemoteDevice(host) // Host is the MAC address
        val s = device.createRfcommSocketToServiceRecord(sppUuid)
        s.connect() // This is a blocking call

        socket = s
        input = BufferedInputStream(s.inputStream)
        output = BufferedOutputStream(s.outputStream)
        _isConnected.value = true

        readerJob = externalScope.launch { readerLoop() }
    }

    // FIX: Make this an extension function of CoroutineScope
    private suspend fun CoroutineScope.readerLoop() {
        val sb = StringBuilder()
        while (isActive && _isConnected.value) {
            val b = try { input?.read() } catch (_: Throwable) { -1 }
            if (b == null || b < 0) { delay(10); continue }
            val ch = b.toInt().toChar()
            sb.append(ch)
            if (ch == '\n') {
                val line = sb.toString().replace("\r", "").trim()
                if (line.isNotEmpty()) inboundChan.trySend(line)
                sb.clear()
            } else if (ch == '>') {
                inboundChan.trySend(">")
            }
        }
    }

    override suspend fun writeLine(line: String) = withContext(Dispatchers.IO) {
        val out = output ?: error("Not connected")
        out.write((line + "\r").toByteArray())
        out.flush()
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val sb = StringBuilder()
        while (System.currentTimeMillis() < deadline) {
            while (!inboundChan.isEmpty) {
                val next = inboundChan.receive()
                if (next == ">") return@withContext sb.toString()
                sb.appendLine(next)
            }
            delay(10)
        }
        sb.toString()
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        _isConnected.value = false
        readerJob?.cancelAndJoin()
        input?.close(); output?.close(); socket?.close()
        input = null; output = null; socket = null
    }
}
