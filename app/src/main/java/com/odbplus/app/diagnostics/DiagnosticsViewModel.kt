package com.odbplus.app.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.core.protocol.DiagnosticTroubleCode
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.transport.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val storedCodes: List<DiagnosticTroubleCode> = emptyList(),
    val pendingCodes: List<DiagnosticTroubleCode> = emptyList(),
    val lastReadTime: Long? = null,
    val errorMessage: String? = null,
    val clearSuccess: Boolean? = null
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val obdService: ObdService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            obdService.connectionState.collect { state ->
                _uiState.update { it.copy(isConnected = state == ConnectionState.CONNECTED) }
            }
        }
    }

    fun readCodes() {
        if (!_uiState.value.isConnected) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, clearSuccess = null) }

            try {
                val stored = obdService.readStoredDtcs()
                val pending = obdService.readPendingDtcs()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        storedCodes = stored,
                        pendingCodes = pending,
                        lastReadTime = System.currentTimeMillis(),
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to read codes: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearCodes() {
        if (!_uiState.value.isConnected) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, clearSuccess = null) }

            try {
                val success = obdService.clearDtcs()

                if (success) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            storedCodes = emptyList(),
                            pendingCodes = emptyList(),
                            clearSuccess = true,
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            clearSuccess = false,
                            errorMessage = "Clear command did not confirm"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        clearSuccess = false,
                        errorMessage = "Failed to clear codes: ${e.message}"
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(errorMessage = null, clearSuccess = null) }
    }
}
