package com.odbplus.core.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Defines the contract for a low-level OBD transport layer (e.g., TCP, Bluetooth).
 * This interface is abstract and does not know about Hilt or any specific implementation.
 */
interface ObdTransport {
    /** A flow that emits every raw line of data received from the transport. */
    val inbound: Flow<String>

    /** A flow that emits the current connection status (true if connected). */
    val isConnected: StateFlow<Boolean>

    /** Initiates a connection to the configured endpoint. */
    suspend fun connect(host: String, port: Int)

    /** Closes the active connection and releases resources. */
    suspend fun close()

    /** Writes a single line of text to the transport, appending the required carriage return. */
    suspend fun writeLine(line: String)

    /**
     * Reads from the inbound flow until a prompt character ('>') is received or a timeout occurs.
     * Returns the aggregated response.
     */
    suspend fun readUntilPrompt(timeoutMs: Long = 1500): String
}
