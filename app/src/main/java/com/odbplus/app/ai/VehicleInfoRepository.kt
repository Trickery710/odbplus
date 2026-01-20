package com.odbplus.app.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.odbplus.app.ai.data.VehicleDatabase
import com.odbplus.app.ai.data.VehicleInfo
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

private val Context.vehicleDataStore: DataStore<Preferences> by preferencesDataStore(name = "vehicle_database")

/**
 * Repository for storing and managing vehicle information indexed by VIN.
 */
@Singleton
class VehicleInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val databaseKey = stringPreferencesKey("vehicle_database")

    private val _database = MutableStateFlow(VehicleDatabase())
    val database: StateFlow<VehicleDatabase> = _database.asStateFlow()

    private val _currentVehicle = MutableStateFlow<VehicleInfo?>(null)
    val currentVehicle: StateFlow<VehicleInfo?> = _currentVehicle.asStateFlow()

    /**
     * Initialize repository and load persisted data.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val prefs = context.vehicleDataStore.data.first()
            val databaseJson = prefs[databaseKey]
            if (databaseJson != null) {
                _database.value = json.decodeFromString(databaseJson)
            }
        } catch (e: Exception) {
            _database.value = VehicleDatabase()
        }
    }

    /**
     * Check if this is the first time we've seen this vehicle (by VIN).
     */
    fun isFirstTimeVehicle(vin: String): Boolean {
        return _database.value.isFirstTimeVehicle(vin)
    }

    /**
     * Get vehicle info by VIN.
     */
    fun getVehicle(vin: String): VehicleInfo? {
        return _database.value.getVehicle(vin)
    }

    /**
     * Save or update vehicle information.
     */
    suspend fun saveVehicle(info: VehicleInfo) = withContext(Dispatchers.IO) {
        val updated = _database.value.addOrUpdateVehicle(info)
        _database.value = updated
        _currentVehicle.value = info
        persistDatabase(updated)
    }

    /**
     * Set the current active vehicle.
     */
    fun setCurrentVehicle(info: VehicleInfo?) {
        _currentVehicle.value = info
    }

    /**
     * Clear the current vehicle (on disconnect).
     */
    fun clearCurrentVehicle() {
        _currentVehicle.value = null
    }

    /**
     * Get all stored vehicles sorted by last seen.
     */
    fun getAllVehicles(): List<VehicleInfo> {
        return _database.value.vehicles.values
            .sortedByDescending { it.lastSeenTimestamp }
    }

    /**
     * Delete a vehicle from the database.
     */
    suspend fun deleteVehicle(vin: String) = withContext(Dispatchers.IO) {
        val updated = _database.value.copy(
            vehicles = _database.value.vehicles - vin,
            lastActiveVin = if (_database.value.lastActiveVin == vin) null else _database.value.lastActiveVin
        )
        _database.value = updated
        if (_currentVehicle.value?.vin == vin) {
            _currentVehicle.value = null
        }
        persistDatabase(updated)
    }

    private suspend fun persistDatabase(database: VehicleDatabase) {
        try {
            context.vehicleDataStore.edit { prefs ->
                prefs[databaseKey] = json.encodeToString(database)
            }
        } catch (e: Exception) {
            // Ignore persistence errors
        }
    }
}
