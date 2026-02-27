package com.odbplus.core.transport

/**
 * Constants used across the transport layer.
 */
object TransportConstants {
    /** Timeout for establishing a connection (in milliseconds). */
    const val CONNECTION_TIMEOUT_MS = 3000

    /** Default timeout for waiting for a response (in milliseconds). */
    const val DEFAULT_RESPONSE_TIMEOUT_MS = 3000L

    /** Extended timeout for initialization commands like ATZ (in milliseconds). */
    const val INIT_TIMEOUT_MS = 5000L

    /** Maximum number of log lines to retain in memory. */
    const val MAX_LOG_LINES = 200

    /**
     * SO_TIMEOUT for the underlying TCP socket read (milliseconds).
     *
     * This makes [InputStream.read] throw [java.net.SocketTimeoutException] after
     * this interval rather than blocking forever.  The reader loop treats that
     * exception as "no data yet" and loops, which keeps the coroutine cooperative
     * (cancellable) and prevents starving [Dispatchers.IO].
     *
     * Bluetooth sockets do not support SO_TIMEOUT on Android; the reader loop
     * relies on stream-close from [BaseTransport.close] to unblock instead.
     */
    const val SOCKET_READ_TIMEOUT_MS = 100
}

/**
 * ELM327 command constants.
 *
 * Centralised here so drivers never hard-code AT-command strings.
 */
object ElmCommands {
    /** Reset the ELM327 adapter (~1-2 s settle required). */
    const val RESET = "ATZ"

    /** Turn off command echo. */
    const val ECHO_OFF = "ATE0"

    /** Turn off linefeeds (rows separated by '\r' only). */
    const val LINEFEEDS_OFF = "ATL0"

    /**
     * Turn off spaces in hex output (e.g. "410C1AF8" instead of "41 0C 1A F8").
     * Optional — parsers must handle both forms; omitting spaces reduces bytes/frame.
     */
    const val SPACES_OFF = "ATS0"

    /** Turn on headers (CAN address prefix visible in responses). */
    const val HEADERS_ON = "ATH1"

    /** Set protocol to automatic (adapter auto-detects CAN/ISO/KWP). */
    const val PROTOCOL_AUTO = "ATSP0"

    /**
     * Adaptive timing level 1 — conservative (use for genuine ELM327).
     * The adapter adjusts its response window based on bus traffic.
     */
    const val ADAPTIVE_TIMING_1 = "ATAT1"

    /**
     * Adaptive timing level 2 — aggressive (use for STN/OBDLink).
     * Tighter window; adapter keeps shrinking until it finds the minimum stable delay.
     */
    const val ADAPTIVE_TIMING_2 = "ATAT2"

    /**
     * Close the current OBD protocol session without resetting the adapter.
     * Used during ERROR_RECOVERY to force a clean protocol re-negotiation.
     */
    const val PROTOCOL_CLOSE = "ATPC"
}
