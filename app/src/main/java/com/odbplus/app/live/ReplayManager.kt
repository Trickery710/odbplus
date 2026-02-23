package com.odbplus.app.live

import com.odbplus.core.protocol.ObdPid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ReplayManager {

    private val _isReplaying = MutableStateFlow(false)
    val isReplaying: StateFlow<Boolean> = _isReplaying.asStateFlow()

    private val _replaySession = MutableStateFlow<LogSession?>(null)
    val replaySession: StateFlow<LogSession?> = _replaySession.asStateFlow()

    private val _replayIndex = MutableStateFlow(0)
    val replayIndex: StateFlow<Int> = _replayIndex.asStateFlow()

    private val _replaySpeed = MutableStateFlow(1.0f)
    val replaySpeed: StateFlow<Float> = _replaySpeed.asStateFlow()

    /** Invoked for each data point during replay with the pid→value snapshot. */
    var onFrame: ((Map<ObdPid, Double?>) -> Unit)? = null

    private var replayJob: Job? = null

    fun setSpeed(multiplier: Float) {
        _replaySpeed.value = multiplier.coerceIn(0.25f, 4.0f)
    }

    fun start(session: LogSession, scope: CoroutineScope) {
        if (session.dataPoints.isEmpty()) return
        replayJob?.cancel()
        _isReplaying.value = true
        _replaySession.value = session
        _replayIndex.value = 0

        replayJob = scope.launch {
            val dataPoints = session.dataPoints
            var index = 0
            while (isActive && _isReplaying.value && index < dataPoints.size) {
                val dataPoint = dataPoints[index]
                _replayIndex.update { index }
                onFrame?.invoke(dataPoint.pidValues)

                if (index + 1 < dataPoints.size) {
                    val delayMs = ((dataPoints[index + 1].timestamp - dataPoint.timestamp) /
                            _replaySpeed.value).toLong()
                    delay(delayMs.coerceAtLeast(50L))
                }
                index++
            }
            if (_isReplaying.value) stop()
        }
    }

    fun stop() {
        replayJob?.cancel()
        replayJob = null
        _isReplaying.value = false
        _replaySession.value = null
        _replayIndex.value = 0
    }
}
