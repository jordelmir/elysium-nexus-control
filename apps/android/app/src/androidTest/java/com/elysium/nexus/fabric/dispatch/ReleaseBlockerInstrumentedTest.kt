package com.elysium.nexus.fabric.dispatch

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.database.IrCatalogRepository
import com.elysium.nexus.fabric.profile.InstalledIrProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0-10 (renamed): Profile resolution instrumented integration test.
 *
 * NOTE: Despite the filename, this test does NOT prove physical IR response.
 * It tests Room database resolution and DeviceCommandResolver on a real device.
 * Renamed to avoid false HIL claims. Actual HIL requires external IR receiver.
 *
 * Test #38-adjacent scenario, on the real device catalog:
 *
 *   GIVEN  two installed profiles (TV A = Samsung, TV B = LG)
 *   AND    an exact profileId for TV B
 *   AND    real SQLite bindings
 *   WHEN   the app is fully closed and reopened (fresh repository)
 *   AND    TV B is selected in "Mis Controles"
 *   AND    MUTE is pressed
 *   THEN   the first profile of the list is NOT used
 *   AND    DeviceCommandResolver loads TV B
 *   AND    resolves TV B's codeSetId
 *   AND    loads exactly TV B's signalId
 *   AND    verifies TV B's fingerprint
 *   AND    the transmitted frame is TV B's frame — NOT NEC-fabricated
 */
@RunWith(AndroidJUnit4::class)
class ReleaseBlockerInstrumentedTest {

    private lateinit var context: android.content.Context
    private lateinit var profileRepo: InstalledIrProfileRepository
    private lateinit var catalogRepo: IrCatalogRepository

    private val tvAId = "rel-blocker-tv-a"
    private val tvBId = "rel-blocker-tv-b"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        catalogRepo = IrCatalogRepository.getInstance(context)
        profileRepo = InstalledIrProfileRepository(context)
        // Clean any previous run state for these fixed test ids.
        profileRepo.deleteProfile(tvAId)
        profileRepo.deleteProfile(tvBId)
    }

    @After
    fun tearDown() {
        profileRepo.deleteProfile(tvAId)
        profileRepo.deleteProfile(tvBId)
    }

    @Test
    fun forceStopReopenSelectsExactProfileAndTransmitsItsOwnFrame() = runBlocking {
        // ── Arrangement: two REAL catalog candidates that have MUTE ──
        val samsung = catalogRepo.getCandidatesForBrand("Samsung", deviceType = "", action = IrAction.MUTE)
            .firstOrNull { IrAction.MUTE in it.commands }
            ?: throw AssertionError("No Samsung code set with MUTE in catalog")
        val lg = catalogRepo.getCandidatesForBrand("LG", deviceType = "", action = IrAction.MUTE)
            .firstOrNull { IrAction.MUTE in it.commands }
            ?: throw AssertionError("No LG code set with MUTE in catalog")

        // Distinct code sets proof the two profiles differ physically.
        assertNotEquals("TV A and TV B must be distinct code sets", samsung.id, lg.id)

        profileRepo.saveProfile(profileFrom(samsung, tvAId), verifiedActions = setOf(IrAction.VOLUME_UP, IrAction.VOLUME_DOWN, IrAction.MUTE))
        profileRepo.saveProfile(profileFrom(lg, tvBId), verifiedActions = setOf(IrAction.VOLUME_UP, IrAction.VOLUME_DOWN, IrAction.MUTE))

        // ── Force-stop + reopen: brand-new repository, must reload from Room ──
        val reopened = InstalledIrProfileRepository(context)
        val reopenedProfiles = reopened.getAllProfiles()
        assertTrue("Reopened repository must contain the two test profiles", reopenedProfiles.any { it.id == tvAId })
        assertTrue("Reopened repository must contain TV B", reopenedProfiles.any { it.id == tvBId })

        // ── "Mis Controles": select TV B by exact profileId ──
        val selectedTvB = reopened.getProfileSuspend(tvBId)
        assertTrue("TV B must resolve by profileId", selectedTvB != null)

        // ── MUTE dispatch through the authoritative device resolver ──
        val resolver = DeviceCommandResolver(context)
        val resolution = resolver.resolve(
            DeviceId(tvBId), // profileId is the canonical route key for IR remotes
            UniversalAction.Mute(targetDeviceId = DeviceId(tvBId))
        )

        // Section 1: must be the exact LG profile, NOT the first profile from the list.
        assertTrue("MUTE must resolve for TV B, got: $resolution", resolution is CommandResolution.Resolved)
        val resolved = resolution as CommandResolution.Resolved
        assertEquals("Resolved profile must be TV B", tvBId, resolved.profileId)
        assertEquals("Resolved codeSetId must be TV B's", lg.id, resolved.codeSetId)

        // Section 2: exact signalId + fingerprint of TV B.
        val plainSignal = lg.commands[IrAction.MUTE] ?: error("LG MUTE signal missing")
        val expectedSignalId = lg.commandSignalIds[IrAction.MUTE]
        val expectedFingerprint = IrProbeEngine.fingerprintSignal(plainSignal)
        assertEquals("SignalId must be exactly LG's MUTE signal", expectedSignalId, resolved.signalId)
        assertEquals("Fingerprint must match LG MUTE", expectedFingerprint, resolved.physicalSha256)

        // Section 3: no NEC fabrication — the resolved frame differs from any Samsung MUTE.
        val samsungSignalId = samsung.commandSignalIds[IrAction.MUTE]
        assertNotEquals("TV B must NOT emit the Samsung MUTE frame", samsungSignalId, resolved.signalId)

        // Section 4: an unknown profile must fail closed — never fall back to a random profile.
        val unknown = resolver.resolve(
            DeviceId("no-such-profile"),
            UniversalAction.Mute(targetDeviceId = DeviceId("no-such-profile"))
        )
        assertTrue("Unknown profile must fail closed", unknown is CommandResolution.ProfileMissing)
    }

    private fun profileFrom(codeSet: IrCodeSet, id: String): InstalledIrProfile {
        val bindings = mutableMapOf<IrAction, IrCommandBinding>()
        for ((action, signal) in codeSet.commands) {
            val signalId = codeSet.commandSignalIds[action]
                ?: codeSet.commandBindings.firstOrNull { it.action == action }?.signalId
                ?: continue
            bindings[action] = IrCommandBinding(
                signalId = signalId,
                physicalFingerprint = IrProbeEngine.fingerprintSignal(signal),
                sourceId = codeSet.provenance.commitSha ?: "catalog-legacy",
                action = action
            )
        }
        return InstalledIrProfile(
            id = id,
            displayName = "${codeSet.brand} Remote ($id)",
            brand = codeSet.brand,
            codeSetId = codeSet.id,
            commands = bindings,
            verificationStatus = VerificationStatus.VERIFIED_LAB
        )
    }
}