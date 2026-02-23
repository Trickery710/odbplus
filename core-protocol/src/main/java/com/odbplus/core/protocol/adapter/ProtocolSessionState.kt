package com.odbplus.core.protocol.adapter

/**
 * Full state machine for an OBD-II adapter session.
 *
 * ```
 *  DISCONNECTED
 *       │  connect()
 *       ▼
 *  TRANSPORT_CONNECTED
 *       │  fingerprint OK
 *       ▼
 *  DEVICE_IDENTIFIED
 *       │  ATSP / protocol probe OK
 *       ▼
 *  PROTOCOL_DETECTED
 *       │  0100 success
 *       ▼
 *  SESSION_ACTIVE ◄──────────────────────────────────────────┐
 *       │  startStream()           │ stopStream()             │
 *       ▼                         │                           │
 *  STREAMING ───────────────────────────────────────────────►┘
 *       │
 *  on 3 consecutive failures ──► ERROR_RECOVERY
 *       │                              │
 *  on transport drop ──► RECONNECTING ─┘ (→ TRANSPORT_CONNECTED on success)
 * ```
 */
enum class ProtocolSessionState {

    /** No transport connection. */
    DISCONNECTED,

    /** TCP/Bluetooth socket is open; device has not yet been identified. */
    TRANSPORT_CONNECTED,

    /** ATZ + ATI + fingerprinting complete; chip family and capabilities are known. */
    DEVICE_IDENTIFIED,

    /** OBD protocol negotiation succeeded (ATSP / 0100 response received). */
    PROTOCOL_DETECTED,

    /** Session is fully operational; PIDs can be queried. */
    SESSION_ACTIVE,

    /**
     * High-frequency CAN streaming is active.
     * The session stays in this state until [stopStreaming] is called or an error occurs.
     */
    STREAMING,

    /**
     * Three consecutive command failures detected.
     * The session attempts a soft-reset (ATZ) before returning to DEVICE_IDENTIFIED.
     */
    ERROR_RECOVERY,

    /**
     * Transport connection was lost.
     * The session attempts to reconnect; on success it returns to TRANSPORT_CONNECTED.
     */
    RECONNECTING;

    /** True when the session can process OBD commands. */
    val canSendCommands: Boolean
        get() = this == SESSION_ACTIVE || this == STREAMING

    /** True when the session is in a degraded / recovery state. */
    val isRecovering: Boolean
        get() = this == ERROR_RECOVERY || this == RECONNECTING

    /** True when the underlying transport is believed to be up. */
    val isTransportAlive: Boolean
        get() = this != DISCONNECTED && this != RECONNECTING
}
