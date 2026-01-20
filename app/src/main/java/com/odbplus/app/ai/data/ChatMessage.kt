package com.odbplus.app.ai.data

import kotlinx.serialization.Serializable

/**
 * Message role in the chat conversation.
 */
enum class MessageRole {
    USER,
    ASSISTANT
}

/**
 * Local chat message model for UI and persistence.
 */
@Serializable
data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
) {
    /**
     * Convert to Claude API message format.
     */
    fun toClaudeMessage(): ClaudeMessage = ClaudeMessage(
        role = when (role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
        },
        content = content
    )

    companion object {
        fun userMessage(content: String): ChatMessage = ChatMessage(
            role = MessageRole.USER,
            content = content
        )

        fun assistantMessage(content: String): ChatMessage = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = content
        )

        fun errorMessage(content: String): ChatMessage = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = content,
            isError = true
        )
    }
}
