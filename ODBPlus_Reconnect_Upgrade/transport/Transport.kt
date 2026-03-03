
package com.odbplus.transport

interface Transport {
    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun write(command: String)
    suspend fun read(): String
    fun isConnected(): Boolean
}
