package com.odbplus.app.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: LogSessionRepository
) : ViewModel() {

    val sessions: StateFlow<List<LogSession>> = repository.sessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            try {
                repository.initialize()
            } catch (e: Exception) {
                // Ignore initialization errors
            }
        }
    }

    fun deleteSession(session: LogSession) {
        viewModelScope.launch {
            try {
                repository.deleteSession(session.id)
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            try {
                repository.clearAllSessions()
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }
}
