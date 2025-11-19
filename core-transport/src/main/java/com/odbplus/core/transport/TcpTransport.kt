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
import java.io.IOException
@Singleton
class TcpTransport @Inject constructor(
    @AppScope private val externalScope: CoroutineScope
) : ObdTransport {

    private var socket: Socket? = null
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

    override suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        if (_isConnected.value) return@withContext
        val s = Socket().apply {
            tcpNoDelay = true
            keepAlive = true
            soTimeout = 0 // Use non-blocking reads in the loop
            connect(InetSocketAddress(host, port), 3000)
        }
        socket = s
        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
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
