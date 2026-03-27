package com.obdplus.app.live

import com.obdplus.app.ai.data.VehicleInfo
import com.obdplus.core.protocol.ObdPid
import com.obdplus.core.protocol.PidDiscoveryState

// ─────────────────────────────────────────────────────────────────────────────
// Per-PID display model
// ─────────────────────────────────────────────────────────────────────────────

data class PidDisplayState(
    val pid: ObdPid,
    val definition: PidDefinition? = null,
    val isSelected: Boolean = false,
    val isLoading: Boolean = false,
    val value: Double? = null,
    val formattedValue: String = "--",
    val error: String? = null,
    val status: SensorStatus = SensorStatus.NORMAL,
    val isFavorite: Boolean = false,
    val changeRate: Double = 0.0     // units/s, for "most active" sorting
) {
    val category: PidCategory get() = definition?.category ?: PidCategory.SENSORS
}

// ─────────────────────────────────────────────────────────────────────────────
// Logging models
// ─────────────────────────────────────────────────────────────────────────────

data class LoggedDataPoint(
    val timestamp: Long,
    val pidValues: Map<ObdPid, Double?>
)

data class LogSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val selectedPids: List<ObdPid> = emptyList(),
    val dataPoints: List<LoggedDataPoint> = emptyList(),
    val vehicleInfo: VehicleInfo? = null
) {
    val duration: Long get() = (endTime ?: System.currentTimeMillis()) - startTime
    val dataPointCount: Int get() = dataPoints.size
}

// ─────────────────────────────────────────────────────────────────────────────
// Chart model
// ─────────────────────────────────────────────────────────────────────────────

data class ChartPoint(val timestamp: Long, val value: Double)

// ─────────────────────────────────────────────────────────────────────────────
// Screen-level UI state
// ─────────────────────────────────────────────────────────────────────────────

data class LiveDataUiState(
    val isConnected: Boolean = false,
    val isPolling: Boolean = false,
    val pollIntervalMs: Long = 500L,
    val chartData: Map<ObdPid, List<ChartPoint>> = emptyMap(),
    val showChart: Boolean = false,
    /**
     * PIDs available for selection on the current vehicle.
     * When disconnected: full master list for offline browsing.
     * When connected + discovery complete: only ECU-confirmed supported PIDs.
     */
    val availablePids: List<PidDisplayState> = emptyList(),
    val selectedPids: List<ObdPid> = emptyList(),
    val pidValues: Map<ObdPid, PidDisplayState> = emptyMap(),
    // Logging
    val isLogging: Boolean = false,
    val currentLogSession: LogSession? = null,
    val savedSessions: List<LogSession> = emptyList(),
    // Replay
    val isReplaying: Boolean = false,
    val replaySession: LogSession? = null,
    val replayIndex: Int = 0,
    val replaySpeed: Float = 1.0f,
    val pidDiscoveryState: PidDiscoveryState = PidDiscoveryState.IDLE,
    // Smart display
    val displayMode: LiveDisplayMode = LiveDisplayMode.NUMERIC,
    val sortOrder: SortOrder = SortOrder.CATEGORY,
    val activeCategory: PidCategory? = null,
    val activeDtcFilter: List<String> = emptyList(),
    val derivedMetrics: List<DerivedMetric> = emptyList(),
    val favoritePidCodes: Set<String> = emptySet(),
    /** Source of the current supported-PID set: "cache_hit", "validated_cache", "discovery", "disconnected" */
    val supportSource: String = "disconnected"
)
