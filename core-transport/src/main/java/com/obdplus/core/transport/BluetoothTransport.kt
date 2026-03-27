package com.obdplus.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.obdplus.core.transport.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.UUID
import javax.inject.Inject

@SuppressLint("MissingPermission")
class BluetoothTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope externalScope: CoroutineScope
) : BaseTransport(externalScope) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var socket: BluetoothSocket? = null

    override suspend fun createConnection(host: String, port: Int): ConnectionStreams =
        withContext(Dispatchers.IO) {
            val adapter = bluetoothAdapter
                ?: throw BluetoothUnavailableException("Bluetooth not supported on this device")
            if (!adapter.isEnabled) {
                throw BluetoothUnavailableException("Bluetooth is not enabled")
            }

            val device = try {
                adapter.getRemoteDevice(host)
            } catch (e: SecurityException) {
                throw PermissionDeniedException("BLUETOOTH_CONNECT permission denied", e)
            }
            val s = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
            } catch (e: SecurityException) {
                throw PermissionDeniedException("BLUETOOTH_CONNECT permission denied", e)
            }

            socket = s
            ConnectionStreams(
                input = BufferedInputStream(s.inputStream),
                output = BufferedOutputStream(s.outputStream)
            )
        }

    override fun isConnectionActive(): Boolean = socket?.isConnected == true

    override suspend fun closeConnection() {
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
