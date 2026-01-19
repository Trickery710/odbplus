package com.odbplus.core.transport

/**
 * Base exception for all transport-related errors.
 */
sealed class TransportException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Thrown when a connection cannot be established.
 */
class ConnectionException(
    message: String,
    cause: Throwable? = null
) : TransportException(message, cause)

/**
 * Thrown when a read/write operation times out.
 */
class TimeoutException(
    message: String = "Operation timed out"
) : TransportException(message)

/**
 * Thrown when the transport is not connected.
 */
class NotConnectedException(
    message: String = "Not connected"
) : TransportException(message)

/**
 * Thrown when Bluetooth is not available or disabled.
 */
class BluetoothUnavailableException(
    message: String
) : TransportException(message)
