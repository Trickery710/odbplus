package com.odbplus.app.ai.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Claude API request model.
 */
@Serializable
data class ClaudeRequest(
    val model: String = "claude-sonnet-4-20250514",
    @SerialName("max_tokens")
    val maxTokens: Int = 1024,
    val system: String? = null,
    val messages: List<ClaudeMessage>
)

/**
 * Claude API message model.
 */
@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String
)

/**
 * Claude API response model.
 */
@Serializable
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContent>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    @SerialName("stop_sequence")
    val stopSequence: String? = null,
    val usage: ClaudeUsage? = null
)

/**
 * Claude API content block.
 */
@Serializable
data class ClaudeContent(
    val type: String,
    val text: String? = null
)

/**
 * Claude API usage stats.
 */
@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)

/**
 * Claude API error response.
 */
@Serializable
data class ClaudeErrorResponse(
    val type: String,
    val error: ClaudeError
)

/**
 * Claude API error details.
 */
@Serializable
data class ClaudeError(
    val type: String,
    val message: String
)
