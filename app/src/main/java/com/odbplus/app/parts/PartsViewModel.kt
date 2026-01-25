package com.odbplus.app.parts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.parts.data.RecommendedPart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PartsViewModel @Inject constructor(
    private val partsRepository: PartsRepository
) : ViewModel() {

    val parts: StateFlow<List<RecommendedPart>> = partsRepository.parts

    init {
        viewModelScope.launch {
            partsRepository.initialize()
        }
    }

    fun removePart(partId: String) {
        viewModelScope.launch {
            partsRepository.removePart(partId)
        }
    }

    fun clearAllParts() {
        viewModelScope.launch {
            partsRepository.clearAllParts()
        }
    }

    fun addPart(part: RecommendedPart) {
        viewModelScope.launch {
            partsRepository.addPart(part)
        }
    }
}
