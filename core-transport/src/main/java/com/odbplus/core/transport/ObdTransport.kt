package com.odbplus.core.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ObdTransport {
    suspend fun connect()
    suspend fun close()
    suspend fun writeLine(line: String)             // appends \r
    suspend fun readUntilPrompt(timeoutMs: Long = 1500): String
    val inbound: Flow<String>
    val isConnected: StateFlow<Boolean>
}

