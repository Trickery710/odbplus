package com.odbplus.core.transport

import com.odbplus.core.transport.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TcpTransport @Inject constructor(
    @AppScope externalScope: CoroutineScope
) : BaseTransport(externalScope) {

    private var socket: Socket? = null

    override suspend fun createConnection(host: String, port: Int): ConnectionStreams =
        withContext(Dispatchers.IO) {
            val s = Socket().apply {
                tcpNoDelay = true
                keepAlive = true
                soTimeout = 0
                connect(InetSocketAddress(host, port), TransportConstants.CONNECTION_TIMEOUT_MS)
            }
            socket = s
            ConnectionStreams(
                input = BufferedInputStream(s.getInputStream()),
                output = BufferedOutputStream(s.getOutputStream())
            )
        }

    override fun isConnectionActive(): Boolean = socket?.isConnected == true

    override suspend fun closeConnection() {
        runCatching { socket?.close() }
        socket = null
    }
}
