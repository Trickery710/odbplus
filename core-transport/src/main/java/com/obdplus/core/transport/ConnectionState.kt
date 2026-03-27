package com.obdplus.core.transport

/**
 * Represents the various states of a transport layer connection.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
