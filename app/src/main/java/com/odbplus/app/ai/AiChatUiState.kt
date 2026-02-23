package com.odbplus.app.ai

import com.odbplus.app.ai.data.AiProvider
import com.odbplus.app.ai.data.ChatMessage

/**
 * UI state for the AI chat screen.
 */
data class AiChatUiState(
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val hasApiKey: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val showProviderSelector: Boolean = false,
    val selectedProvider: AiProvider = AiProvider.GEMINI,
    val isConnected: Boolean = false,
    val isFetchingVehicleInfo: Boolean = false,
    val currentVin: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val suggestedPrompts: List<String> = emptyList(),
    val errorMessage: String? = null,
    val apiKeyError: String? = null,
    // Google Auth state
    val isGoogleSignedIn: Boolean = false,
    val googleUserEmail: String? = null,
    val googleUserName: String? = null,
    val isGoogleSignInConfigured: Boolean = false,
    val googleSignInError: String? = null
)
