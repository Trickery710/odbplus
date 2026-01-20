package com.odbplus.app.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.data.ChatMessage
import com.odbplus.app.ai.data.VehicleContext
import com.odbplus.app.diagnostics.DiagnosticsViewModel
import com.odbplus.app.live.LogSessionRepository
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.transport.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the AI chat screen.
 */
data class AiChatUiState(
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val hasApiKey: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val isConnected: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val suggestedPrompts: List<String> = emptyList(),
    val errorMessage: String? = null,
    val apiKeyError: String? = null
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiSettingsRepository: AiSettingsRepository,
    private val chatRepository: ChatRepository,
    private val claudeApiService: ClaudeApiService,
    private val obdService: ObdService,
    private val logSessionRepository: LogSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var currentVehicleContext = VehicleContext()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Initialize chat repository
            chatRepository.initialize()

            // Collect messages
            launch {
                chatRepository.messages.collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                    updateSuggestedPrompts()
                }
            }

            // Collect API key status
            launch {
                aiSettingsRepository.hasApiKey.collect { hasKey ->
                    _uiState.update { it.copy(hasApiKey = hasKey) }
                }
            }

            // Collect connection state
            launch {
                obdService.connectionState.collect { state ->
                    val isConnected = state == ConnectionState.CONNECTED
                    _uiState.update { it.copy(isConnected = isConnected) }
                    updateVehicleContext()
                }
            }

            // Collect live PID values
            launch {
                logSessionRepository.currentPidValues.collect { pidValues ->
                    currentVehicleContext = currentVehicleContext.copy(livePidValues = pidValues)
                    updateSuggestedPrompts()
                }
            }

            // Collect log sessions
            launch {
                logSessionRepository.sessions.collect { sessions ->
                    currentVehicleContext = currentVehicleContext.copy(recentSessions = sessions)
                }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun updateVehicleContext() {
        currentVehicleContext = currentVehicleContext.copy(
            isConnected = _uiState.value.isConnected
        )
    }

    private fun updateSuggestedPrompts() {
        if (_uiState.value.messages.isEmpty()) {
            val prompts = AutomotiveSystemPrompt.getSuggestedPrompts(currentVehicleContext)
            _uiState.update { it.copy(suggestedPrompts = prompts) }
        } else {
            _uiState.update { it.copy(suggestedPrompts = emptyList()) }
        }
    }

    /**
     * Update DTCs from the diagnostics screen.
     */
    fun updateDtcs(
        storedDtcs: List<com.odbplus.core.protocol.DiagnosticTroubleCode>,
        pendingDtcs: List<com.odbplus.core.protocol.DiagnosticTroubleCode>
    ) {
        currentVehicleContext = currentVehicleContext.copy(
            storedDtcs = storedDtcs,
            pendingDtcs = pendingDtcs
        )
        updateSuggestedPrompts()
    }

    /**
     * Send a message to the AI.
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        if (_uiState.value.isSending) return

        viewModelScope.launch {
            // Add user message
            val userMessage = ChatMessage.userMessage(content.trim())
            chatRepository.addMessage(userMessage)

            _uiState.update { it.copy(isSending = true, errorMessage = null) }

            // Get API key
            val apiKey = aiSettingsRepository.apiKey.first()
            if (apiKey == null) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        showApiKeyDialog = true
                    )
                }
                return@launch
            }

            // Prepare messages for API
            val allMessages = chatRepository.getCurrentMessages()
            val claudeMessages = allMessages.map { it.toClaudeMessage() }

            // Generate system prompt with vehicle context
            val systemPrompt = AutomotiveSystemPrompt.generate(currentVehicleContext)

            // Send to Claude API
            when (val result = claudeApiService.sendMessage(apiKey, systemPrompt, claudeMessages)) {
                is ApiResult.Success -> {
                    val responseText = result.data.content
                        .filter { it.type == "text" }
                        .mapNotNull { it.text }
                        .joinToString("\n")

                    if (responseText.isNotBlank()) {
                        val assistantMessage = ChatMessage.assistantMessage(responseText)
                        chatRepository.addMessage(assistantMessage)
                    }

                    _uiState.update { it.copy(isSending = false) }
                }
                is ApiResult.Error -> {
                    val errorMessage = ChatMessage.errorMessage(result.message)
                    chatRepository.addMessage(errorMessage)

                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Show the API key dialog.
     */
    fun showApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = true, apiKeyError = null) }
    }

    /**
     * Hide the API key dialog.
     */
    fun hideApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = false, apiKeyError = null) }
    }

    /**
     * Save the API key.
     */
    fun saveApiKey(key: String) {
        viewModelScope.launch {
            if (!aiSettingsRepository.isValidApiKeyFormat(key)) {
                _uiState.update {
                    it.copy(apiKeyError = "Invalid API key format. Key should start with 'sk-ant-'")
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, apiKeyError = null) }

            // Test the API key
            when (val result = claudeApiService.testApiKey(key.trim())) {
                is ApiResult.Success -> {
                    aiSettingsRepository.saveApiKey(key.trim())
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showApiKeyDialog = false,
                            hasApiKey = true
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            apiKeyError = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Clear the API key.
     */
    fun clearApiKey() {
        viewModelScope.launch {
            aiSettingsRepository.clearApiKey()
            _uiState.update { it.copy(hasApiKey = false) }
        }
    }

    /**
     * Clear chat history.
     */
    fun clearHistory() {
        viewModelScope.launch {
            chatRepository.clearHistory()
            updateSuggestedPrompts()
        }
    }

    /**
     * Dismiss error message.
     */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Retry the last failed message.
     */
    fun retryLastMessage() {
        val messages = _uiState.value.messages
        val lastUserMessage = messages.lastOrNull { it.role == com.odbplus.app.ai.data.MessageRole.USER }
        if (lastUserMessage != null) {
            // Remove the error message if present
            viewModelScope.launch {
                val lastMessage = messages.lastOrNull()
                if (lastMessage?.isError == true) {
                    // Clear and re-add without the error
                    chatRepository.clearHistory()
                    val messagesWithoutError = messages.dropLast(1)
                    chatRepository.addMessages(messagesWithoutError)
                }
                // Resend the last user message
                sendMessage(lastUserMessage.content)
            }
        }
    }
}
