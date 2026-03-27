package com.obdplus.app.ai.diagnostic

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticKnowledgeGraph @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class DtcEntry(
        val code: String,
        val description: String,
        val system: String,
        val subsystem: String,
        val severity: String,
        val relatedPids: List<String>,
        val relatedModules: List<String>,
        val knowledgeTokens: List<String>,
        val faultTokens: List<String>
    )

    data class PidEntry(
        val code: String,
        val name: String,
        val shortName: String,
        val unit: String,
        val minNormal: Float,
        val maxNormal: Float,
        val criticalLow: Float,
        val criticalHigh: Float
    )

    data class SensorRelation(
        val pidA: String,
        val pidB: String,
        val relationship: String,
        val ruleDescription: String,
        val threshold: String
    )

    data class WiringCircuit(
        val circuitId: String,
        val circuitName: String,
        val function: String,
        val pidCodes: List<String>,
        val modules: List<String>
    )

    data class ModuleNode(
        val moduleId: String,
        val displayName: String,
        val protocol: String,
        val bus: String,
        val canAddress: String,
        val dependencies: List<String>,
        val function: String
    )

    // Lazy-loaded data
    private val dtcMap: Map<String, DtcEntry> by lazy { loadDtcMaster() }
    private val pidMap: Map<String, PidEntry> by lazy { loadPidMaster() }
    private val sensorRelations: List<SensorRelation> by lazy { loadSensorRelationships() }
    private val wiringCircuits: Map<String, WiringCircuit> by lazy { loadWiringGraph() }
    private val moduleNodes: Map<String, ModuleNode> by lazy { loadModuleNetworks() }

    // ── Public API ────────────────────────────────────────────────────────────

    fun getKnowledgeTokens(dtcCodes: List<String>, symptomTokens: String): List<String> {
        val tokens = mutableListOf<String>()
        dtcCodes.forEach { code ->
            dtcMap[code.uppercase()]?.knowledgeTokens?.let { tokens.addAll(it) }
        }
        return tokens.distinct()
    }

    fun getFaultTokens(dtcCodes: List<String>): List<String> {
        val tokens = mutableListOf<String>()
        dtcCodes.forEach { code ->
            dtcMap[code.uppercase()]?.faultTokens?.let { tokens.addAll(it) }
        }
        return tokens.distinct()
    }

    fun getRelatedPids(dtcCode: String): List<String> =
        dtcMap[dtcCode.uppercase()]?.relatedPids ?: emptyList()

    fun getCircuitsForPid(pidCode: String): List<String> =
        wiringCircuits.values
            .filter { pidCode.uppercase() in it.pidCodes.map { p -> p.uppercase() } }
            .map { it.circuitId }

    fun getModuleForDtc(dtcCode: String): List<String> =
        dtcMap[dtcCode.uppercase()]?.relatedModules ?: emptyList()

    // ── CSV Loaders ──────────────────────────────────────────────────────────

    private fun loadDtcMaster(): Map<String, DtcEntry> {
        val map = mutableMapOf<String, DtcEntry>()
        try {
            context.assets.open("data/dtc_master.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.split(",", limit = 9)
                    if (cols.size < 9) return@forEach
                    val entry = DtcEntry(
                        code            = cols[0].trim(),
                        description     = cols[1].trim(),
                        system          = cols[2].trim(),
                        subsystem       = cols[3].trim(),
                        severity        = cols[4].trim(),
                        relatedPids     = cols[5].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() },
                        relatedModules  = cols[6].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() },
                        knowledgeTokens = cols[7].trim().split(" ").map { it.trim() }.filter { it.isNotEmpty() },
                        faultTokens     = cols[8].trim().split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                    )
                    map[entry.code.uppercase()] = entry
                }
            }
        } catch (_: Exception) { /* Return empty map on asset read failure */ }
        return map
    }

    private fun loadPidMaster(): Map<String, PidEntry> {
        val map = mutableMapOf<String, PidEntry>()
        try {
            context.assets.open("data/pid_master.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.split(",", limit = 8)
                    if (cols.size < 8) return@forEach
                    val entry = PidEntry(
                        code        = cols[0].trim(),
                        name        = cols[1].trim(),
                        shortName   = cols[2].trim(),
                        unit        = cols[3].trim(),
                        minNormal   = cols[4].trim().toFloatOrNull() ?: 0f,
                        maxNormal   = cols[5].trim().toFloatOrNull() ?: 0f,
                        criticalLow = cols[6].trim().toFloatOrNull() ?: 0f,
                        criticalHigh = cols[7].trim().toFloatOrNull() ?: 0f
                    )
                    map[entry.code.uppercase()] = entry
                }
            }
        } catch (_: Exception) { }
        return map
    }

    private fun loadSensorRelationships(): List<SensorRelation> {
        val list = mutableListOf<SensorRelation>()
        try {
            context.assets.open("data/sensor_relationships.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.split(",", limit = 5)
                    if (cols.size < 5) return@forEach
                    list += SensorRelation(
                        pidA            = cols[0].trim(),
                        pidB            = cols[1].trim(),
                        relationship    = cols[2].trim(),
                        ruleDescription = cols[3].trim(),
                        threshold       = cols[4].trim()
                    )
                }
            }
        } catch (_: Exception) { }
        return list
    }

    private fun loadWiringGraph(): Map<String, WiringCircuit> {
        val map = mutableMapOf<String, WiringCircuit>()
        try {
            context.assets.open("data/wiring_graph.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.split(",", limit = 5)
                    if (cols.size < 5) return@forEach
                    val circuit = WiringCircuit(
                        circuitId   = cols[0].trim(),
                        circuitName = cols[1].trim(),
                        function    = cols[2].trim(),
                        pidCodes    = cols[3].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() },
                        modules     = cols[4].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    )
                    map[circuit.circuitId] = circuit
                }
            }
        } catch (_: Exception) { }
        return map
    }

    private fun loadModuleNetworks(): Map<String, ModuleNode> {
        val map = mutableMapOf<String, ModuleNode>()
        try {
            context.assets.open("data/module_networks.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.split(",", limit = 7)
                    if (cols.size < 7) return@forEach
                    val node = ModuleNode(
                        moduleId    = cols[0].trim(),
                        displayName = cols[1].trim(),
                        protocol    = cols[2].trim(),
                        bus         = cols[3].trim(),
                        canAddress  = cols[4].trim(),
                        dependencies = cols[5].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() },
                        function    = cols[6].trim()
                    )
                    map[node.moduleId] = node
                }
            }
        } catch (_: Exception) { }
        return map
    }
}
