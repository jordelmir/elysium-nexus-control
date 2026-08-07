package com.elysium.nexus.fabric.infrared.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Section 21 Instrumented Test for [IrCatalogRepository].
 *
 * Verifies that `ir_catalog.db` packages cleanly inside the APK, copies atomically to
 * local app cache, opens successfully via SQLite, decompresses zlib raw pattern blobs,
 * and returns valid, positive-duration candidates for [IrProbeEngine].
 */
@RunWith(AndroidJUnit4::class)
class IrCatalogRepositoryInstrumentedTest {

    private lateinit var repository: IrCatalogRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = IrCatalogRepository.getInstance(context)
    }

    @Test
    fun catalogDatabaseOpensAndReturnsValidStats() = runBlocking {
        val stats = repository.getStats()
        assertTrue("Catalog must contain brands", stats.brands > 0)
        assertTrue("Catalog must contain remotes", stats.remotes > 0)
        assertTrue("Catalog must contain total commands", stats.totalCommands > 0)
        assertTrue("Catalog must contain protocols", stats.protocols > 0)
    }

    @Test
    fun catalogReturnsVolumeUpCandidatesForMajorBrands() = runBlocking {
        val testBrands = listOf(
            "Samsung", "LG", "Sony", "Panasonic", "Philips",
            "Kintech", "Control Universal TV"
        )

        for (brand in testBrands) {
            val candidates = repository.getCandidatesForBrand(
                brand = brand,
                deviceType = "",
                action = IrAction.VOLUME_UP
            )

            assertTrue("Candidates for $brand must be non-empty", candidates.isNotEmpty())

            // Verify every candidate resolves a real VOLUME_UP fingerprint
            candidates.forEach { cs ->
                val sig = cs.commands[IrAction.VOLUME_UP] ?: error("Candidate ${cs.id} missing VOLUME_UP")
                assertNotNull("VOLUME_UP fingerprint must exist", cs.commandSignalIds[IrAction.VOLUME_UP])
                val fp = IrProbeEngine.fingerprintSignal(sig)
                assertTrue("Fingerprint must be non-empty for $brand", fp.isNotBlank())
            }

            // Verify raw signals have strictly positive timing slices
            candidates.forEach { cs ->
                val sig = cs.commands[IrAction.VOLUME_UP]
                assertNotNull(sig)
                if (sig is IrSignal.Raw) {
                    assertTrue("Raw timing pattern must not be empty", sig.patternUs.isNotEmpty())
                    assertTrue("All slice durations in raw pattern must be > 0", sig.patternUs.all { it > 0 })
                    assertTrue("Total duration must be < 2,000,000 µs", sig.patternUs.sumOf { it.toLong() } < 2_000_000L)
                }
            }
        }
    }

    @Test
    fun searchDevicesReturnsMatchingResults() = runBlocking {
        val results = repository.searchDevices("Samsung")
        assertTrue("Search for 'Samsung' must return device results", results.isNotEmpty())
        assertTrue("Result brand must contain 'Samsung'", results.first().brand.contains("Samsung", ignoreCase = true))
    }
}
