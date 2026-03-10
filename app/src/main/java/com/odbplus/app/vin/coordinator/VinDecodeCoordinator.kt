package com.odbplus.app.vin.coordinator

import com.odbplus.app.vin.domain.VinDecodeOutcome
import com.odbplus.app.vin.domain.VinDecodeState
import com.odbplus.app.vin.domain.VerificationStatus
import com.odbplus.app.vin.repository.VinDecoderRepository
import com.odbplus.app.vin.validation.VinValidator
import com.odbplus.core.transport.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background sidecar coordinator for VIN decoding.
 *
 * Operates as a co-process that is completely decoupled from:
 * - Live OBD PID polling
 * - Connect/disconnect flows
 * - UI rendering
 * - DTC reads
 *
 * Key behaviors:
 * - Debounces repeated VIN events (same VIN within [DEBOUNCE_WINDOW_MS] is ignored)
 * - Deduplicates concurrent decode jobs — only one job per VIN at a time
 * - Uses SupervisorJob (via [AppScope]) so a failed decode never crashes the app
 * - All network/DB work runs on [Dispatchers.IO]
 * - Publishes [VinDecodeState] to [decodeState] for UI consumption
 */
@Singleton
class VinDecodeCoordinator @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val repository: VinDecoderRepository
) {

    companion object {
        /** Minimum interval between decode attempts for the same VIN (milliseconds). */
        private const val DEBOUNCE_WINDOW_MS = 5_000L
    }

    private val _decodeState = MutableStateFlow<VinDecodeState>(VinDecodeState.NotStarted)
    val decodeState: StateFlow<VinDecodeState> = _decodeState.asStateFlow()

    /** Guards [inFlightJobs] and [lastDecodeTime] from concurrent modification. */
    private val mutex = Mutex()

    /** VINs currently being decoded. Prevents duplicate network calls. */
    private val inFlightJobs = mutableMapOf<String, Job>()

    /** Last time (epoch ms) each VIN was submitted for decode. Used for debouncing. */
    private val lastDecodeTime = mutableMapOf<String, Long>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called by [ConnectViewModel] (or any OBD layer) when a VIN is discovered from the ECU.
     *
     * This is fire-and-forget — returns immediately without blocking the caller.
     * The decode result is published via [decodeState].
     *
     * @param rawVin      The VIN string as returned by the ECU (may be raw/unformatted).
     * @param forceRefresh True to bypass cache (subject to rate limiting).
     */
    fun onVinDiscovered(rawVin: String, forceRefresh: Boolean = false) {
        val normalized = VinValidator.normalize(rawVin)
        if (normalized.isBlank()) {
            Timber.w("VIN coordinator: ignoring blank VIN")
            return
        }

        appScope.launch(Dispatchers.Default) {
            mutex.withLock {
                // Debounce: ignore if same VIN was recently submitted
                val lastTime = lastDecodeTime[normalized] ?: 0L
                val now = System.currentTimeMillis()
                if (!forceRefresh && (now - lastTime) < DEBOUNCE_WINDOW_MS) {
                    Timber.d("VIN coordinator: debouncing $normalized (${now - lastTime}ms since last)")
                    return@withLock
                }

                // Deduplication: cancel existing in-flight job if force-refreshing
                if (normalized in inFlightJobs) {
                    if (forceRefresh) {
                        Timber.d("VIN coordinator: cancelling previous job for $normalized (force refresh)")
                        inFlightJobs[normalized]?.cancel()
                    } else {
                        Timber.d("VIN coordinator: decode already in flight for $normalized")
                        return@withLock
                    }
                }

                lastDecodeTime[normalized] = now

                val job = appScope.launch(Dispatchers.IO) {
                    runDecode(normalized, forceRefresh)
                }
                inFlightJobs[normalized] = job
                job.invokeOnCompletion {
                    appScope.launch { mutex.withLock { inFlightJobs.remove(normalized) } }
                }
            }
        }
    }

    /**
     * Force a refresh for the currently displayed VIN.
     * Subject to rate limiting in [VinDecoderRepository].
     */
    fun requestRefresh(vin: String) {
        onVinDiscovered(vin, forceRefresh = true)
    }

    /**
     * Reset state — typically called on adapter disconnect.
     * In-flight jobs are NOT cancelled; their results will still be persisted.
     */
    fun reset() {
        _decodeState.value = VinDecodeState.NotStarted
        Timber.d("VIN coordinator: reset")
    }

    // ── Private pipeline ──────────────────────────────────────────────────────

    private suspend fun runDecode(vin: String, forceRefresh: Boolean) {
        try {
            _decodeState.value = VinDecodeState.Validating(vin)
            Timber.d("VIN coordinator: starting decode for $vin")

            val outcome = repository.decode(vin, forceRefresh)
            Timber.d("VIN coordinator: outcome for $vin — ${outcome::class.simpleName}")

            _decodeState.value = outcome.toUiState(vin)
        } catch (e: Exception) {
            // Coordinator must never propagate — log and surface as Failed
            Timber.e(e, "VIN coordinator: unexpected error for $vin")
            _decodeState.value = VinDecodeState.Failed(vin, e.message ?: "Unexpected error")
        }
    }

    // ── Outcome → UI state mapping ────────────────────────────────────────────

    private fun VinDecodeOutcome.toUiState(vin: String): VinDecodeState = when (this) {
        is VinDecodeOutcome.Success -> {
            if (verification.status == VerificationStatus.SUSPECT) {
                VinDecodeState.VerificationWarning(decoded, verification)
            } else {
                VinDecodeState.Decoded(decoded, verification)
            }
        }
        is VinDecodeOutcome.Cached ->
            VinDecodeState.Cached(decoded)

        is VinDecodeOutcome.PartialSuccess ->
            VinDecodeState.Partial(decoded, warnings)

        is VinDecodeOutcome.ValidationFailed ->
            VinDecodeState.Failed(this.vin, validation.validationMessages.joinToString("; "))

        is VinDecodeOutcome.NetworkFailed ->
            VinDecodeState.Failed(this.vin, error)

        is VinDecodeOutcome.ProviderReturnedIncomplete ->
            VinDecodeState.Partial(decoded, listOf("Provider returned incomplete data"))

        is VinDecodeOutcome.VerificationFailed ->
            VinDecodeState.VerificationWarning(
                decoded = com.odbplus.app.vin.domain.DecodedVin(
                    vin = this.vin,
                    make = null, model = null, modelYear = null, trim = null, series = null,
                    manufacturer = null, vehicleType = null, bodyClass = null, engineModel = null,
                    engineCylinders = null, displacementL = null, fuelTypePrimary = null,
                    fuelTypeSecondary = null, driveType = null, transmissionStyle = null,
                    plantCountry = null, plantCompany = null, plantCity = null, plantState = null,
                    gvwrClass = null, brakeSystemType = null, airBagLocations = null,
                    source = com.odbplus.app.vin.domain.DecodeSource.LOCAL_ONLY,
                    decodeTimestamp = System.currentTimeMillis(),
                    confidence = verification.confidence,
                    verificationStatus = verification.status,
                    warnings = verification.warnings
                ),
                verification = verification
            )
    }
}
