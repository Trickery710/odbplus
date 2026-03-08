package com.odbplus.app.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.toDomain
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.app.data.db.dao.DtcLogDao
import com.odbplus.app.data.db.dao.VehicleDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class VehicleHistoryViewModel @Inject constructor(
    vehicleDao: VehicleDao,
    val dtcLogDao: DtcLogDao
) : ViewModel() {

    val vehicles: StateFlow<List<VehicleInfo>> = vehicleDao
        .getAllFlow()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
