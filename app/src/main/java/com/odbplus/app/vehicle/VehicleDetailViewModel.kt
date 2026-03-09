package com.odbplus.app.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.VehicleInfoRepository
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.app.expertdiag.DiagnosticKnowledgeBase
import com.odbplus.app.expertdiag.model.KnowledgeBaseEntry
import com.odbplus.app.data.db.dao.DtcLogDao
import com.odbplus.app.data.db.dao.EcuModuleDao
import com.odbplus.app.data.db.dao.FreezeFrameDao
import com.odbplus.app.data.db.dao.TestResultDao
import com.odbplus.app.data.db.dao.VehicleSessionDao
import com.odbplus.app.data.db.entity.DtcLogEntity
import com.odbplus.app.data.db.entity.EcuModuleEntity
import com.odbplus.app.data.db.entity.FreezeFrameEntity
import com.odbplus.app.data.db.entity.TestResultEntity
import com.odbplus.app.data.db.entity.VehicleSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    private val vehicleInfoRepository: VehicleInfoRepository,
    private val sessionDao: VehicleSessionDao,
    private val dtcLogDao: DtcLogDao,
    private val freezeFrameDao: FreezeFrameDao,
    private val ecuModuleDao: EcuModuleDao,
    private val testResultDao: TestResultDao,
    private val knowledgeBase: DiagnosticKnowledgeBase,
    savedState: SavedStateHandle
) : ViewModel() {

    val vin: String = savedState.get<String>("vin") ?: ""

    private val _vehicle = MutableStateFlow<VehicleInfo?>(null)
    val vehicle: StateFlow<VehicleInfo?> = _vehicle.asStateFlow()

    val sessions: StateFlow<List<VehicleSessionEntity>> = sessionDao
        .getSessionsForVin(vin)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dtcs: StateFlow<List<DtcLogEntity>> = dtcLogDao
        .getDtcsForVin(vin)
        .map { list -> list.distinctBy { it.dtcCode } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val freezeFrames: StateFlow<List<FreezeFrameEntity>> = freezeFrameDao
        .getFramesForVin(vin)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ecuModules: StateFlow<List<EcuModuleEntity>> = ecuModuleDao
        .getModulesForVin(vin)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val testResults: StateFlow<List<TestResultEntity>> = testResultDao
        .getResultsForVin(vin)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val healthScore: StateFlow<Int> = combine(dtcs, testResults) { dtcList, results ->
        calculateHealthScore(dtcList, results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    init {
        viewModelScope.launch {
            _vehicle.value = vehicleInfoRepository.getVehicle(vin)
        }
    }

    fun kbEntry(dtcCode: String): KnowledgeBaseEntry? = knowledgeBase.lookup(dtcCode)

    private fun calculateHealthScore(
        dtcs: List<DtcLogEntity>,
        testResults: List<TestResultEntity>
    ): Int {
        val dtcPenalty = (dtcs.size * 10).coerceAtMost(60)
        val failPenalty = testResults.count { it.result == "FAIL" } * 5
        val warnPenalty = testResults.count { it.result == "WARNING" } * 2
        return (100 - dtcPenalty - failPenalty - warnPenalty).coerceAtLeast(0)
    }
}
