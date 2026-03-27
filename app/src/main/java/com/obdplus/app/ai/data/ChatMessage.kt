package com.obdplus.app.ai.data

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
 *
 * [content] is what is sent to the AI API.
 * [displayContent] is what is shown in the UI; falls back to [content] when null.
 * This allows the diagnostic bootstrap to send a rich context prompt to the AI
 * while showing only the user's typed symptoms in the chat thread.
 */
@Serializable
data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val role: MessageRole,
    val content: String,
    val displayContent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
) {
    /** Text shown in the chat UI. */
    val uiContent: String get() = displayContent ?: content

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
