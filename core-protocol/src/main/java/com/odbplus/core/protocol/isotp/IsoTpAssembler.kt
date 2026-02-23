package com.odbplus.core.protocol.isotp

import timber.log.Timber

/**
 * Software ISO-TP (ISO 15765-2) multi-frame assembler.
 *
 * Used when the adapter does not handle ISO-TP reassembly internally
 * (i.e. ATCAF is not available or the adapter is a dumb clone).
 *
 * ## Frame types handled
 * | Nibble | Type            | Meaning                                |
 * |--------|-----------------|----------------------------------------|
 * | 0x0    | Single Frame    | Complete message in one frame          |
 * | 0x1    | First Frame     | Start of multi-frame message           |
 * | 0x2    | Consecutive Frame | Continuation of multi-frame message  |
 * | 0x3    | Flow Control    | Sent TO the ECU (not assembled here)   |
 *
 * ## Usage
 * ```
 * val asm = IsoTpAssembler()
 * for (rawHexLine in responseLines) {
 *     val result = asm.feed(rawHexLine)
 *     if (result != null) {
 *         // complete payload ready
 *     }
 * }
 * ```
 */
class IsoTpAssembler {

    private var totalLength: Int = 0
    private var expectedSequence: Int = 0
    private val assembled = mutableListOf<Byte>()
    private var state: State = State.IDLE

    enum class State { IDLE, COLLECTING }

    /** Result of feeding one raw hex line to the assembler. */
    data class AssemblyResult(
        val payload: ByteArray,
        val isComplete: Boolean,
        val totalExpected: Int,
        val received: Int
    )

    /**
     * Feed one raw hex line (space-separated or compact) from the adapter.
     *
     * @return [AssemblyResult] when the message is complete, null if more frames needed.
     */
    fun feed(rawLine: String): AssemblyResult? {
        val bytes = parseHex(rawLine) ?: return null
        if (bytes.isEmpty()) return null

        // Strip the 3-byte ISO 15765-4 header if headers are on (ATH1 mode)
        // Header format: [ECU_addr] [PCI_nibble | len_nibble] ...
        // For simplicity we handle both with-header and no-header responses.
        val pciOffset = detectPciOffset(bytes)
        if (pciOffset < 0 || pciOffset >= bytes.size) return null

        val pciNibble = (bytes[pciOffset].toInt() and 0xFF) shr 4

        return when (pciNibble) {
            0x0 -> handleSingleFrame(bytes, pciOffset)
            0x1 -> handleFirstFrame(bytes, pciOffset)
            0x2 -> handleConsecutiveFrame(bytes, pciOffset)
            0x3 -> null  // Flow Control — sent by us, not assembled
            else -> {
                Timber.w("IsoTpAssembler: unknown PCI nibble 0x${pciNibble.toString(16)}")
                null
            }
        }
    }

    /** Reset assembler state — call before starting a new query. */
    fun reset() {
        state = State.IDLE
        assembled.clear()
        totalLength = 0
        expectedSequence = 0
    }

    /** True if a multi-frame assembly is in progress. */
    val isCollecting: Boolean get() = state == State.COLLECTING

    // ── Frame handlers ────────────────────────────────────────────────────────

    private fun handleSingleFrame(bytes: ByteArray, pciOffset: Int): AssemblyResult? {
        val len = bytes[pciOffset].toInt() and 0x0F
        val dataStart = pciOffset + 1
        if (dataStart + len > bytes.size) {
            Timber.w("IsoTpAssembler: SF length overrun (len=$len bytes=${bytes.size})")
            return null
        }
        val payload = bytes.copyOfRange(dataStart, dataStart + len)
        reset()
        return AssemblyResult(payload, isComplete = true, totalExpected = len, received = len)
    }

    private fun handleFirstFrame(bytes: ByteArray, pciOffset: Int): AssemblyResult? {
        val b0 = bytes[pciOffset].toInt() and 0xFF
        val b1 = bytes[pciOffset + 1].toInt() and 0xFF
        totalLength = ((b0 and 0x0F) shl 8) or b1

        if (totalLength > MAX_PAYLOAD_BYTES) {
            Timber.w("IsoTpAssembler: FF payload too large ($totalLength bytes) — capping")
            totalLength = MAX_PAYLOAD_BYTES
        }

        val dataStart = pciOffset + 2
        assembled.clear()
        assembled.addAll(bytes.drop(dataStart).toList())
        expectedSequence = 1
        state = State.COLLECTING

        Timber.d("IsoTpAssembler: FF — totalLength=$totalLength  collected=${assembled.size}")
        return null  // Need consecutive frames
    }

    private fun handleConsecutiveFrame(bytes: ByteArray, pciOffset: Int): AssemblyResult? {
        if (state != State.COLLECTING) {
            Timber.w("IsoTpAssembler: unexpected CF — not collecting")
            return null
        }

        val seq = bytes[pciOffset].toInt() and 0x0F
        if (seq != expectedSequence) {
            Timber.w("IsoTpAssembler: CF sequence mismatch — expected $expectedSequence got $seq")
            reset()
            return null
        }

        val dataStart = pciOffset + 1
        assembled.addAll(bytes.drop(dataStart).toList())
        expectedSequence = (expectedSequence + 1) and 0x0F  // Wraps 0–15

        Timber.d("IsoTpAssembler: CF seq=$seq  collected=${assembled.size}/$totalLength")

        return if (assembled.size >= totalLength) {
            val payload = assembled.take(totalLength).toByteArray()
            reset()
            AssemblyResult(payload, isComplete = true, totalExpected = totalLength, received = payload.size)
        } else {
            null
        }
    }

    // ── PCI offset detection ──────────────────────────────────────────────────

    /**
     * Detect where the PCI byte starts.
     *
     * - With ATH1 (headers on): 3-byte CAN header precedes PCI
     *   e.g.  7E8 10 14 49 02 01 ...
     *              ↑ PCI at index 3
     * - Without headers: PCI at index 0
     *   e.g.  10 14 49 02 01 ...
     *          ↑ PCI at index 0
     */
    private fun detectPciOffset(bytes: ByteArray): Int {
        if (bytes.size < 1) return -1

        val firstByte = bytes[0].toInt() and 0xFF
        val pciNibble = firstByte shr 4

        // If first nibble looks like a CAN address header byte (unlikely to be 0–3)
        // and we have at least 4 bytes, assume 3-byte header
        return if (pciNibble > 3 && bytes.size >= 4) 3 else 0
    }

    // ── Hex parsing ───────────────────────────────────────────────────────────

    private fun parseHex(raw: String): ByteArray? {
        val tokens = raw.trim().uppercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        return try {
            tokens.mapNotNull { t ->
                if (t.all { it.isLetterOrDigit() } && t.length <= 2) t.toInt(16).toByte()
                else null
            }.toByteArray().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Timber.w("IsoTpAssembler: hex parse failed for '$raw'")
            null
        }
    }

    companion object {
        /**
         * Maximum assembled payload in bytes.
         * ISO-TP theoretically supports 4095; practical safe limit for most adapters is 512.
         */
        const val MAX_PAYLOAD_BYTES = 512
    }
}
