package com.odbplus.app.vin.domain

/**
 * UI-facing state emitted by [VinDecodeViewModel] via StateFlow.
 * The coordinator publishes updates through the repository; the ViewModel exposes them here.
 */
sealed class VinDecodeState {
    object NotStarted : VinDecodeState()
    data class Validating(val vin: String) : VinDecodeState()
    data class Cached(val decoded: DecodedVin) : VinDecodeState()
    data class DecodingOnline(val vin: String) : VinDecodeState()
    data class Decoded(
        val decoded: DecodedVin,
        val verification: VinVerificationResult
    ) : VinDecodeState()
    data class Partial(
        val decoded: DecodedVin,
        val warnings: List<String>
    ) : VinDecodeState()
    data class VerificationWarning(
        val decoded: DecodedVin,
        val verification: VinVerificationResult
    ) : VinDecodeState()
    data class Failed(val vin: String, val reason: String) : VinDecodeState()
}

/**
 * Sealed result returned by [VinDecoderRepository] to the coordinator.
 * Distinct from [VinDecodeState] — this is the internal pipeline result,
 * not the UI state.
 */
sealed class VinDecodeOutcome {
    data class Success(
        val decoded: DecodedVin,
        val verification: VinVerificationResult
    ) : VinDecodeOutcome()

    data class Cached(val decoded: DecodedVin) : VinDecodeOutcome()

    data class PartialSuccess(
        val decoded: DecodedVin,
        val warnings: List<String>
    ) : VinDecodeOutcome()

    data class ValidationFailed(
        val vin: String,
        val validation: VinValidationResult
    ) : VinDecodeOutcome()

    data class NetworkFailed(
        val vin: String,
        val error: String
    ) : VinDecodeOutcome()

    data class ProviderReturnedIncomplete(
        val decoded: DecodedVin
    ) : VinDecodeOutcome()

    data class VerificationFailed(
        val vin: String,
        val verification: VinVerificationResult
    ) : VinDecodeOutcome()
}
