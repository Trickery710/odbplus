package com.odbplus.app.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.VehicleInfoRepository
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.app.data.db.dao.DtcLogDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehicleHistoryViewModel @Inject constructor(
    private val vehicleInfoRepository: VehicleInfoRepository,
    val dtcLogDao: DtcLogDao
) : ViewModel() {

    private val _vehicles = MutableStateFlow<List<VehicleInfo>>(emptyList())
    val vehicles: StateFlow<List<VehicleInfo>> = _vehicles.asStateFlow()

    init {
        viewModelScope.launch {
            _vehicles.value = vehicleInfoRepository.getAllVehicles()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _vehicles.value = vehicleInfoRepository.getAllVehicles()
        }
    }
}
