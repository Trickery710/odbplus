package com.odbplus.app.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.tools.data.Tool
import com.odbplus.app.tools.data.ToolCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ToolsUiState(
    val odbAdapters: List<Tool> = emptyList(),
    val multimeters: List<Tool> = emptyList(),
    val testingTools: List<Tool> = emptyList(),
    val aiRecommendedTools: List<Tool> = emptyList(),
    val expandedCategory: ToolCategory? = null
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val toolsRepository: ToolsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    init {
        loadTools()
        observeAiRecommendations()
    }

    private fun loadTools() {
        _uiState.update { state ->
            state.copy(
                odbAdapters = toolsRepository.getOdbAdapters(),
                multimeters = toolsRepository.getMultimeters(),
                testingTools = toolsRepository.getTestingTools()
            )
        }
    }

    private fun observeAiRecommendations() {
        toolsRepository.aiRecommendedTools
            .onEach { tools ->
                _uiState.update { it.copy(aiRecommendedTools = tools) }
            }
            .launchIn(viewModelScope)
    }

    fun toggleCategory(category: ToolCategory) {
        _uiState.update { state ->
            state.copy(
                expandedCategory = if (state.expandedCategory == category) null else category
            )
        }
    }
}
