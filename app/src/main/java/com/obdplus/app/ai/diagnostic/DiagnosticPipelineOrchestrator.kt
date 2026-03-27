package com.obdplus.app.ai.diagnostic

import com.obdplus.app.ai.SymptomTokenizer
import com.obdplus.app.ai.data.VehicleContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticPipelineOrchestrator @Inject constructor(
    private val knowledgeGraph: DiagnosticKnowledgeGraph,
    private val guidedTestEngine: GuidedTestEngine,
    private val memoryRepository: DiagnosticMemoryRepository,
    private val adaptiveLearning: AdaptiveLearningRepository,
    private val telemetryAnalyzer: TelemetryAnalyzer,
    private val intermittentFaultDetector: IntermittentFaultDetector
) {

    suspend fun run(vehicleCtx: VehicleContext, symptoms: String): DiagnosticPipelineResult {

        // 1. Symptom tokenization
        val symptomTokens = SymptomTokenizer.tokenize(symptoms)

        // 2. PID map
        val pidMap = vehicleCtx.livePidValues.filter { it.value.value != null }

        // 3. Plausibility analysis
        val plausibilityFlags = SensorPlausibilityEngine.analyze(pidMap)

        // 4. Electrical fault detection
        val electricalFlags = ElectricalFaultDetector.analyze(pidMap)

        // 5. DTC codes
        val dtcCodes = (vehicleCtx.storedDtcs + vehicleCtx.pendingDtcs).map { it.code }

        // 6. Fault tree reasoning
        val hypothesisTokens = FaultTreeEngine.reason(dtcCodes, plausibilityFlags, electricalFlags)

        // 7. Knowledge graph tokens
        val knowledgeTokens = knowledgeGraph.getKnowledgeTokens(dtcCodes, symptomTokens)

        // 8. Guided test IDs
        val allFaultSignals = (hypothesisTokens + electricalFlags + plausibilityFlags).distinct()
        val guidedTestIds = guidedTestEngine.getTestIds(allFaultSignals)

        // 9. VIN memory
        val vin = vehicleCtx.vehicleInfo?.vin
        val vinMemory = vin?.takeIf { it.isNotBlank() }?.let { memoryRepository.getMemory(it) }

        // 10. Weight map from adaptive learning for top hypotheses
        val weights = mutableMapOf<String, Float>()
        hypothesisTokens.forEach { token ->
            val adjustment = runCatching {
                adaptiveLearning.getTopConfirmedCauses(token)
                    .firstOrNull()?.second ?: 0f
            }.getOrElse { 0f }
            weights[token] = adjustment
        }

        // 11. Probability scoring
        val probabilities = ProbabilityScoringEngine.score(
            hypothesisTokens  = hypothesisTokens,
            dtcCodes          = dtcCodes,
            plausibilityFlags = plausibilityFlags,
            electricalFlags   = electricalFlags,
            vinMemory         = vinMemory,
            weightAdjustments = weights
        )

        // 12. Telemetry anomalies from background analyzers
        val telemetryAnomalies = (
            telemetryAnalyzer.detectedAnomalies.value +
            intermittentFaultDetector.faultPatterns.value
        ).distinct()

        // 13. Record session in memory
        if (!vin.isNullOrBlank()) {
            runCatching {
                memoryRepository.recordDiagnosticSession(
                    vin        = vin,
                    dtcCodes   = dtcCodes,
                    faultTokens = hypothesisTokens,
                    anomalies  = telemetryAnomalies
                )
            }
        }

        // 14. Return result
        return DiagnosticPipelineResult(
            symptomTokens        = symptomTokens,
            plausibilityFlags    = plausibilityFlags,
            electricalFlags      = electricalFlags,
            hypothesisTokens     = hypothesisTokens,
            knowledgeTokens      = knowledgeTokens,
            guidedTestIds        = guidedTestIds,
            rootCauseProbabilities = probabilities,
            telemetryAnomalies   = telemetryAnomalies
        )
    }
}
