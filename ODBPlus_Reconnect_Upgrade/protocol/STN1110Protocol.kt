
package com.obdplus.protocol

import com.obdplus.transport.Transport

class STN1110Protocol(private val transport: Transport) {

    suspend fun initialize() {
        transport.write("ATZ")
        transport.read()
        transport.write("ATE0")
        transport.read()
        transport.write("ATL0")
        transport.read()
    }

    suspend fun send(command: String): String {
        transport.write(command)
        return transport.read()
    }
}
