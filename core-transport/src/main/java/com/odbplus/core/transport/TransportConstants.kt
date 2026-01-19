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
}

/**
 * ELM327 command constants.
 */
object ElmCommands {
    /** Reset the ELM327 adapter. */
    const val RESET = "ATZ"

    /** Turn off command echo. */
    const val ECHO_OFF = "ATE0"

    /** Turn off linefeeds. */
    const val LINEFEEDS_OFF = "ATL0"

    /** Turn on headers (PIDs visible). */
    const val HEADERS_ON = "ATH1"

    /** Set protocol to automatic. */
    const val PROTOCOL_AUTO = "ATSP0"
}
