package com.odbplus.app.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.data.AiProvider
import com.odbplus.app.ai.data.ChatMessage
import com.odbplus.app.parts.PartsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiSettingsRepository: AiSettingsRepository,
    private val chatRepository: ChatRepository,
    private val claudeApiService: ClaudeApiService,
    private val vehicleContextProvider: VehicleContextProvider,
    private val partsRepository: PartsRepository,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isGoogleSignInConfigured = googleAuthManager.isConfigured()
                )
            }

            // Start all collectors immediately — they don't need DataStore to have loaded yet.
            // This means the UI can process cached/default values while I/O is in flight.
            launch {
                chatRepository.messages.collect { messages ->
                    val hasMessages = messages.isNotEmpty()
                    val prompts = if (!hasMessages) {
                        AutomotiveSystemPrompt.getSuggestedPrompts(vehicleContextProvider.current())
                    } else emptyList()
                    _uiState.update { it.copy(messages = messages, suggestedPrompts = prompts) }
                }
            }

            launch {
                aiSettingsRepository.selectedProvider.collect { provider ->
                    _uiState.update { it.copy(selectedProvider = provider) }
                }
            }

            launch {
                aiSettingsRepository.hasApiKey.collect { hasKey ->
                    _uiState.update { it.copy(hasApiKey = hasKey) }
                }
            }

            launch {
                googleAuthManager.authState.collect { authState ->
                    _uiState.update {
                        it.copy(
                            isGoogleSignedIn = authState.isSignedIn,
                            googleUserEmail = authState.userEmail,
                            googleUserName = authState.userName
                        )
                    }
                    if (authState.isSignedIn && _uiState.value.selectedProvider == AiProvider.GEMINI) {
                        _uiState.update { it.copy(hasApiKey = true) }
                    }
                }
            }

            // Mirror vehicle-context fields that the UI needs
            launch {
                vehicleContextProvider.context.collect { ctx ->
                    val prompts = if (_uiState.value.messages.isEmpty()) {
                        AutomotiveSystemPrompt.getSuggestedPrompts(ctx)
                    } else emptyList()
                    _uiState.update {
                        it.copy(
                            isConnected = ctx.isConnected,
                            isFetchingVehicleInfo = ctx.isFetchingVehicleInfo,
                            currentVin = ctx.vehicleInfo?.vin,
                            suggestedPrompts = prompts
                        )
                    }
                }
            }

            // Run both DataStore reads in parallel — chat_history and ai_settings are separate
            // files so they can be read concurrently with no contention.
            val chatInitJob = async(Dispatchers.IO) { chatRepository.initialize() }
            val googleAuthJob = async(Dispatchers.IO) { aiSettingsRepository.getSavedGoogleAuth() }

            chatInitJob.await()
            val (savedToken, savedEmail, savedName) = googleAuthJob.await()

            if (!savedToken.isNullOrBlank()) {
                googleAuthManager.restoreAuthState(savedToken, savedEmail, savedName)
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Update DTCs from the diagnostics screen.
     * Delegates to [VehicleContextProvider] via the shared state — callers should use
     * [VehicleContextViewModel.updateDtcs] where possible.
     */
    fun updateDtcs(
        storedDtcs: List<com.odbplus.core.protocol.DiagnosticTroubleCode>,
        pendingDtcs: List<com.odbplus.core.protocol.DiagnosticTroubleCode>
    ) {
        vehicleContextProvider.update(
            vehicleContextProvider.current().copy(
                storedDtcs = storedDtcs,
                pendingDtcs = pendingDtcs
            )
        )
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isSending) return

        viewModelScope.launch {
            val userMessage = ChatMessage.userMessage(content.trim())
            chatRepository.addMessage(userMessage)
            _uiState.update { it.copy(isSending = true, errorMessage = null) }

            val provider = _uiState.value.selectedProvider
            val useGoogleAuth = provider == AiProvider.GEMINI && _uiState.value.isGoogleSignedIn
            val googleToken = if (useGoogleAuth) googleAuthManager.authState.value.idToken else null

            val apiKey = if (useGoogleAuth && !googleToken.isNullOrBlank()) {
                googleToken
            } else {
                aiSettingsRepository.getApiKeyForProvider(provider).first()
            }

            if (apiKey == null) {
                _uiState.update { it.copy(isSending = false, showApiKeyDialog = true) }
                return@launch
            }

            val systemPrompt = AutomotiveSystemPrompt.generate(vehicleContextProvider.current())
            val claudeMessages = chatRepository.getCurrentMessages().map { it.toClaudeMessage() }

            when (val result = claudeApiService.sendMessage(
                provider = provider,
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                messages = claudeMessages,
                useOAuth = useGoogleAuth
            )) {
                is ApiResult.Success -> {
                    val text = result.data.content
                    if (text.isNotBlank()) {
                        chatRepository.addMessage(ChatMessage.assistantMessage(text))
                        val parts = partsRepository.parsePartsFromAiResponse(text)
                        if (parts.isNotEmpty()) partsRepository.addParts(parts)
                    }
                    _uiState.update { it.copy(isSending = false) }
                }
                is ApiResult.Error -> {
                    if (useGoogleAuth && (result.code == 401 || result.code == 403)) googleSignOut()
                    chatRepository.addMessage(ChatMessage.errorMessage(result.message))
                    _uiState.update { it.copy(isSending = false, errorMessage = result.message) }
                }
            }
        }
    }

    suspend fun googleSignIn(activityContext: android.content.Context): GoogleSignInResult {
        _uiState.update { it.copy(isLoading = true, googleSignInError = null) }
        val result = googleAuthManager.signIn(activityContext)
        when (result) {
            is GoogleSignInResult.Success -> {
                result.state.idToken?.let { token ->
                    aiSettingsRepository.saveGoogleAuth(
                        idToken = token,
                        email = result.state.userEmail,
                        name = result.state.userName
                    )
                }
                _uiState.update { it.copy(isLoading = false, hasApiKey = true, showApiKeyDialog = false) }
            }
            is GoogleSignInResult.Error ->
                _uiState.update { it.copy(isLoading = false, googleSignInError = result.message) }
            GoogleSignInResult.Cancelled ->
                _uiState.update { it.copy(isLoading = false) }
        }
        return result
    }

    fun googleSignOut() {
        viewModelScope.launch {
            googleAuthManager.signOut()
            aiSettingsRepository.clearGoogleAuth()
            _uiState.update { it.copy(isGoogleSignedIn = false, googleUserEmail = null, googleUserName = null) }
            val hasManualKey = aiSettingsRepository.getApiKeyForProvider(AiProvider.GEMINI).first() != null
            _uiState.update { it.copy(hasApiKey = hasManualKey) }
        }
    }

    fun showApiKeyDialog() { _uiState.update { it.copy(showApiKeyDialog = true, apiKeyError = null) } }
    fun hideApiKeyDialog() { _uiState.update { it.copy(showApiKeyDialog = false, apiKeyError = null) } }
    fun showProviderSelector() { _uiState.update { it.copy(showProviderSelector = true) } }
    fun hideProviderSelector() { _uiState.update { it.copy(showProviderSelector = false) } }

    fun selectProvider(provider: AiProvider) {
        viewModelScope.launch {
            aiSettingsRepository.saveSelectedProvider(provider)
            _uiState.update { it.copy(showProviderSelector = false) }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            val provider = _uiState.value.selectedProvider
            if (!aiSettingsRepository.isValidApiKeyFormat(provider, key)) {
                _uiState.update {
                    it.copy(apiKeyError = "Invalid API key format. Key should start with '${provider.keyPrefix}'")
                }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, apiKeyError = null) }
            when (val result = claudeApiService.testApiKey(provider, key.trim())) {
                is ApiResult.Success -> {
                    aiSettingsRepository.saveApiKey(provider, key.trim())
                    _uiState.update { it.copy(isLoading = false, showApiKeyDialog = false, hasApiKey = true) }
                }
                is ApiResult.Error ->
                    _uiState.update { it.copy(isLoading = false, apiKeyError = result.message) }
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            aiSettingsRepository.clearApiKey(_uiState.value.selectedProvider)
            _uiState.update { it.copy(hasApiKey = false) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { chatRepository.clearHistory() }
    }

    fun dismissError() { _uiState.update { it.copy(errorMessage = null) } }

    fun retryLastMessage() {
        val messages = _uiState.value.messages
        val lastUser = messages.lastOrNull { it.role == com.odbplus.app.ai.data.MessageRole.USER }
        if (lastUser != null) {
            viewModelScope.launch {
                if (messages.lastOrNull()?.isError == true) {
                    chatRepository.clearHistory()
                    chatRepository.addMessages(messages.dropLast(1))
                }
                sendMessage(lastUser.content)
            }
        }
    }
}
