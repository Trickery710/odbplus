package com.odbplus.core.protocol.adapter

/**
 * Hardware family classification for detected OBD-II adapters.
 *
 * Used by the fingerprinting engine to route commands through
 * the appropriate driver with the correct timing and behavior.
 */
enum class AdapterFamily {
    /** Genuine ELM327 (PIC18F2480 / PIC18F25K80) — verified firmware v1.x or v2.0 */
    ELM327,

    /**
     * ELM327 clone / counterfeit.
     *
     * Indicators: reports v1.5 (no such official version), reports v2.1 but fails
     * ATAL, fails ATCAF1, or produces garbled AT@1 responses.
     */
    ELM_CLONE,

    /** Genuine STN chip (STN1110 / STN1170 / STN2120 / STN2230) */
    STN,

    /**
     * OBDLink-branded device (Scantool.net).
     * Also STN-based but exposes the full OBDLink command surface and reports
     * "OBDLink" in its AT@1 / ATI response.
     */
    OBDLINK,

    /**
     * ESP32-based custom or DIY adapter.
     * May speak ELM ASCII emulation, a binary CAN stream, REST/WebSocket, or MQTT.
     */
    ESP32,

    /** Could not classify the adapter — safe conservative defaults will be applied. */
    UNKNOWN
}
