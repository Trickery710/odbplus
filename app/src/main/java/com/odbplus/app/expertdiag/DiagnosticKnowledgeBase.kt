package com.odbplus.app.expertdiag

import android.content.Context
import com.odbplus.app.expertdiag.model.DtcSeverity
import com.odbplus.app.expertdiag.model.KnowledgeBaseEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and indexes the OBD diagnostic knowledge base from
 * `assets/obd_diagnostic_knowledgebase.csv`.
 *
 * The CSV columns are:
 *   number, dtc, description, severity, common_causes,
 *   automatic_tests, guided_tests, monitored_pids
 *
 * Semicolon-separated lists within fields are split into proper [List]s.
 * The index is built at construction and never changes — treat as immutable
 * after [load].
 */
@Singleton
class DiagnosticKnowledgeBase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val byCode = mutableMapOf<String, KnowledgeBaseEntry>()

    val entries: Collection<KnowledgeBaseEntry> get() = byCode.values

    /** Number of entries successfully loaded. */
    var loadedCount: Int = 0
        private set

    init {
        load()
    }

    /**
     * Look up an entry by DTC code string, e.g. "P0100".
     * Returns null if the code is not in the knowledge base.
     */
    fun lookup(dtcCode: String): KnowledgeBaseEntry? = byCode[dtcCode.uppercase().trim()]

    // ── Private parsing ───────────────────────────────────────────────────

    private fun load() {
        try {
            context.assets.open("obd_diagnostic_knowledgebase.csv").bufferedReader().use { reader ->
                var lineNum = 0
                reader.forEachLine { rawLine ->
                    lineNum++
                    if (lineNum == 1) return@forEachLine // skip header
                    parseLine(rawLine)?.let { entry ->
                        byCode[entry.dtc.uppercase()] = entry
                    }
                }
            }
            loadedCount = byCode.size
            Timber.d("DiagnosticKnowledgeBase: loaded $loadedCount entries")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load diagnostic knowledge base")
        }
    }

    private fun parseLine(line: String): KnowledgeBaseEntry? {
        if (line.isBlank()) return null
        val cols = line.split(",")
        if (cols.size < 8) return null
        return try {
            KnowledgeBaseEntry(
                number = cols[0].trim().toIntOrNull() ?: 0,
                dtc = cols[1].trim(),
                description = cols[2].trim(),
                severity = DtcSeverity.from(cols[3].trim()),
                commonCauses = cols[4].trim().split(";").map { it.trim() }.filter { it.isNotBlank() },
                automaticTests = cols[5].trim().split(";").map { it.trim() }.filter { it.isNotBlank() },
                guidedTests = cols[6].trim().split(";").map { it.trim() }.filter { it.isNotBlank() },
                monitoredPids = cols[7].trim().split(";").map { it.trim() }.filter { it.isNotBlank() },
            )
        } catch (e: Exception) {
            Timber.w("KnowledgeBase: failed to parse line: $line")
            null
        }
    }
}
