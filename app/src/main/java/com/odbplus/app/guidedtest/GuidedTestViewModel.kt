package com.odbplus.app.guidedtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.AiSettingsRepository
import com.odbplus.app.ai.ApiResult
import com.odbplus.app.ai.ClaudeApiService
import com.odbplus.app.ai.data.ClaudeMessage
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.transport.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class GuidedTestUiState {

    /** Waiting for the user to tap "Start Test". */
    data class Idle(val isConnected: Boolean) : GuidedTestUiState()

    /** A stage is actively running. */
    data class Running(
        val stage: TestStage,
        val liveRpm: Int?,
        val timeInRangeSec: Float,
        val inRange: Boolean,
        val skipValidation: Boolean = false,
    ) : GuidedTestUiState()

    /** Collecting DTCs and VIN after the three stages complete. */
    data object ReadingDtcs : GuidedTestUiState()

    /** User selects symptoms and enters free text before payload is sent. */
    data class SymptomsInput(
        val storedDtcs: List<String>,
        val pendingDtcs: List<String>,
        val selectedSymptoms: Set<String> = emptySet(),
        val freeText: String = "",
    ) : GuidedTestUiState()

    /** Payload is being sent to the AI backend. */
    data class Sending(val message: String = "Sending to AI…") : GuidedTestUiState()

    /** Test complete. Shows AI analysis or raw JSON if no API is configured. */
    data class Done(
        val summary: String,
        val payloadJson: String,
        val hasApi: Boolean,
    ) : GuidedTestUiState()

    /** An unrecoverable error occurred. */
    data class Error(val message: String) : GuidedTestUiState()

    /** The user cancelled the test. */
    data object Cancelled : GuidedTestUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class GuidedTestViewModel @Inject constructor(
    private val obdService: ObdService,
    private val aiSettings: AiSettingsRepository,
    private val aiService: ClaudeApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GuidedTestUiState>(
        GuidedTestUiState.Idle(isConnected = false)
    )
    val uiState: StateFlow<GuidedTestUiState> = _uiState.asStateFlow()

    private val orchestrator = GuidedRpmTestOrchestrator(obdService)
    private var testJob: Job? = null

    /** Mutable test session data accumulated during the run. */
    private var testStartTime: Long = 0L
    private var _skipValidation = false
    private var _stageResults = mutableListOf<StageRunResult>()
    private var _cachedVin: String? = null

    private val payloadJson = Json { prettyPrint = true }

    init {
        viewModelScope.launch {
            obdService.connectionState.collect { state ->
                val connected = state == ConnectionState.CONNECTED
                _uiState.update { current ->
                    if (current is GuidedTestUiState.Idle) current.copy(isConnected = connected) else current
                }
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun startTest() {
        _skipValidation = false
        _stageResults.clear()
        _cachedVin = null
        testStartTime = System.currentTimeMillis()

        testJob?.cancel()
        testJob = viewModelScope.launch {
            try {
                val stages = orchestrator.runAllStages(
                    onStageStart = { stage ->
                        _uiState.value = GuidedTestUiState.Running(
                            stage = stage,
                            liveRpm = orchestrator.liveRpm.value,
                            timeInRangeSec = 0f,
                            inRange = false,
                            skipValidation = _skipValidation,
                        )
                    },
                    onProgress = { stage, timeInRange, inRange ->
                        _uiState.update { current ->
                            if (current is GuidedTestUiState.Running && current.stage == stage) {
                                current.copy(
                                    liveRpm = orchestrator.liveRpm.value,
                                    timeInRangeSec = timeInRange,
                                    inRange = inRange,
                                    skipValidation = _skipValidation,
                                )
                            } else current
                        }
                    },
                    skipValidation = { _skipValidation },
                )
                _stageResults.addAll(stages)

                // Collect VIN and DTCs
                _uiState.value = GuidedTestUiState.ReadingDtcs
                _cachedVin = orchestrator.readVin()
                val (stored, pending) = orchestrator.readDtcs()

                _uiState.value = GuidedTestUiState.SymptomsInput(
                    storedDtcs = stored,
                    pendingDtcs = pending,
                )
            } catch (e: CancellationException) {
                if (_uiState.value !is GuidedTestUiState.Cancelled) {
                    _uiState.value = GuidedTestUiState.Cancelled
                }
                throw e
            } catch (e: Exception) {
                _uiState.value = GuidedTestUiState.Error(e.message ?: "Test failed unexpectedly")
            }
        }
    }

    /** Ask the orchestrator to skip RPM validation for the current stage. */
    fun skipValidation() {
        _skipValidation = true
    }

    /** Cancel the running test immediately. */
    fun cancelTest() {
        testJob?.cancel()
        _uiState.value = GuidedTestUiState.Cancelled
    }

    fun toggleSymptom(symptom: String) {
        _uiState.update { current ->
            if (current is GuidedTestUiState.SymptomsInput) {
                val updated = current.selectedSymptoms.toMutableSet().apply {
                    if (contains(symptom)) remove(symptom) else add(symptom)
                }
                current.copy(selectedSymptoms = updated)
            } else current
        }
    }

    fun updateFreeText(text: String) {
        _uiState.update { current ->
            if (current is GuidedTestUiState.SymptomsInput) current.copy(freeText = text) else current
        }
    }

    /** Build the payload and send to AI (or show clipboard fallback if no API is set). */
    fun submitSymptoms() {
        val symptoms = _uiState.value as? GuidedTestUiState.SymptomsInput ?: return
        viewModelScope.launch {
            _uiState.value = GuidedTestUiState.Sending("Building payload…")

            val stageResults = _stageResults.map { run ->
                buildStageResult(run)
            }

            val result = GuidedTestResult(
                startTime = testStartTime,
                endTime = System.currentTimeMillis(),
                adapterLabel = resolveAdapterLabel(),
                vin = _cachedVin,
                stageResults = stageResults,
                storedDtcs = symptoms.storedDtcs,
                pendingDtcs = symptoms.pendingDtcs,
                selectedSymptoms = symptoms.selectedSymptoms,
                freeText = symptoms.freeText,
            )

            val json = payloadJson.encodeToString(result.toPayload())
            val hasApi = aiSettings.hasApiKey.first()

            if (hasApi) {
                _uiState.value = GuidedTestUiState.Sending("Sending to AI…")
                val provider = aiSettings.selectedProvider.first()
                val apiKey = aiSettings.apiKey.first().orEmpty()
                val useOAuth = aiSettings.useGoogleAuthForGemini.first()

                val systemPrompt = "You are an expert automotive diagnostic AI. " +
                    "Analyze the OBD-II guided test data below and provide a structured, " +
                    "concise diagnosis. Highlight what the data reveals about engine health, " +
                    "likely causes for reported symptoms, and recommended next steps or repairs."

                val messages = listOf(ClaudeMessage(role = "user", content = json))
                val apiResult = try {
                    aiService.sendMessage(provider, apiKey, systemPrompt, messages, useOAuth)
                } catch (e: Exception) {
                    ApiResult.Error("Network error: ${e.message}")
                }

                val summary = when (apiResult) {
                    is ApiResult.Success -> apiResult.data.content
                    is ApiResult.Error -> "AI error: ${apiResult.message}\n\nRaw payload is ready to copy below."
                }
                _uiState.value = GuidedTestUiState.Done(summary = summary, payloadJson = json, hasApi = true)
            } else {
                _uiState.value = GuidedTestUiState.Done(
                    summary = "No AI provider configured. Copy the payload below to analyze manually.",
                    payloadJson = json,
                    hasApi = false,
                )
            }
        }
    }

    /** Reset back to Idle so the user can run another test. */
    fun reset() {
        _uiState.value = GuidedTestUiState.Idle(
            isConnected = obdService.connectionState.value == ConnectionState.CONNECTED
        )
        _stageResults.clear()
        _skipValidation = false
        _cachedVin = null
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildStageResult(run: StageRunResult): StageResult {
        val samples = run.samples
        val rpmValues = samples.filter { it.pid == ObdPid.ENGINE_RPM }.mapNotNull { it.value }

        val pidSummaries = GUIDED_TEST_PIDS.associateWith { pid ->
            val vals = samples.filter { it.pid == pid }.mapNotNull { it.value }
            PidStageSummary(
                pid = pid,
                avg = vals.average().takeIf { !it.isNaN() },
                min = vals.minOrNull(),
                max = vals.maxOrNull(),
                last = vals.lastOrNull(),
                sampleCount = vals.size,
            )
        }

        return StageResult(
            stage = run.stage,
            avgRpm = rpmValues.average().takeIf { !it.isNaN() },
            minRpm = rpmValues.minOrNull(),
            maxRpm = rpmValues.maxOrNull(),
            timeInRangeSec = run.finalTimeInRangeSec,
            pidSummaries = pidSummaries,
        )
    }

    private fun resolveAdapterLabel(): String {
        val profile = obdService.deviceProfile.value ?: return "OBD Adapter"
        return "${profile.deviceName} (${profile.transport})"
    }
}
