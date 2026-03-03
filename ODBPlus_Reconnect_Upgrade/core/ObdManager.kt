
package com.odbplus.core

import kotlinx.coroutines.*
import com.odbplus.transport.Transport
import com.odbplus.protocol.STN1110Protocol
import com.odbplus.scheduler.PidScheduler

class ObdManager(
    private val scope: CoroutineScope,
    private val transport: Transport
) {

    private val protocol = STN1110Protocol(transport)
    private lateinit var scheduler: PidScheduler
    private lateinit var reconnectManager: ReconnectManager

    private var savedPids: Set<String> = emptySet()

    var state: ConnectionState = ConnectionState.Disconnected
        private set

    init {
        scheduler = PidScheduler(scope) { pid ->
            protocol.send("01$pid")
        }

        reconnectManager = ReconnectManager(
            scope = scope,
            connectBlock = { connectInternal() },
            restoreBlock = { restoreState() }
        )
    }

    suspend fun connect() {
        state = ConnectionState.Connecting
        if (connectInternal()) {
            state = ConnectionState.Connected
            scheduler.start()
        } else {
            state = ConnectionState.Reconnecting
            reconnectManager.scheduleReconnect()
        }
    }

    private suspend fun connectInternal(): Boolean {
        return runCatching {
            if (!transport.connect()) return false
            protocol.initialize()
            true
        }.getOrDefault(false)
    }

    suspend fun disconnect() {
        scheduler.stop()
        transport.disconnect()
        state = ConnectionState.Disconnected
    }

    private suspend fun restoreState() {
        scheduler.restore(savedPids)
        scheduler.start()
        state = ConnectionState.Connected
    }

    fun addPid(pid: String) {
        scheduler.addPid(pid)
        savedPids = scheduler.snapshot()
    }

    fun removePid(pid: String) {
        scheduler.removePid(pid)
        savedPids = scheduler.snapshot()
    }

    fun onConnectionLost() {
        scheduler.stop()
        state = ConnectionState.Reconnecting
        reconnectManager.scheduleReconnect()
    }
}
