package com.odbplus.app.ai

import com.odbplus.app.ai.data.AiProvider
import com.odbplus.app.ai.data.ClaudeErrorResponse
import com.odbplus.app.ai.data.ClaudeMessage
import com.odbplus.app.ai.data.ClaudeRequest
import com.odbplus.app.ai.data.ClaudeResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result wrapper for API calls.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

/**
 * Unified response from any AI provider.
 */
data class AiResponse(
    val content: String,
    val provider: AiProvider
)

// ==================== Gemini API Models ====================

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiGenerationConfig(
    val maxOutputTokens: Int = 1024,
    val temperature: Float = 0.7f
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@Serializable
data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)

// ==================== Groq API Models ====================

@Serializable
data class GroqRequest(
    val model: String = "llama-3.1-70b-versatile",
    val messages: List<GroqMessage>,
    val max_tokens: Int = 1024,
    val temperature: Float = 0.7f
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
data class GroqResponse(
    val choices: List<GroqChoice>? = null,
    val error: GroqError? = null
)

@Serializable
data class GroqChoice(
    val message: GroqMessage? = null
)

@Serializable
data class GroqError(
    val message: String,
    val type: String? = null
)

/**
 * Unified service for communicating with multiple AI providers.
 */
@Singleton
class ClaudeApiService @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    companion object {
        // Claude
        private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
        private const val CLAUDE_API_VERSION = "2023-06-01"
        private const val CLAUDE_MODEL = "claude-sonnet-4-20250514"

        // Gemini
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        private const val GEMINI_OAUTH_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

        // Groq
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_MODEL = "llama-3.1-70b-versatile"

        private const val DEFAULT_MAX_TOKENS = 1024
    }

    /**
     * Send a message to the selected AI provider and get a response.
     */
    suspend fun sendMessage(
        provider: AiProvider,
        apiKey: String,
        systemPrompt: String,
        messages: List<ClaudeMessage>,
        useOAuth: Boolean = false
    ): ApiResult<AiResponse> {
        return when (provider) {
            AiProvider.CLAUDE -> sendToClaude(apiKey, systemPrompt, messages)
            AiProvider.GEMINI -> {
                if (useOAuth) {
                    sendToGeminiWithOAuth(apiKey, systemPrompt, messages)
                } else {
                    sendToGemini(apiKey, systemPrompt, messages)
                }
            }
            AiProvider.GROQ -> sendToGroq(apiKey, systemPrompt, messages)
        }
    }

    // ==================== Claude Implementation ====================

    private suspend fun sendToClaude(
        apiKey: String,
        systemPrompt: String,
        messages: List<ClaudeMessage>
    ): ApiResult<AiResponse> {
        return try {
            val request = ClaudeRequest(
                model = CLAUDE_MODEL,
                maxTokens = DEFAULT_MAX_TOKENS,
                system = systemPrompt,
                messages = messages
            )

            val response: HttpResponse = client.post(CLAUDE_API_URL) {
                contentType(ContentType.Application.Json)
                headers {
                    append("x-api-key", apiKey)
                    append("anthropic-version", CLAUDE_API_VERSION)
                }
                setBody(request)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val claudeResponse = response.body<ClaudeResponse>()
                    val content = claudeResponse.content
                        .filter { it.type == "text" }
                        .mapNotNull { it.text }
                        .joinToString("\n")
                    ApiResult.Success(AiResponse(content, AiProvider.CLAUDE))
                }
                HttpStatusCode.Unauthorized -> {
                    ApiResult.Error("Invalid Claude API key. Please check your key.", 401)
                }
                HttpStatusCode.TooManyRequests -> {
                    ApiResult.Error("Claude rate limit exceeded. Please wait and try again.", 429)
                }
                else -> {
                    val errorBody = tryParseClaudeError(response)
                    ApiResult.Error(errorBody ?: "Claude request failed: ${response.status.value}", response.status.value)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(parseNetworkError(e))
        }
    }

    private suspend fun tryParseClaudeError(response: HttpResponse): String? {
        return try {
            val body = response.bodyAsText()
            val errorResponse = json.decodeFromString<ClaudeErrorResponse>(body)
            errorResponse.error.message
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Gemini Implementation ====================

    private suspend fun sendToGemini(
        apiKey: String,
        systemPrompt: String,
        messages: List<ClaudeMessage>
    ): ApiResult<AiResponse> {
        return try {
            // Convert messages to Gemini format
            val geminiContents = messages.map { msg ->
                GeminiContent(
                    role = if (msg.role == "user") "user" else "model",
                    parts = listOf(GeminiPart(msg.content))
                )
            }

            val request = GeminiRequest(
                contents = geminiContents,
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(systemPrompt))),
                generationConfig = GeminiGenerationConfig(maxOutputTokens = DEFAULT_MAX_TOKENS)
            )

            val response: HttpResponse = client.post("$GEMINI_API_URL?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val geminiResponse = response.body<GeminiResponse>()
                    val content = geminiResponse.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text
                        ?: "No response generated"
                    ApiResult.Success(AiResponse(content, AiProvider.GEMINI))
                }
                HttpStatusCode.BadRequest -> {
                    val errorBody = tryParseGeminiError(response)
                    ApiResult.Error(errorBody ?: "Invalid Gemini API key or request.", 400)
                }
                HttpStatusCode.Forbidden -> {
                    ApiResult.Error("Invalid Gemini API key. Please check your key.", 403)
                }
                HttpStatusCode.TooManyRequests -> {
                    ApiResult.Error("Gemini rate limit exceeded. Free tier: 15 req/min. Please wait.", 429)
                }
                else -> {
                    val errorBody = tryParseGeminiError(response)
                    ApiResult.Error(errorBody ?: "Gemini request failed: ${response.status.value}", response.status.value)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(parseNetworkError(e))
        }
    }

    private suspend fun tryParseGeminiError(response: HttpResponse): String? {
        return try {
            val body = response.bodyAsText()
            val errorResponse = json.decodeFromString<GeminiResponse>(body)
            errorResponse.error?.message
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Gemini OAuth Implementation ====================

    /**
     * Send message to Gemini using OAuth (Google Sign-In ID token).
     * Uses the OAuth-enabled endpoint with Bearer token authentication.
     */
    private suspend fun sendToGeminiWithOAuth(
        idToken: String,
        systemPrompt: String,
        messages: List<ClaudeMessage>
    ): ApiResult<AiResponse> {
        return try {
            // Convert messages to Gemini format
            val geminiContents = messages.map { msg ->
                GeminiContent(
                    role = if (msg.role == "user") "user" else "model",
                    parts = listOf(GeminiPart(msg.content))
                )
            }

            val request = GeminiRequest(
                contents = geminiContents,
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(systemPrompt))),
                generationConfig = GeminiGenerationConfig(maxOutputTokens = DEFAULT_MAX_TOKENS)
            )

            // Use OAuth endpoint with Bearer token
            val response: HttpResponse = client.post(GEMINI_OAUTH_API_URL) {
                contentType(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $idToken")
                }
                setBody(request)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val geminiResponse = response.body<GeminiResponse>()
                    val content = geminiResponse.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text
                        ?: "No response generated"
                    ApiResult.Success(AiResponse(content, AiProvider.GEMINI))
                }
                HttpStatusCode.Unauthorized -> {
                    ApiResult.Error("Google Sign-In expired. Please sign in again.", 401)
                }
                HttpStatusCode.Forbidden -> {
                    ApiResult.Error("Access denied. Please sign in with Google again.", 403)
                }
                HttpStatusCode.TooManyRequests -> {
                    ApiResult.Error("Gemini rate limit exceeded. Please wait and try again.", 429)
                }
                else -> {
                    val errorBody = tryParseGeminiError(response)
                    ApiResult.Error(errorBody ?: "Gemini request failed: ${response.status.value}", response.status.value)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(parseNetworkError(e))
        }
    }

    // ==================== Groq Implementation ====================

    private suspend fun sendToGroq(
        apiKey: String,
        systemPrompt: String,
        messages: List<ClaudeMessage>
    ): ApiResult<AiResponse> {
        return try {
            // Convert messages to Groq format (OpenAI-compatible)
            val groqMessages = mutableListOf<GroqMessage>()

            // Add system message
            groqMessages.add(GroqMessage(role = "system", content = systemPrompt))

            // Add conversation messages
            messages.forEach { msg ->
                groqMessages.add(GroqMessage(
                    role = if (msg.role == "user") "user" else "assistant",
                    content = msg.content
                ))
            }

            val request = GroqRequest(
                model = GROQ_MODEL,
                messages = groqMessages,
                max_tokens = DEFAULT_MAX_TOKENS
            )

            val response: HttpResponse = client.post(GROQ_API_URL) {
                contentType(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $apiKey")
                }
                setBody(request)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val groqResponse = response.body<GroqResponse>()
                    val content = groqResponse.choices
                        ?.firstOrNull()
                        ?.message
                        ?.content
                        ?: "No response generated"
                    ApiResult.Success(AiResponse(content, AiProvider.GROQ))
                }
                HttpStatusCode.Unauthorized -> {
                    ApiResult.Error("Invalid Groq API key. Please check your key.", 401)
                }
                HttpStatusCode.TooManyRequests -> {
                    ApiResult.Error("Groq rate limit exceeded. Free tier: 30 req/min. Please wait.", 429)
                }
                else -> {
                    val errorBody = tryParseGroqError(response)
                    ApiResult.Error(errorBody ?: "Groq request failed: ${response.status.value}", response.status.value)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(parseNetworkError(e))
        }
    }

    private suspend fun tryParseGroqError(response: HttpResponse): String? {
        return try {
            val body = response.bodyAsText()
            val errorResponse = json.decodeFromString<GroqResponse>(body)
            errorResponse.error?.message
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Utilities ====================

    private fun parseNetworkError(e: Exception): String {
        return when {
            e.message?.contains("Unable to resolve host") == true ->
                "No internet connection. Please check your network."
            e.message?.contains("timeout") == true ->
                "Request timed out. Please try again."
            else ->
                "Network error: ${e.message ?: "Unknown error"}"
        }
    }

    /**
     * Test the API key for a specific provider.
     */
    suspend fun testApiKey(provider: AiProvider, apiKey: String, useOAuth: Boolean = false): ApiResult<Boolean> {
        val testMessages = listOf(ClaudeMessage(role = "user", content = "Hi"))
        return when (val result = sendMessage(provider, apiKey, "Reply with just 'ok'", testMessages, useOAuth)) {
            is ApiResult.Success -> ApiResult.Success(true)
            is ApiResult.Error -> ApiResult.Error(result.message, result.code)
        }
    }
}
