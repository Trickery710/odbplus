package com.odbplus.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.connectionProfileDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "connection_profiles")

@Singleton
class ConnectionProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val LAST_BT_MAC  = stringPreferencesKey("last_bt_mac")
    private val LAST_BT_NAME = stringPreferencesKey("last_bt_name")
    private val LAST_WIFI_HOST = stringPreferencesKey("last_wifi_host")
    private val LAST_WIFI_PORT = intPreferencesKey("last_wifi_port")

    val lastBtMac:  Flow<String?> = context.connectionProfileDataStore.data.map { it[LAST_BT_MAC] }
    val lastBtName: Flow<String?> = context.connectionProfileDataStore.data.map { it[LAST_BT_NAME] }
    val lastWifiHost: Flow<String?> = context.connectionProfileDataStore.data.map { it[LAST_WIFI_HOST] }
    val lastWifiPort: Flow<Int>  = context.connectionProfileDataStore.data.map { it[LAST_WIFI_PORT] ?: 35000 }

    suspend fun saveBluetoothProfile(mac: String, name: String) {
        context.connectionProfileDataStore.edit {
            it[LAST_BT_MAC]  = mac
            it[LAST_BT_NAME] = name
        }
    }

    suspend fun saveWifiProfile(host: String, port: Int) {
        context.connectionProfileDataStore.edit {
            it[LAST_WIFI_HOST] = host
            it[LAST_WIFI_PORT] = port
        }
    }
}
