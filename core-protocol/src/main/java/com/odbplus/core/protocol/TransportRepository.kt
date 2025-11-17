package com.odbplus.core.protocol

import com.odbplus.core.transport.ObdTransport
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransportRepository @Inject constructor(
    private val transport: ObdTransport
) {
    val isConnected: StateFlow<Boolean> = transport.isConnected
    suspend fun connect() = transport.connect()
    suspend fun disconnect() = transport.close()
    suspend fun initElmSession() {
        sendAndAwait("ATZ", 2000)
        sendAndAwait("ATE0", 1000)
        sendAndAwait("ATL0", 1000)
        sendAndAwait("ATS0", 1000)
    }
    suspend fun sendAndAwait(cmd: String, timeoutMs: Long = 1500): String {
        transport.writeLine(cmd)
        return transport.readUntilPrompt(timeoutMs)
    }
}
