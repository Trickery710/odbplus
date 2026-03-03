
package com.odbplus.core

import kotlinx.coroutines.*
import kotlin.math.min

class ReconnectManager(
    private val scope: CoroutineScope,
    private val connectBlock: suspend () -> Boolean,
    private val restoreBlock: suspend () -> Unit
) {

    private var attempt = 0
    private var reconnectJob: Job? = null

    fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            attempt = 0
            while (true) {
                attempt++
                val delayMs = min(30_000L, 1_000L * (1 shl attempt))
                delay(delayMs)

                val success = runCatching { connectBlock() }.getOrDefault(false)
                if (success) {
                    restoreBlock()
                    attempt = 0
                    break
                }
            }
        }
    }

    fun cancel() {
        reconnectJob?.cancel()
    }
}
