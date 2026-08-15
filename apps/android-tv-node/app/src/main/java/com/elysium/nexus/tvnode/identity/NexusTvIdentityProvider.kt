package com.elysium.nexus.tvnode.identity

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import com.elysium.nexus.tvnode.canonical.DeviceId

/**
 * NexusTvIdentityProvider — honest identity + device facts of THIS TV.
 *
 * §TV-FABRIC.1: the node reports only *metadata join keys* (manufacturer,
 * model family, platform, API level, feature flags). It deliberately does
 * NOT expose a physical unique identity over the wire: pairing binds the
 * node to its user through intent (QR + 6-digit proof of presence), not
 * through a serialized fingerprint.
 *
 * The `/device` facts block is what the phone's RoutePlanner and the IR
 * oracle matcher will use to narrow the candidate code_set universe.
 */
class NexusTvIdentityProvider(
    private val appContext: Context,
    private val managerInfo: () -> String = { "Elysium Nexus TvNode" },
    private val featureFlags: Map<String, Boolean> = emptyMap()
) {

    companion object {
        const val META_FEATURE_LEANBACK = "android.software.leanback"
        const val META_FEATURE_TELEVISION = "android.hardware.type.television"
        const val META_FEATURE_HDMI_CEC = "android.hardware.hdmi.cec"
    }

    /** Structural identity — join key, not a physical serial. */
    val deviceId: DeviceId = DeviceId("${Build.MANUFACTURER}:${Build.MODEL}:${Build.DEVICE}")

    /** TV facts exposed to the phone for route planning, ranking and IR narrowing. */
    fun deviceFacts(): DeviceFacts = DeviceFacts(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        device = Build.DEVICE,
        product = Build.PRODUCT,
        apiLevel = Build.VERSION.SDK_INT,
        platform = platformOf(),
        isTv = hasFeature(META_FEATURE_LEANBACK) || hasFeature(META_FEATURE_TELEVISION),
        leanback = hasFeature(META_FEATURE_LEANBACK),
        hdmiCec = hasFeature(META_FEATURE_HDMI_CEC),
        volumeFixed = (appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isVolumeFixed == true,
        canRequestFilterKeyEvents = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        isAccessibilityEnabled = false, // filled by the observer engine at runtime
        hasBluetooth = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
        manager = managerInfo()
    )

    private fun hasFeature(name: String): Boolean =
        featureFlags[name] ?: appContext.packageManager.hasSystemFeature(name)

    private fun platformOf(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> "Android-15+"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> "Android-14"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> "Android-13"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 -> "Android-12L"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> "Android-12"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> "Android-11"
        else -> "Android-${Build.VERSION.SDK_INT}"
    }
}

/**
 * Immutable snapshot of the TV's facts. Serialized to JSON for the
 * pairing handshake and for the oracle's TV-identity pin.
 */
data class DeviceFacts(
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val apiLevel: Int,
    val platform: String,
    val isTv: Boolean,
    val leanback: Boolean,
    val hdmiCec: Boolean,
    val volumeFixed: Boolean,
    val canRequestFilterKeyEvents: Boolean,
    val isAccessibilityEnabled: Boolean,
    val hasBluetooth: Boolean,
    val manager: String
)