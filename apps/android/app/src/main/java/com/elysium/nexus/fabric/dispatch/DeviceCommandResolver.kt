package com.elysium.nexus.fabric.dispatch

import android.content.Context
import android.util.Log
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.database.IrCatalogRepository
import com.elysium.nexus.fabric.profile.InstalledIrProfileRepository

private const val TAG = "ElysiumNexus.CommandResolver"

/**
 * §11.1 Command Resolution Result.
 */
sealed interface CommandResolution {
    data class ResolvedIr(
        val profileId: String,
        val signalId: String,
        val signal: IrSignal,
        val physicalFingerprint: String
    ) : CommandResolution

    data class Unsupported(val reason: String) : CommandResolution
    data class ProfileMissing(val profileId: String) : CommandResolution
}

/**
 * §11.1 Device Command Resolver Interface.
 */
interface DeviceCommandResolver {
    suspend fun resolve(
        profileId: String,
        action: UniversalAction
    ): CommandResolution
}

/**
 * §11.1 IrCommandResolver.
 *
 * Resolves a [UniversalAction] via active [profileId] and [InstalledIrProfileRepository],
 * retrieving the exact physical [IrSignal] from [IrCatalogRepository].
 */
class IrCommandResolver(
    private val context: Context,
    private val profileRepository: InstalledIrProfileRepository = InstalledIrProfileRepository(context),
    private val catalogRepository: IrCatalogRepository = IrCatalogRepository(context)
) : DeviceCommandResolver {

    override suspend fun resolve(
        profileId: String,
        action: UniversalAction
    ): CommandResolution {
        val profile = profileRepository.getProfile(profileId)
            ?: return CommandResolution.ProfileMissing(profileId)

        val irAction = mapUniversalActionToIrAction(action)
            ?: return CommandResolution.Unsupported("Action ${action::class.simpleName} cannot be mapped to IrAction")

        val binding = profile.commands[irAction]
            ?: return CommandResolution.Unsupported("Action $irAction not bound in profile $profileId")

        val candidates = catalogRepository.getCandidatesForBrand(profile.brand, profile.deviceType, irAction)
        val matched = candidates.firstOrNull { it.id == profile.codeSetId }
            ?: candidates.firstOrNull { cs -> cs.commands[irAction]?.let { IrProtocol.encode(it) } != null }

        val signal = matched?.commands?.get(irAction)
            ?: return CommandResolution.Unsupported("Signal ${binding.signalId} not found in catalog for $irAction")

        Log.d(TAG, "Resolved action $irAction to physical signalId=${binding.signalId} (codeSetId=${profile.codeSetId})")
        return CommandResolution.ResolvedIr(
            profileId = profileId,
            signalId = binding.signalId,
            signal = signal,
            physicalFingerprint = binding.physicalFingerprint
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
        is UniversalAction.InputSelect -> IrAction.INPUT
        is UniversalAction.Home -> IrAction.HOME
        is UniversalAction.Back -> IrAction.BACK
        is UniversalAction.Menu -> IrAction.MENU
        is UniversalAction.Ok -> IrAction.OK
        is UniversalAction.MediaPlay -> IrAction.PLAY
        is UniversalAction.MediaPause -> IrAction.PAUSE
        is UniversalAction.MediaStop -> IrAction.STOP
        else -> null
    }
}
