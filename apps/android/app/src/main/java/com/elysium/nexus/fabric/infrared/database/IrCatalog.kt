package com.elysium.nexus.fabric.infrared.database

import com.elysium.nexus.core.device.CatalogCommandBinding
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal

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

data class SignalMetadata(
    val signalId: String,
    val encodingType: String,
    val codecId: String?,
    val carrierHz: Int,
    val addressValue: Int,
    val subDeviceValue: Int,
    val commandValue: Int,
    val physicalSha256: String,
    val sourceRevisionSha: String?
)

/**
 * P1-PROVENANCE: Multi-source provenance for a single signal.
 * A signal may originate from Flipper, SmartIR, local capture, etc.
 */
data class SignalProvenance(
    val signalId: String,
    val sourceId: String,
    val sourceRevisionId: String,
    val evidenceLevel: String,
    val verificationSource: String?,
    val verifiedAtEpochMs: Long?,
    val deviceModel: String?,
    val notes: String?
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
     * Universal sweep: query ALL production-approved code sets of a device
     * type that contain [action], across every brand in the catalog.
     * Powers the "Control Universal" auto-sweep experience.
     *
     * P0-8: Progressive search — returns candidates ordered by probability:
     *   1. Popular global brands (Samsung, LG, Sony, Panasonic, Philips)
     *   2. Regional brands (Sankey, Kintech, Kalley, Hisense, TCL)
     *   3. All remaining brands
     * No LIMIT 400 — caller drains via IrProbeEngine.nextCandidate().
     */
    suspend fun getAllCandidates(
        deviceType: String = "TV",
        action: IrAction = IrAction.VOLUME_UP,
        limit: Int = 400
    ): List<IrCodeSet>

    /**
     * Retrieve single physical [IrSignal] by exact signal ID.
     */
    suspend fun getSignal(signalId: String): IrSignal?

    /**
     * §7 Retrieve all command bindings for a code set with deterministic selection.
     */
    suspend fun getCommandsForCodeSet(codeSetId: String): Map<IrAction, List<CatalogCommandBinding>>

    /**
     * §7 Retrieve signal metadata including source revision SHA.
     */
    suspend fun getSignalMetadata(signalId: String): SignalMetadata?

    /**
     * §7 Retrieve a full code set by ID for authoritative re-read during installation.
     */
    suspend fun getCodeSet(codeSetId: String): IrCodeSet?

    /**
     * Query overall catalog statistics.
     */
    suspend fun getStats(): CatalogStats

    /**
     * P1-PROVENANCE: Get all source provenance records for a signal.
     * A single signal may have multiple sources (Flipper + SmartIR + local capture).
     */
    suspend fun getSignalProvenance(signalId: String): List<SignalProvenance>
}

/**
 * In-Memory IR Catalog implementation for fast JVM testing and standalone mocks.
 */
class InMemoryIrCatalog(
    private val candidateMap: Map<String, List<IrCodeSet>> = emptyMap(),
    private val devices: List<DeviceSearchResult> = emptyList(),
    private val signalMap: Map<String, IrSignal> = emptyMap()
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

    override suspend fun getAllCandidates(
        deviceType: String,
        action: IrAction,
        limit: Int
    ): List<IrCodeSet> {
        return candidateMap.values.flatten()
            .filter { action in it.commands }
            .take(limit)
    }

    override suspend fun getSignal(signalId: String): IrSignal? {
        return signalMap[signalId]
    }

    override suspend fun getCommandsForCodeSet(codeSetId: String): Map<IrAction, List<CatalogCommandBinding>> {
        return emptyMap()
    }

    override suspend fun getSignalMetadata(signalId: String): SignalMetadata? {
        return null
    }

    override suspend fun getCodeSet(codeSetId: String): IrCodeSet? {
        return candidateMap.values.flatten().firstOrNull { it.id == codeSetId }
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

    override suspend fun getSignalProvenance(signalId: String): List<SignalProvenance> {
        return emptyList()
    }
}
