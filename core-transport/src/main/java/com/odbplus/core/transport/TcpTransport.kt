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
                // A non-zero SO_TIMEOUT makes read() throw SocketTimeoutException after
                // SOCKET_READ_TIMEOUT_MS instead of blocking forever.  The reader loop
                // treats that as "no data yet", keeps the coroutine cooperative, and
                // prevents the app from freezing when the adapter goes silent.
                soTimeout = TransportConstants.SOCKET_READ_TIMEOUT_MS
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
