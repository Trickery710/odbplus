package com.odbplus.core.transport

import com.odbplus.core.transport.di.AppScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
// Inject the CoroutineScope into the constructor
class TcpTransport @Inject constructor(
    @AppScope private val externalScope: CoroutineScope
) : ObdTransport {

    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    private val inboundChan = Channel<String>(Channel.BUFFERED)
    override val inbound = inboundChan.receiveAsFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private var readerJob: Job? = null

    // Update the connect method to accept host and port
    override suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        if (_isConnected.value) return@withContext
        val s = Socket().apply {
            tcpNoDelay = true
            keepAlive = true
            soTimeout = 0
            // Use the host and port parameters here
            connect(InetSocketAddress(host, port), 3000)
        }
        socket = s
        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
        _isConnected.value = true

        readerJob = externalScope.launch { // Now externalScope is available
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
