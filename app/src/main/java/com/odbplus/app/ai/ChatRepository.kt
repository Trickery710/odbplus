package com.odbplus.app.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.odbplus.app.ai.data.ChatMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chatDataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_history")

/**
 * Repository for persisting chat history.
 */
@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val messagesKey = stringPreferencesKey("chat_messages")

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /**
     * Maximum number of messages to persist.
     */
    private val maxMessages = 50

    /**
     * Initialize the repository and load persisted messages.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val prefs = context.chatDataStore.data.first()
            val messagesJson = prefs[messagesKey] ?: return@withContext
            val loaded = json.decodeFromString<List<ChatMessage>>(messagesJson)
            _messages.value = loaded.takeLast(maxMessages)
        } catch (e: Exception) {
            _messages.value = emptyList()
        }
    }

    /**
     * Add a message to the chat history.
     */
    suspend fun addMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        val updated = (_messages.value + message).takeLast(maxMessages)
        _messages.value = updated
        persistMessages(updated)
    }

    /**
     * Add multiple messages to the chat history.
     */
    suspend fun addMessages(newMessages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        val updated = (_messages.value + newMessages).takeLast(maxMessages)
        _messages.value = updated
        persistMessages(updated)
    }

    /**
     * Clear all chat history.
     */
    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        _messages.value = emptyList()
        context.chatDataStore.edit { prefs ->
            prefs.remove(messagesKey)
        }
    }

    /**
     * Get the current messages as a list for API calls.
     */
    fun getCurrentMessages(): List<ChatMessage> = _messages.value

    private suspend fun persistMessages(messages: List<ChatMessage>) {
        try {
            context.chatDataStore.edit { prefs ->
                prefs[messagesKey] = json.encodeToString(messages)
            }
        } catch (e: Exception) {
            // Ignore persistence errors
        }
    }
}
