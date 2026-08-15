package com.elysium.nexus.tvnode

import com.elysium.nexus.tvnode.access.CapabilityManifestBuilder
import com.elysium.nexus.tvnode.access.TvAccessLevel
import com.elysium.nexus.tvnode.canonical.ActionResult
import com.elysium.nexus.tvnode.canonical.Capability
import com.elysium.nexus.tvnode.canonical.Direction
import com.elysium.nexus.tvnode.canonical.DeviceId
import com.elysium.nexus.tvnode.canonical.UniversalAction
import com.elysium.nexus.tvnode.identity.DeviceFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvNodeCanonicalTest {

    private val tvId = DeviceId("universal:test:abs")

    @Test
    fun `canonical twin is wire-compatible with the controller contract`() {
        // Every action type present in the controller's sealed hierarchy
        // must resolve to a capability on the TV node copy.
        val actions: List<UniversalAction> = listOf(
            UniversalAction.PowerOn(tvId),
            UniversalAction.PowerOff(tvId),
            UniversalAction.PowerToggle(tvId),
            UniversalAction.VolumeUp(tvId),
            UniversalAction.VolumeDown(tvId),
            UniversalAction.Mute(tvId),
            UniversalAction.SetVolume(tvId, 0.5f),
            UniversalAction.ChannelUp(tvId),
            UniversalAction.ChannelDown(tvId),
            UniversalAction.InputSelect(tvId, "hdmi1"),
            UniversalAction.MediaPlay(tvId),
            UniversalAction.MediaPause(tvId),
            UniversalAction.MediaStop(tvId),
            UniversalAction.MediaNext(tvId),
            UniversalAction.MediaPrevious(tvId),
            UniversalAction.Navigate(tvId, Direction.Up),
            UniversalAction.Ok(tvId),
            UniversalAction.Back(tvId),
            UniversalAction.Home(tvId),
            UniversalAction.Menu(tvId),
            UniversalAction.SetTemperature(tvId, 24f),
            UniversalAction.SetFanSpeed(tvId, 0.3f),
            UniversalAction.SetMode(tvId, com.elysium.nexus.tvnode.canonical.ClimateMode.Cool),
            UniversalAction.Custom(tvId, "volume_up_os")
        )
        assertEquals(24, actions.size)
        actions.forEach { assertTrue(it.requiredCapability().name.isNotBlank()) }
    }

    @Test
    fun `every capability carries a documentable risk class`() {
        // This is what the phone-side risk gate will trust for POWER/INPUT gating.
        assertEquals(Capability.OnOff.defaultRisk, com.elysium.nexus.tvnode.canonical.ActionRisk.Low)
        assertEquals(Capability.Volume.defaultRisk, com.elysium.nexus.tvnode.canonical.ActionRisk.Low)
    }

    @Test
    fun `action results carry the honest verdict taxonomy`() {
        assertTrue(ActionResult.Success("observed delta").detail.isNotBlank())
        assertTrue(ActionResult.ExecutedUnverified("fired, no proof").detail.isNotBlank())
    }

    @Test
    fun `capability manifest degrades with API level`() {
        val api33 = DeviceFacts(
            manufacturer = "universal", model = "test", device = "abs", product = "test",
            apiLevel = 33, platform = "Android-13", isTv = true, leanback = true,
            hdmiCec = false, volumeFixed = false, canRequestFilterKeyEvents = true,
            isAccessibilityEnabled = true, hasBluetooth = true, manager = "test"
        )
        val manifest = CapabilityManifestBuilder.build(api33, TvAccessLevel.ENHANCED_USER_GRANTED)
        assertTrue(manifest.globalTvActions)         // API 33+: GLOBAL_ACTION_* available
        assertTrue(manifest.keyFilteringObservable)  // canRequestFilterKeyEvents = true
        assertTrue(manifest.volumeObservable)

        val api30 = api33.copy(apiLevel = 30, platform = "Android-11", canRequestFilterKeyEvents = false)
        val degraded = CapabilityManifestBuilder.build(api30, TvAccessLevel.ENHANCED_USER_GRANTED)
        assertTrue(!degraded.globalTvActions)
        assertTrue(!degraded.keyFilteringObservable)
        assertTrue(degraded.volumeObservable)
    }

    @Test
    fun `fixed volume TV drops mute from the honest surface`() {
        val facts = DeviceFacts(
            manufacturer = "universal", model = "test", device = "abs", product = "test",
            apiLevel = 31, platform = "Android-12", isTv = true, leanback = true,
            hdmiCec = false, volumeFixed = true, canRequestFilterKeyEvents = false,
            isAccessibilityEnabled = false, hasBluetooth = true, manager = "test"
        )
        val manifest = CapabilityManifestBuilder.build(facts, TvAccessLevel.ENHANCED_USER_GRANTED)
        assertTrue(!manifest.muteObservable)
    }
}