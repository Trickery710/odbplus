package com.odbplus.core.protocol.adapter

/**
 * Runtime capability flags resolved during adapter fingerprinting.
 *
 * Every flag defaults to the safest / most-conservative value so that
 * unknown adapters degrade gracefully rather than crash.
 */
data class DeviceCapabilities(

    // ── CAN/Protocol ─────────────────────────────────────────────────────────

    /** Adapter accepts and correctly handles ISO-TP frames > 8 bytes (ATAL). */
    val supportsLongCanFrames: Boolean = false,

    /** Single-wire CAN (SW CAN / GMLAN) supported — STN family only. */
    val supportsSwCan: Boolean = false,

    /** Full J1939 29-bit PGN support. */
    val supportsJ1939: Boolean = false,

    /** Adapter can stream raw binary CAN frames (ESP32 framed mode). */
    val supportsBinaryMode: Boolean = false,

    /** Adapter will auto-detect the OBD protocol (ATSP0). */
    val supportsAutoProtocol: Boolean = true,

    // ── AT Command Surface ────────────────────────────────────────────────────

    /** ATAL (Allow Long messages) accepted without error. */
    val supportsAtAl: Boolean = false,

    /** ATCAF1 (CAN Auto-Formatting) accepted without error. */
    val supportsAtCaf: Boolean = false,

    /** ATSH (Set Header) accepted — custom CAN headers can be used. */
    val supportsCustomHeaders: Boolean = true,

    /** Adapter supports high-frequency polling (>12 PIDs/sec). */
    val supportsHighSpeedPolling: Boolean = false,

    // ── Timing & Buffering ────────────────────────────────────────────────────

    /**
     * A mandatory inter-command delay must be inserted.
     * True for known-unstable clones that lock up under rapid-fire commands.
     */
    val requiresDelayBetweenCmds: Boolean = false,

    /** Usable input buffer in bytes. Limits ISO-TP payload on some clones. */
    val bufferSize: Int = 256,

    /** Maximum safe polling rate in Hz (PIDs per second). */
    val maxPollRateHz: Int = 10,

    /** Recommended delay between consecutive AT/OBD commands in milliseconds. */
    val recommendedDelayMs: Long = 20L,

    // ── Keepalive ─────────────────────────────────────────────────────────────

    /** Adapter requires periodic keepalive pings to prevent Bluetooth disconnect. */
    val requiresKeepalive: Boolean = false,

    /** Interval in milliseconds between keepalive pings when idle. */
    val keepaliveIntervalMs: Long = 15_000L
)
