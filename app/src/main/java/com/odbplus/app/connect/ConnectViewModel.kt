package com.odbplus.app.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.core.protocol.TransportRepository   // <-- correct import
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val repo: TransportRepository
) : ViewModel() {
    val isConnected = repo.isConnected

    fun connect() {
        viewModelScope.launch {
            if (!isConnected.value) {
                repo.connect()
                repo.initElmSession()
            }
        }
    }

    fun sendCustom(cmd: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(repo.sendAndAwait(cmd))
        }
    }

    override fun onCleared() {
        viewModelScope.launch { repo.disconnect() }
    }
}
