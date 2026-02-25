package com.odbplus.core.protocol.signalset

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and caches [VehicleSignalSet] instances.
 *
 * Load priority (first hit wins):
 * 1. **Memory cache** — in-process [ConcurrentHashMap]
 * 2. **Disk cache**   — `<cacheDir>/signalsets/<key>.json`
 * 3. **Bundled assets** — `assets/signalsets/<key>.json` (shipped in APK)
 * 4. **Network**      — OBDb GitHub raw content; saved to disk cache on success
 *
 * All I/O runs on [Dispatchers.IO].
 */
@Singleton
class VehicleSignalSetRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val memoryCache = ConcurrentHashMap<String, VehicleSignalSet>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Load the signal set for [vehicleKey] (e.g. "Toyota-Camry").
     * Returns null only if all four sources fail.
     */
    suspend fun load(vehicleKey: String): VehicleSignalSet? = withContext(Dispatchers.IO) {
        // 1. Memory
        memoryCache[vehicleKey]?.let { return@withContext it }

        // 2. Disk cache
        readDiskCache(vehicleKey)?.let { json ->
            val set = VehicleSignalSetParser.parse(vehicleKey, json)
            if (set != null) {
                memoryCache[vehicleKey] = set
                return@withContext set
            }
        }

        // 3. Bundled assets
        readAsset(vehicleKey)?.let { json ->
            val set = VehicleSignalSetParser.parse(vehicleKey, json)
            if (set != null) {
                memoryCache[vehicleKey] = set
                return@withContext set
            }
        }

        // 4. Network
        fetchNetwork(vehicleKey)?.let { json ->
            val set = VehicleSignalSetParser.parse(vehicleKey, json)
            if (set != null) {
                writeDiskCache(vehicleKey, json)
                memoryCache[vehicleKey] = set
                return@withContext set
            }
        }

        Timber.w("VehicleSignalSetRepository: no source yielded a set for '$vehicleKey'")
        null
    }

    /** Remove [vehicleKey] from in-memory and disk cache. */
    fun evict(vehicleKey: String) {
        memoryCache.remove(vehicleKey)
        diskFile(vehicleKey).takeIf { it.exists() }?.delete()
    }

    /** Returns the list of vehicle keys available as bundled assets. */
    fun listBundled(): List<String> {
        return try {
            context.assets.list("signalsets")
                ?.filter { it.endsWith(".json") }
                ?.map { it.removeSuffix(".json") }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "VehicleSignalSetRepository: could not list bundled assets")
            emptyList()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun diskFile(vehicleKey: String): File {
        val dir = File(context.cacheDir, "signalsets")
        dir.mkdirs()
        return File(dir, "$vehicleKey.json")
    }

    private fun readDiskCache(vehicleKey: String): String? {
        return try {
            val file = diskFile(vehicleKey)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            Timber.w(e, "VehicleSignalSetRepository: disk read failed for '$vehicleKey'")
            null
        }
    }

    private fun writeDiskCache(vehicleKey: String, json: String) {
        try {
            diskFile(vehicleKey).writeText(json)
        } catch (e: Exception) {
            Timber.w(e, "VehicleSignalSetRepository: disk write failed for '$vehicleKey'")
        }
    }

    private fun readAsset(vehicleKey: String): String? {
        return try {
            context.assets.open("signalsets/$vehicleKey.json").bufferedReader().readText()
        } catch (e: Exception) {
            null  // asset simply doesn't exist — not a warning
        }
    }

    private fun fetchNetwork(vehicleKey: String): String? {
        return try {
            val url = "https://raw.githubusercontent.com/OBDb/$vehicleKey/main/signalsets/v3/default.json"
            Timber.d("VehicleSignalSetRepository: fetching $url")
            URL(url).openStream().bufferedReader().readText()
        } catch (e: Exception) {
            Timber.w(e, "VehicleSignalSetRepository: network fetch failed for '$vehicleKey'")
            null
        }
    }
}
