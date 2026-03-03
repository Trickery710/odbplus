
package com.odbplus.scheduler

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class PidScheduler(
    private val scope: CoroutineScope,
    private val pollBlock: suspend (String) -> Unit
) {

    private val activePids = mutableSetOf<String>()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job = scope.launch {
            while (isActive) {
                for (pid in activePids) {
                    pollBlock(pid)
                    delay(75) // Conservative ECU-friendly pacing
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun addPid(pid: String) {
        activePids.add(pid)
    }

    fun removePid(pid: String) {
        activePids.remove(pid)
    }

    fun snapshot(): Set<String> = activePids.toSet()

    fun restore(pids: Set<String>) {
        activePids.clear()
        activePids.addAll(pids)
    }
}
