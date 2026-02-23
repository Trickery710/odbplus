package com.odbplus.app.ai

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

/**
 * Centralises HTTP-status-code → error-message logic shared across all provider implementations.
 */
internal object ApiErrorHandler {

    suspend fun handleHttpError(
        response: HttpResponse,
        provider: String,
        parseBody: suspend (HttpResponse) -> String?
    ): ApiResult.Error {
        val code = response.status.value
        val providerLabel = provider.replaceFirstChar { it.uppercaseChar() }
        return when (response.status) {
            HttpStatusCode.Unauthorized ->
                ApiResult.Error("Invalid $providerLabel API key. Please check your key.", 401)
            HttpStatusCode.Forbidden ->
                ApiResult.Error("Access denied for $providerLabel. Please check your credentials.", 403)
            HttpStatusCode.TooManyRequests ->
                ApiResult.Error("$providerLabel rate limit exceeded. Please wait and try again.", 429)
            HttpStatusCode.BadRequest -> {
                val body = parseBody(response)
                ApiResult.Error(body ?: "Invalid $providerLabel request.", 400)
            }
            else -> {
                val body = parseBody(response)
                ApiResult.Error(body ?: "$providerLabel request failed: $code", code)
            }
        }
    }

    fun parseNetworkError(e: Exception): String = when {
        e.message?.contains("Unable to resolve host") == true ->
            "No internet connection. Please check your network."
        e.message?.contains("timeout") == true ->
            "Request timed out. Please try again."
        else ->
            "Network error: ${e.message ?: "Unknown error"}"
    }
}
