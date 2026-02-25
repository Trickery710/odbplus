package com.odbplus.core.protocol.diagnostic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-process ring-buffer diagnostic logger for UOAPL events.
 *
 * Use [d], [i], [w], [e] to append entries from any layer.
 * Use [exportText] to get a plain-text snapshot for file export.
 *
 * Thread-safe — all mutations and reads are synchronized on [entries].
 */
object DiagnosticLogger {

    private const val MAX_ENTRIES = 2000

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
    )

    private val entries = ArrayDeque<Entry>()

    // ── Public logging API ────────────────────────────────────────────────────

    fun d(tag: String, message: String) = add(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = add(Level.INFO,  tag, message)
    fun w(tag: String, message: String) = add(Level.WARN,  tag, message)
    fun e(tag: String, message: String) = add(Level.ERROR, tag, message)

    private fun add(level: Level, tag: String, message: String) {
        synchronized(entries) {
            entries.addLast(Entry(System.currentTimeMillis(), level, tag, message))
            if (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
    }

    /** Immutable snapshot of all current entries. */
    fun snapshot(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }

    /**
     * Formats all entries as a plain-text string ready for file export or clipboard.
     */
    fun exportText(): String {
        val snap = snapshot()
        val timeFmt  = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val dateFmt  = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            appendLine("ODBplus Diagnostic Log")
            appendLine("Generated : ${dateFmt.format(Date())}")
            appendLine("Entries   : ${snap.size}")
            appendLine("=".repeat(64))
            for (entry in snap) {
                val time = timeFmt.format(Date(entry.timestamp))
                appendLine("[$time] ${entry.level.name.padEnd(5)} [${entry.tag}] ${entry.message}")
            }
        }
    }
}
