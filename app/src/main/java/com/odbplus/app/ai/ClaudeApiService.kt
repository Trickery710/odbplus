package com.odbplus.app.ai

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
 * Service for communicating with the Claude API.
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
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val DEFAULT_MODEL = "claude-sonnet-4-20250514"
        private const val DEFAULT_MAX_TOKENS = 1024
    }

    /**
     * Send a message to Claude and get a response.
     *
     * @param apiKey The Claude API key
     * @param systemPrompt The system prompt for context
     * @param messages The conversation history
     * @return The API result containing the response or error
     */
    suspend fun sendMessage(
        apiKey: String,
        systemPrompt: String,
        messages: List<ClaudeMessage>
    ): ApiResult<ClaudeResponse> {
        return try {
            val request = ClaudeRequest(
                model = DEFAULT_MODEL,
                maxTokens = DEFAULT_MAX_TOKENS,
                system = systemPrompt,
                messages = messages
            )

            val response: HttpResponse = client.post(API_URL) {
                contentType(ContentType.Application.Json)
                headers {
                    append("x-api-key", apiKey)
                    append("anthropic-version", API_VERSION)
                }
                setBody(request)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val claudeResponse = response.body<ClaudeResponse>()
                    ApiResult.Success(claudeResponse)
                }
                HttpStatusCode.Unauthorized -> {
                    ApiResult.Error("Invalid API key. Please check your key in settings.", 401)
                }
                HttpStatusCode.TooManyRequests -> {
                    ApiResult.Error("Rate limit exceeded. Please wait a moment and try again.", 429)
                }
                HttpStatusCode.BadRequest -> {
                    val errorBody = tryParseError(response)
                    ApiResult.Error(errorBody ?: "Bad request. Please try again.", 400)
                }
                else -> {
                    val errorBody = tryParseError(response)
                    ApiResult.Error(
                        errorBody ?: "Request failed with status ${response.status.value}",
                        response.status.value
                    )
                }
            }
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("Unable to resolve host") == true ->
                    "No internet connection. Please check your network."
                e.message?.contains("timeout") == true ->
                    "Request timed out. Please try again."
                else ->
                    "Network error: ${e.message ?: "Unknown error"}"
            }
            ApiResult.Error(message)
        }
    }

    private suspend fun tryParseError(response: HttpResponse): String? {
        return try {
            val body = response.bodyAsText()
            val errorResponse = json.decodeFromString<ClaudeErrorResponse>(body)
            errorResponse.error.message
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Test the API key by making a minimal request.
     */
    suspend fun testApiKey(apiKey: String): ApiResult<Boolean> {
        val testMessages = listOf(ClaudeMessage(role = "user", content = "Hi"))
        return when (val result = sendMessage(apiKey, "Reply with 'ok'", testMessages)) {
            is ApiResult.Success -> ApiResult.Success(true)
            is ApiResult.Error -> ApiResult.Error(result.message, result.code)
        }
    }
}
