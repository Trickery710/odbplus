
package com.obdplus.core

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Reconnecting : ConnectionState()
}
