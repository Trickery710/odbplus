package com.odbplus.app.parts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.odbplus.app.parts.data.PartCategory
import com.odbplus.app.parts.data.PartPriority
import com.odbplus.app.parts.data.RecommendedPart
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.partsDataStore: DataStore<Preferences> by preferencesDataStore(name = "parts_data")

/**
 * Repository for managing recommended parts from AI diagnostics.
 */
@Singleton
class PartsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val partsKey = stringPreferencesKey("recommended_parts")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _parts = MutableStateFlow<List<RecommendedPart>>(emptyList())
    val parts: StateFlow<List<RecommendedPart>> = _parts.asStateFlow()

    /**
     * Initialize the repository and load saved parts.
     */
    suspend fun initialize() {
        val preferences = context.partsDataStore.data.first()
        val partsJson = preferences[partsKey]
        if (!partsJson.isNullOrBlank()) {
            try {
                val loadedParts = json.decodeFromString<List<RecommendedPart>>(partsJson)
                _parts.value = loadedParts
            } catch (e: Exception) {
                _parts.value = emptyList()
            }
        }
    }

    /**
     * Add a recommended part from AI diagnostics.
     */
    suspend fun addPart(part: RecommendedPart) {
        val currentParts = _parts.value.toMutableList()

        // Check if similar part already exists (by name and category)
        val existingIndex = currentParts.indexOfFirst {
            it.name.equals(part.name, ignoreCase = true) && it.category == part.category
        }

        if (existingIndex >= 0) {
            // Update existing part with new info
            currentParts[existingIndex] = part.copy(
                id = currentParts[existingIndex].id,
                timestamp = System.currentTimeMillis()
            )
        } else {
            // Add new part
            currentParts.add(0, part.copy(id = UUID.randomUUID().toString()))
        }

        _parts.value = currentParts
        saveParts(currentParts)
    }

    /**
     * Add multiple parts at once.
     */
    suspend fun addParts(newParts: List<RecommendedPart>) {
        newParts.forEach { addPart(it) }
    }

    /**
     * Remove a part from the list.
     */
    suspend fun removePart(partId: String) {
        val currentParts = _parts.value.toMutableList()
        currentParts.removeAll { it.id == partId }
        _parts.value = currentParts
        saveParts(currentParts)
    }

    /**
     * Clear all recommended parts.
     */
    suspend fun clearAllParts() {
        _parts.value = emptyList()
        context.partsDataStore.edit { it.remove(partsKey) }
    }

    /**
     * Get parts filtered by category.
     */
    fun getPartsByCategory(category: PartCategory): List<RecommendedPart> {
        return _parts.value.filter { it.category == category }
    }

    /**
     * Get parts filtered by priority.
     */
    fun getPartsByPriority(priority: PartPriority): List<RecommendedPart> {
        return _parts.value.filter { it.priority == priority }
    }

    /**
     * Get critical/high priority parts that need attention.
     */
    fun getUrgentParts(): List<RecommendedPart> {
        return _parts.value.filter {
            it.priority == PartPriority.CRITICAL || it.priority == PartPriority.HIGH
        }
    }

    /**
     * Parse parts from AI response text.
     * The AI should format parts in a specific way that this parser understands.
     */
    fun parsePartsFromAiResponse(response: String): List<RecommendedPart> {
        val parts = mutableListOf<RecommendedPart>()

        // Look for part recommendations in the format:
        // [PART: name | category | priority | reason]
        // or
        // **Recommended Part:** name
        // - Category: X
        // - Priority: X
        // - Reason: X

        val partPattern = Regex(
            """\[PART:\s*([^|]+)\s*\|\s*([^|]+)\s*\|\s*([^|]+)\s*\|\s*([^\]]+)\]""",
            RegexOption.IGNORE_CASE
        )

        partPattern.findAll(response).forEach { match ->
            val (name, categoryStr, priorityStr, reason) = match.destructured

            val category = PartCategory.entries.find {
                it.displayName.equals(categoryStr.trim(), ignoreCase = true) ||
                        it.name.equals(categoryStr.trim().replace(" ", "_"), ignoreCase = true)
            } ?: PartCategory.OTHER

            val priority = PartPriority.entries.find {
                it.displayName.equals(priorityStr.trim(), ignoreCase = true) ||
                        it.name.equals(priorityStr.trim(), ignoreCase = true)
            } ?: PartPriority.MEDIUM

            parts.add(
                RecommendedPart(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    description = reason.trim(),
                    category = category,
                    priority = priority,
                    reason = reason.trim()
                )
            )
        }

        return parts
    }

    private suspend fun saveParts(parts: List<RecommendedPart>) {
        val partsJson = json.encodeToString(parts)
        context.partsDataStore.edit { preferences ->
            preferences[partsKey] = partsJson
        }
    }
}
