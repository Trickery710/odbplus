package com.odbplus.app.ai

import com.odbplus.app.ai.data.ClaudeMessage

/**
 * Converts [ClaudeMessage] lists into provider-specific request formats.
 */
internal object MessageConverters {

    fun toGeminiContents(messages: List<ClaudeMessage>): List<GeminiContent> =
        messages.map { msg ->
            GeminiContent(
                role = if (msg.role == "user") "user" else "model",
                parts = listOf(GeminiPart(msg.content))
            )
        }

    fun toGroqMessages(systemPrompt: String, messages: List<ClaudeMessage>): List<GroqMessage> {
        val result = mutableListOf(GroqMessage(role = "system", content = systemPrompt))
        messages.forEach { msg ->
            result.add(GroqMessage(
                role = if (msg.role == "user") "user" else "assistant",
                content = msg.content
            ))
        }
        return result
    }
}
