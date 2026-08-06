package com.elysium.nexus.fabric.infrared.database

import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet

data class DeviceSearchResult(
    val id: String,
    val brand: String,
    val model: String,
    val category: String,
    val remoteModel: String = "",
    val source: String = ""
)

data class CatalogStats(
    val brands: Int,
    val deviceTypes: Int,
    val remotes: Int,
    val encodedCommands: Int,
    val rawCommands: Int,
    val totalCommands: Int,
    val protocols: Int
)

/**
 * Domain interface for querying IR remote control catalogs.
 * Decouples the UI and probe engine from concrete SQLite or in-memory persistence.
 */
interface IrCatalog {
    /**
     * Search brands in the catalog matching [query].
     */
    suspend fun searchBrands(query: String): List<String>

    /**
     * Search specific device remotes in the catalog matching [query].
     */
    suspend fun searchDevices(query: String): List<DeviceSearchResult>

    /**
     * Query candidate code sets for a given brand and action.
     */
    suspend fun getCandidatesForBrand(
        brand: String,
        deviceType: String = "TV",
        action: IrAction = IrAction.VOLUME_UP
    ): List<IrCodeSet>

    /**
     * Query overall catalog statistics.
     */
    suspend fun getStats(): CatalogStats
}

/**
 * In-Memory IR Catalog implementation for fast JVM testing and standalone mocks.
 */
class InMemoryIrCatalog(
    private val candidateMap: Map<String, List<IrCodeSet>> = emptyMap(),
    private val devices: List<DeviceSearchResult> = emptyList()
) : IrCatalog {

    override suspend fun searchBrands(query: String): List<String> {
        return candidateMap.keys.filter { it.contains(query, ignoreCase = true) }
    }

    override suspend fun searchDevices(query: String): List<DeviceSearchResult> {
        return devices.filter {
            it.brand.contains(query, ignoreCase = true) ||
            it.model.contains(query, ignoreCase = true)
        }
    }

    override suspend fun getCandidatesForBrand(
        brand: String,
        deviceType: String,
        action: IrAction
    ): List<IrCodeSet> {
        return candidateMap[brand] ?: candidateMap.entries.firstOrNull {
            it.key.equals(brand, ignoreCase = true)
        }?.value ?: emptyList()
    }

    override suspend fun getStats(): CatalogStats {
        val totalRemotes = candidateMap.values.sumOf { it.size }
        return CatalogStats(
            brands = candidateMap.size,
            deviceTypes = 1,
            remotes = totalRemotes,
            encodedCommands = totalRemotes,
            rawCommands = 0,
            totalCommands = totalRemotes,
            protocols = 1
        )
    }
}
