package com.odbplus.core.transport

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Base implementation of [ObdTransport] that handles common I/O operations.
 * Subclasses only need to implement [createConnection] to provide the specific
 * socket/stream creation logic for their transport type (TCP, Bluetooth, etc.).
 */
abstract class BaseTransport(
    private val externalScope: CoroutineScope
) : ObdTransport {

    protected var input: BufferedInputStream? = null
    protected var output: BufferedOutputStream? = null

    /**
     * Serialises write+read pairs so that a keepalive ping cannot inject
     * its "OK>" response into a concurrently-reading command handler.
     */
    private val commandMutex = Mutex()

    private val inboundChan = Channel<String>(Channel.UNLIMITED)
    override val inbound = inboundChan.receiveAsFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private var readerJob: Job? = null

    override fun drainChannel() {
        while (inboundChan.tryReceive().isSuccess) { /* consume */ }
    }

    /**
     * Creates the underlying connection and returns the input/output streams.
     * Implementations should establish the connection (TCP socket, Bluetooth RFCOMM, etc.)
     * and return the streams for reading and writing.
     */
    protected abstract suspend fun createConnection(host: String, port: Int): ConnectionStreams

    /**
     * Performs any cleanup specific to the transport implementation.
     * Called during [close] after streams are closed.
     */
    protected abstract suspend fun closeConnection()

    override suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        if (_isConnected.value) return@withContext

        val streams = createConnection(host, port)
        input = streams.input
        output = streams.output
        _isConnected.value = true

        // Blocking I/O must run on Dispatchers.IO, not Dispatchers.Default.
        // externalScope uses Dispatchers.Default (see CoroutineModule); without
        // an explicit IO dispatcher here, the blocking read() would starve the
        // CPU-bound thread pool and could cause apparent UI freezes.
        readerJob = externalScope.launch(Dispatchers.IO) { readerLoop() }
    }

    private suspend fun CoroutineScope.readerLoop() {
        val sb = StringBuilder()
        while (isActive) {
            val b: Int = try {
                input?.read() ?: -1
            } catch (_: SocketTimeoutException) {
                // TCP SO_TIMEOUT fired — no data within SOCKET_READ_TIMEOUT_MS.
                // This is normal; loop back so we can check isActive and keep the
                // coroutine cooperative (and thus cancellable) without blocking forever.
                continue
            } catch (_: Throwable) {
                // IOException (stream closed by close()), Bluetooth errors, etc.
                -1
            }

            if (b < 0) {
                // Blocking read returning -1 means EOF — remote closed the connection.
                // isConnected stays true in Java even after remote closure, so we
                // cannot rely on isConnectionActive() here. Mark disconnected and exit.
                _isConnected.value = false
                break
            }

            val ch = b.toChar()

            // The '>' prompt terminates a command response.
            if (ch == '>') {
                if (sb.isNotEmpty()) {
                    val line = sb.toString().replace("\r", "").trim()
                    if (line.isNotEmpty()) inboundChan.trySend(line)
                    sb.clear()
                }
                inboundChan.trySend(">")
            } else {
                sb.append(ch)
                // Treat BOTH '\r' and '\n' as line-end markers.
                //
                // ELM327 with ATL0 (linefeeds off) uses '\r' as the only row
                // separator — multi-line responses arrive as:
                //   "LINE1\rLINE2\r>"
                // Without handling '\r' here each response block would be
                // buffered until '>' and then sent as one concatenated string
                // (replace("\r","") erases the separator), which corrupts every
                // multi-line response: 0100 support bitmaps, Mode-03 DTC lists,
                // Mode-09 VIN frames, and multi-ECU replies.
                //
                // When ATL1 is on the adapter sends '\r\n' pairs; '\r' flushes
                // the line and the immediately following '\n' fires on an already-
                // cleared buffer (line.isNotEmpty() == false) so nothing is
                // double-emitted.
                if (ch == '\n' || ch == '\r') {
                    val line = sb.toString().replace("\r", "").trim()
                    if (line.isNotEmpty()) inboundChan.trySend(line)
                    sb.clear()
                }
            }
        }
    }

    /**
     * Checks if the underlying connection is still active.
     * Used by the reader loop to determine when to stop.
     */
    protected abstract fun isConnectionActive(): Boolean

    override suspend fun writeLine(line: String) = withContext(Dispatchers.IO) {
        val out = output ?: throw NotConnectedException()
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
        } catch (_: TimeoutCancellationException) {
            // Expected outcome if the device doesn't respond
        }
        sb.toString().trim()
    }

    /**
     * Atomically drain → write → read under [commandMutex].
     *
     * Use this instead of separate [writeLine] + [readUntilPrompt] calls to
     * prevent a concurrent keepalive ping from injecting its "OK>" response
     * into the middle of another command's response window.
     */
    override suspend fun sendCommand(line: String, timeoutMs: Long): String =
        commandMutex.withLock {
            drainChannel()
            writeLine(line)
            readUntilPrompt(timeoutMs)
        }

    override suspend fun close() = withContext(Dispatchers.IO) {
        _isConnected.value = false

        // Close streams BEFORE cancelAndJoin.
        //
        // The reader loop calls the blocking input.read().  Kotlin coroutine
        // cancellation only works at suspension points; a blocked native read()
        // cannot be interrupted by cancel().  Closing the stream first forces
        // read() to throw IOException immediately, which the loop catches and
        // converts to -1, allowing the loop to exit cleanly.
        //
        // If we called cancelAndJoin() first with soTimeout=0 (or Bluetooth
        // where soTimeout is not supported), we would deadlock here forever.
        try { input?.close() } catch (_: IOException) {}
        try { output?.close() } catch (_: IOException) {}
        input = null
        output = null

        readerJob?.cancelAndJoin()
        closeConnection()
    }

    /**
     * Container for the input/output streams returned by [createConnection].
     */
    data class ConnectionStreams(
        val input: BufferedInputStream,
        val output: BufferedOutputStream
    )
}
