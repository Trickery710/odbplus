package com.odbplus.core.protocol.signalset

/**
 * Describes the binary encoding of a single signal within a CAN frame payload.
 *
 * @param bix      Start bit index (0 = MSB of first byte)
 * @param len      Bit length of the field
 * @param mul      Multiplier applied after extraction   (default 1)
 * @param div      Divisor applied after mul             (default 1)
 * @param add      Offset added after mul/div            (default 0)
 * @param signed   True if the raw field is two's-complement signed
 * @param unit     Physical unit string (e.g. "rpm", "°C", "km/h")
 * @param min      Minimum valid physical value (null = no lower bound)
 * @param max      Maximum valid physical value (null = no upper bound)
 * @param nullMin  Raw range lower bound that indicates "no data"
 * @param nullMax  Raw range upper bound that indicates "no data"
 * @param enumMap  Optional discrete-value label map (raw int → display string)
 */
data class SignalFormat(
    val bix: Int,
    val len: Int,
    val mul: Double = 1.0,
    val div: Double = 1.0,
    val add: Double = 0.0,
    val signed: Boolean = false,
    val unit: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val nullMin: Double? = null,
    val nullMax: Double? = null,
    val enumMap: Map<Int, String>? = null
)

/**
 * A single named signal decoded from one vehicle command response frame.
 *
 * @param id          Unique signal identifier (e.g. "ENGINE_COOLANT_TEMP")
 * @param name        Human-readable display name
 * @param description Optional description
 * @param fmt         Binary encoding and scaling parameters
 * @param suggestedMetric Suggested metric key for charting/logging
 */
data class VehicleSignal(
    val id: String,
    val name: String,
    val description: String = "",
    val fmt: SignalFormat,
    val suggestedMetric: String? = null
)

/**
 * One CAN command that the vehicle ECU responds to with a Mode 22 frame.
 *
 * @param hdr         CAN header / target ECU address (e.g. "7E0")
 * @param rax         Response CAN address / filter (e.g. "7E8")
 * @param service     OBD service byte (e.g. 0x22 for Mode 22 UDS Read DID)
 * @param did         Data Identifier string (e.g. "1002")
 * @param freq        Recommended query frequency in Hz (0 = on-demand)
 * @param yearFrom    Model-year range start (inclusive, null = all)
 * @param yearTo      Model-year range end   (inclusive, null = all)
 * @param signals     Signals encoded in the response payload
 */
data class VehicleCommand(
    val hdr: String,
    val rax: String,
    val service: Int,
    val did: String,
    val freq: Double = 0.0,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val signals: List<VehicleSignal>
) {
    /** Full command string sent to the adapter, e.g. "221002". */
    val commandString: String
        get() = String.format("%02X%s", service, did)
}

/**
 * All commands and signals for a specific vehicle model.
 *
 * @param vehicleKey  Repository key (e.g. "Toyota-Camry")
 * @param commands    All commands defined for this vehicle
 */
data class VehicleSignalSet(
    val vehicleKey: String,
    val commands: List<VehicleCommand>
) {
    /**
     * Return only the commands applicable to the given model year.
     * If [year] is null every command is included.
     */
    fun commandsForYear(year: Int?): List<VehicleCommand> {
        if (year == null) return commands
        return commands.filter { cmd ->
            val fromOk = cmd.yearFrom == null || year >= cmd.yearFrom
            val toOk   = cmd.yearTo   == null || year <= cmd.yearTo
            fromOk && toOk
        }
    }
}

/**
 * The result of extracting a single [VehicleSignal] from a response frame.
 *
 * @param signal    The signal definition that produced this result
 * @param rawValue  The physical (scaled) value, or null if extraction failed / null-range
 * @param enumLabel Resolved enum string when [SignalFormat.enumMap] is set
 */
data class VehicleSignalResult(
    val signal: VehicleSignal,
    val rawValue: Double?,
    val enumLabel: String? = null
) {
    /**
     * Human-readable representation: enum label, value+unit, or "–" when null.
     */
    val displayValue: String
        get() = when {
            rawValue == null    -> "–"
            enumLabel != null   -> enumLabel
            signal.fmt.unit.isNotEmpty() -> "${formatNumber(rawValue)} ${signal.fmt.unit}"
            else                -> formatNumber(rawValue)
        }

    private fun formatNumber(v: Double): String =
        if (v == kotlin.math.floor(v) && !v.isInfinite()) v.toLong().toString()
        else "%.2f".format(v)
}
