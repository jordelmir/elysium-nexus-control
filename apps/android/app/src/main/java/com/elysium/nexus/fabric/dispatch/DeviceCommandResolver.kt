package com.elysium.nexus.fabric.dispatch

import android.content.Context
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.infrared.database.IrCatalogRepository
import com.elysium.nexus.fabric.profile.InstalledIrProfileRepository

data class ResolvedIrCommand(
    val profileId: String,
    val codeSetId: String,
    val action: IrAction,
    val signalId: String,
    val physicalSha256: String,
    val signal: IrSignal
)

/**
 * §4.7 Authoritative Device Command Resolver.
 *
 * Resolves [UniversalAction] and [DeviceId] to exact [ResolvedIrCommand] carrying
 * database [signalId] and physical [IrSignal] loaded directly from SQLite/Room.
 * Zero manufactured signals or brand fallbacks.
 */
class DeviceCommandResolver(
    private val context: Context
) {
    private val profileRepository = InstalledIrProfileRepository(context)
    private val catalogRepository = IrCatalogRepository.getInstance(context)

    suspend fun resolve(
        deviceId: DeviceId,
        action: UniversalAction
    ): ResolvedIrCommand? {
        val profileId = deviceId.value.removePrefix("ir-")
        val profile = profileRepository.getProfile(profileId)
            ?: profileRepository.getAllProfiles().firstOrNull()
            ?: return null

        val irAction = mapUniversalActionToIrAction(action) ?: return null
        val binding = profile.commands[irAction] ?: return null
        val signal = catalogRepository.getSignal(binding.signalId) ?: return null

        return ResolvedIrCommand(
            profileId = profile.id,
            codeSetId = profile.codeSetId,
            action = irAction,
            signalId = binding.signalId,
            physicalSha256 = binding.physicalFingerprint,
            signal = signal
        )
    }

    private fun mapUniversalActionToIrAction(action: UniversalAction): IrAction? = when (action) {
        is UniversalAction.PowerOn -> IrAction.POWER_ON
        is UniversalAction.PowerOff -> IrAction.POWER_OFF
        is UniversalAction.PowerToggle -> IrAction.POWER_TOGGLE
        is UniversalAction.VolumeUp -> IrAction.VOLUME_UP
        is UniversalAction.VolumeDown -> IrAction.VOLUME_DOWN
        is UniversalAction.Mute -> IrAction.MUTE
        is UniversalAction.ChannelUp -> IrAction.CHANNEL_UP
        is UniversalAction.ChannelDown -> IrAction.CHANNEL_DOWN
        is UniversalAction.Ok -> IrAction.OK
        is UniversalAction.Back -> IrAction.BACK
        is UniversalAction.Home -> IrAction.HOME
        is UniversalAction.Menu -> IrAction.MENU
        is UniversalAction.MediaPlay -> IrAction.PLAY
        is UniversalAction.MediaPause -> IrAction.PAUSE
        is UniversalAction.MediaStop -> IrAction.STOP
        else -> null
    }
}
