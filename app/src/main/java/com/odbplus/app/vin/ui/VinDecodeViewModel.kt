package com.odbplus.app.vin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.data.db.entity.VehicleIdentityEntity
import com.odbplus.app.vin.coordinator.VinDecodeCoordinator
import com.odbplus.app.vin.domain.VinDecodeState
import com.odbplus.app.vin.repository.VehicleIdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel exposing decoded VIN state to UI composables.
 *
 * Observes [VinDecodeCoordinator.decodeState] (already running in [AppScope])
 * and translates it to [VinDecodeUiState] for the View layer.
 *
 * Does NOT trigger decodes — that is done by [ConnectViewModel] via [VinDecodeCoordinator].
 */
@HiltViewModel
class VinDecodeViewModel @Inject constructor(
    private val coordinator: VinDecodeCoordinator,
    private val identityRepository: VehicleIdentityRepository
) : ViewModel() {

    /** Raw pipeline state for advanced consumers. */
    val decodeState: StateFlow<VinDecodeState> = coordinator.decodeState

    /**
     * UI-friendly state derived from [decodeState].
     * Suitable for direct use in composables.
     */
    val uiState: StateFlow<VinDecodeUiState> = coordinator.decodeState
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VinDecodeUiState.NotStarted
        )

    /**
     * Observe the persisted [VehicleIdentityEntity] for a specific VIN.
     * Returns null while no data is available.
     */
    fun observeIdentity(vin: String) = identityRepository.observeByVin(vin)

    /** Request a forced refresh for the current VIN. */
    fun requestRefresh(vin: String) {
        Timber.d("VIN ViewModel: manual refresh requested for $vin")
        coordinator.requestRefresh(vin)
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun VinDecodeState.toUiState(): VinDecodeUiState = when (this) {
        is VinDecodeState.NotStarted -> VinDecodeUiState.NotStarted
        is VinDecodeState.Validating -> VinDecodeUiState.Loading(vin, "Validating VIN…")
        is VinDecodeState.DecodingOnline -> VinDecodeUiState.Loading(vin, "Looking up vehicle data…")
        is VinDecodeState.Cached -> VinDecodeUiState.Ready(
            label = decoded.displayLabel(),
            make = decoded.make,
            model = decoded.model,
            modelYear = decoded.modelYear,
            trim = decoded.trim,
            engine = buildEngineString(decoded.engineCylinders, decoded.displacementL),
            bodyClass = decoded.bodyClass,
            fuelType = decoded.fuelTypePrimary,
            driveType = decoded.driveType,
            confidence = decoded.confidence,
            verificationStatus = decoded.verificationStatus.name,
            source = "Cached",
            warnings = emptyList()
        )
        is VinDecodeState.Decoded -> VinDecodeUiState.Ready(
            label = decoded.displayLabel(),
            make = decoded.make,
            model = decoded.model,
            modelYear = decoded.modelYear,
            trim = decoded.trim,
            engine = buildEngineString(decoded.engineCylinders, decoded.displacementL),
            bodyClass = decoded.bodyClass,
            fuelType = decoded.fuelTypePrimary,
            driveType = decoded.driveType,
            confidence = decoded.confidence,
            verificationStatus = decoded.verificationStatus.name,
            source = decoded.source.name,
            warnings = verification.warnings
        )
        is VinDecodeState.Partial -> VinDecodeUiState.Ready(
            label = decoded.displayLabel(),
            make = decoded.make,
            model = decoded.model,
            modelYear = decoded.modelYear,
            trim = decoded.trim,
            engine = buildEngineString(decoded.engineCylinders, decoded.displacementL),
            bodyClass = decoded.bodyClass,
            fuelType = decoded.fuelTypePrimary,
            driveType = decoded.driveType,
            confidence = decoded.confidence,
            verificationStatus = decoded.verificationStatus.name,
            source = decoded.source.name,
            warnings = warnings
        )
        is VinDecodeState.VerificationWarning -> VinDecodeUiState.Warning(
            label = decoded.displayLabel(),
            confidence = decoded.confidence,
            warnings = verification.warnings + verification.errors
        )
        is VinDecodeState.Failed -> VinDecodeUiState.Failed(vin, reason)
    }

    private fun buildEngineString(cylinders: Int?, displacementL: Double?): String? =
        when {
            cylinders != null && displacementL != null ->
                "${"%.1f".format(displacementL)}L ${cylinders}-cyl"
            cylinders != null -> "${cylinders}-cyl"
            displacementL != null -> "${"%.1f".format(displacementL)}L"
            else -> null
        }
}

/** UI model for composables — no raw domain types exposed. */
sealed class VinDecodeUiState {
    object NotStarted : VinDecodeUiState()

    data class Loading(
        val vin: String,
        val message: String
    ) : VinDecodeUiState()

    data class Ready(
        val label: String,
        val make: String?,
        val model: String?,
        val modelYear: Int?,
        val trim: String?,
        val engine: String?,
        val bodyClass: String?,
        val fuelType: String?,
        val driveType: String?,
        val confidence: Float,
        val verificationStatus: String,
        val source: String,
        val warnings: List<String>
    ) : VinDecodeUiState()

    data class Warning(
        val label: String,
        val confidence: Float,
        val warnings: List<String>
    ) : VinDecodeUiState()

    data class Failed(
        val vin: String,
        val reason: String
    ) : VinDecodeUiState()
}
