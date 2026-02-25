package com.odbplus.core.protocol.signalset

/**
 * Extracts a physical value from a raw CAN payload byte array.
 *
 * Bit addressing follows the OBDb convention used in signalset v3 JSON:
 * - bix 0  = MSB of byte[0]
 * - bix 7  = LSB of byte[0]
 * - bix 8  = MSB of byte[1]
 * … and so on (big-endian / Motorola byte order).
 *
 * Steps:
 * 1. Determine the byte range that contains the field.
 * 2. Read those bytes into a Long (big-endian).
 * 3. Right-shift to align the field's LSB with bit 0.
 * 4. Mask to [len] bits.
 * 5. Optionally sign-extend for two's-complement interpretation.
 * 6. Apply: physicalValue = raw * mul / div + add
 * 7. Return null when the value falls in the null range or outside min/max.
 *
 * @receiver The [SignalFormat] that describes how the field is encoded.
 * @param payload Raw bytes returned by the ECU (header stripped, data bytes only).
 * @return Physical value, or null when the signal is invalid / not available.
 */
fun SignalFormat.extract(payload: ByteArray): Double? {
    val startByte = bix / 8
    val endByte   = (bix + len - 1) / 8

    if (endByte >= payload.size) return null

    // Accumulate the spanning bytes into a Long (big-endian)
    var raw = 0L
    for (i in startByte..endByte) {
        raw = (raw shl 8) or (payload[i].toLong() and 0xFF)
    }

    // Number of bits to the right of our field's LSB within the accumulated window
    val totalBits  = (endByte - startByte + 1) * 8
    val rightShift = totalBits - (bix % 8) - len
    raw = raw shr rightShift

    // Mask to the exact field width
    val mask = if (len >= 64) -1L else (1L shl len) - 1L
    raw = raw and mask

    // Two's-complement sign extension
    if (signed && len > 0) {
        val signBit = 1L shl (len - 1)
        if (raw and signBit != 0L) {
            raw = raw or signBit.inv().xor(mask).inv()   // fill upper bits with 1s
            // Simpler equivalent: raw -= (1L shl len)
            raw -= (1L shl len)
        }
    }

    val physical = raw.toDouble() * mul / div + add

    // Null-range check (signals "NO DATA" / sensor not fitted)
    if (nullMin != null && nullMax != null && physical in nullMin..nullMax) return null

    // Physical range check
    if (min != null && physical < min) return null
    if (max != null && physical > max) return null

    return physical
}

/**
 * Convenience extension: extract this signal's value from [payload] and
 * wrap it in a [VehicleSignalResult], resolving enum labels when applicable.
 */
fun VehicleSignal.extractFrom(payload: ByteArray): VehicleSignalResult {
    val value = fmt.extract(payload)
    val label = if (value != null && fmt.enumMap != null) {
        val key = value.toLong().toInt()
        fmt.enumMap[key]
    } else null
    return VehicleSignalResult(signal = this, rawValue = value, enumLabel = label)
}
