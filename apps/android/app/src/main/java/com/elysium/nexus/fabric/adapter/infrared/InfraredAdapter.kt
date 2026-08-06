package com.elysium.nexus.fabric.adapter.infrared

import android.content.Context
import android.util.Log
import com.elysium.nexus.core.device.DeviceCatalog
import com.elysium.nexus.core.device.DeviceCategory
import com.elysium.nexus.fabric.adapter.AdapterResult
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.ReadResult
import com.elysium.nexus.fabric.adapter.ScanResult
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.ClimateMode
import com.elysium.nexus.fabric.canonical.ConnectivityState
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.DeviceType
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.ProtocolBinding
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.IrTransmitResult
import com.elysium.nexus.fabric.infrared.IrWaveform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InfraredAdapter(
    private val context: Context? = null
) : DeviceAdapter {

    override val protocol: Protocol = Protocol.DirectIr
    override val label: String = "Infrared"
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.Level,
        Capability.MediaTransport,
        Capability.Volume,
        Capability.Channel,
        Capability.InputSource,
        Capability.TargetTemperature,
        Capability.FanSpeed,
        Capability.Mode
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    private var transmitter: AndroidIrTransmitter? = null

    companion object {
        private const val TAG = "InfraredAdapter"
    }

    override suspend fun start(): AdapterResult {
        _state.value = AdapterState.Starting
        transmitter = context?.let { AndroidIrTransmitter(it) }
        val hasEmitter = transmitter?.hasEmitter() == true
        Log.i(TAG, "IR emitter present: $hasEmitter")
        _state.value = AdapterState.Active
        return AdapterResult.Ok
    }

    override suspend fun scan(timeoutMs: Long): ScanResult {
        if (transmitter == null) {
            return ScanResult.Error(
                ErrorCode.HardwareUnavailable,
                "No IR emitter available."
            )
        }
        val twins = DeviceCatalog.all.map { template ->
            DeviceTwin(
                deviceId = DeviceId("ir-${template.id}"),
                manufacturer = template.brand,
                model = template.model,
                deviceType = when (template.category) {
                    DeviceCategory.TV -> DeviceType.Television
                    DeviceCategory.ANDROID_TV -> DeviceType.Television
                    DeviceCategory.SOUNDBAR -> DeviceType.Soundbar
                    DeviceCategory.PROJECTOR -> DeviceType.Projector
                    else -> DeviceType.Unknown
                },
                capabilities = setOf(Capability.OnOff),
                connectivity = ConnectivityState.Online,
                protocolBindings = setOf(
                    ProtocolBinding(
                        protocol = Protocol.DirectIr,
                        endpoint = "ir-${template.id}",
                        capabilities = setOf(Capability.OnOff)
                    )
                )
            )
        }
        _devices.value = twins
        return ScanResult.Ok(deviceCount = twins.size)
    }

    override suspend fun read(deviceId: DeviceId): ReadResult {
        return ReadResult.Error(
            ErrorCode.UnsupportedOperation,
            "IR is transmit-only; no state to read."
        )
    }

    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult {
        val tx = transmitter
        if (tx == null || !tx.hasEmitter()) {
            return WriteResult.Error(
                ErrorCode.HardwareUnavailable,
                "No IR emitter available on this device."
            )
        }
        return when (state) {
            is DeviceState.IrCommand -> {
                val waveform = encodeIrCommand(state)
                if (waveform == null) {
                    return WriteResult.Error(
                        ErrorCode.UnsupportedOperation,
                        "Cannot encode IR command for protocol ${state.protocolName}."
                    )
                }
                val transmitResult = tx.transmit(waveform)
                if (transmitResult is IrTransmitResult.Success) {
                    Log.i(TAG, "IR transmitted: ${state.protocolName} addr=${state.address} cmd=${state.command}")
                    WriteResult.Ok(reportedState = state)
                } else {
                    WriteResult.Error(
                        ErrorCode.HardwareUnavailable,
                        "IR transmit failed ($transmitResult)."
                    )
                }
            }
            else -> WriteResult.Error(
                ErrorCode.UnsupportedOperation,
                "IR adapter requires DeviceState.IrCommand."
            )
        }
    }

    private fun encodeIrCommand(state: DeviceState.IrCommand): IrWaveform? {
        return when (state.protocolName.uppercase()) {
            "NEC" -> IrWaveform.encodeNec(state.address, state.command)
            "NECX", "NEC_EXTENDED" -> IrWaveform.encodeNecExtended(state.address, state.command)
            "RC5" -> IrWaveform.encodeRc5(state.address, state.command)
            "RC6" -> IrWaveform.encodeRc6(state.address, state.command)
            "SIRC", "SONY_SIRC" -> IrWaveform.encodeSonySirc(state.address, state.command)
            "SAMSUNG" -> IrWaveform.encodeSamsung(state.address, state.command)
            "KASEIKYO", "PANASONIC" -> IrWaveform.encodeKaseikyo(state.address, state.command)
            "DAIKIN" -> IrWaveform.encodeDaikin(
                address = state.address,
                powerOn = state.extras["on"]?.toBoolean() ?: true,
                temperatureCelsius = state.extras["temp"]?.toIntOrNull()?.coerceIn(16, 32) ?: 25,
                mode = if (state.extras["mode"]?.uppercase() == "HEAT") 1 else 0,
                fanSpeed = state.extras["fan"]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
            )
            "GREE" -> IrWaveform.encodeGree(
                address = state.address and 0xF,
                powerOn = state.extras["on"]?.toBoolean() ?: true,
                temperatureCelsius = state.extras["temp"]?.toIntOrNull()?.coerceIn(16, 30) ?: 25,
                mode = if (state.extras["mode"]?.uppercase() == "HEAT") 1 else 0,
                fanSpeed = state.extras["fan"]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
            )
            "MIDEA" -> IrWaveform.encodeMidea(
                address = state.address,
                powerOn = state.extras["on"]?.toBoolean() ?: true,
                temperatureCelsius = state.extras["temp"]?.toIntOrNull()?.coerceIn(17, 30) ?: 25,
                mode = if (state.extras["mode"]?.uppercase() == "HEAT") 1 else 0,
                fanSpeed = state.extras["fan"]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
            )
            "MITSUBISHI" -> IrWaveform.encodeMitsubishi(
                address = state.address,
                powerOn = state.extras["on"]?.toBoolean() ?: true,
                temperatureCelsius = state.extras["temp"]?.toIntOrNull()?.coerceIn(16, 31) ?: 25,
                mode = if (state.extras["mode"]?.uppercase() == "HEAT") 1 else 0,
                fanSpeed = state.extras["fan"]?.toIntOrNull()?.coerceIn(0, 4) ?: 0
            )
            else -> null
        }
    }

    /**
     * Translate a [UniversalAction] into a [DeviceState.IrCommand]
     * for IR transmission. Maps canonical actions to IR protocol
     * commands using NEC as the default protocol.
     */
    override fun translateAction(action: UniversalAction): DeviceState? {
        return when (action) {
            is UniversalAction.PowerOn, is UniversalAction.PowerOff, is UniversalAction.PowerToggle ->
                DeviceState.IrCommand(protocolName = "NEC", address = 0, command = 0x45)
            is UniversalAction.VolumeUp ->
                DeviceState.IrCommand(protocolName = "NEC", address = 0, command = 0x44)
            is UniversalAction.VolumeDown ->
                DeviceState.IrCommand(protocolName = "NEC", address = 0, command = 0x43)
            is UniversalAction.Mute ->
                DeviceState.IrCommand(protocolName = "NEC", address = 0, command = 0x46)
            is UniversalAction.ChannelUp ->
                DeviceState.IrCommand(protocolName = "NEC", address = 0, command = 0x40)
            is UniversalAction.ChannelDown ->
                DeviceState.IrCommand(protocolName = "NEC", address = 0, command = 0x41)
            is UniversalAction.InputSelect ->
                DeviceState.IrCommand(protocolName = "NEC", address = 0, command = 0x42)
            is UniversalAction.SetTemperature ->
                DeviceState.IrCommand(
                    protocolName = "DAIKIN",
                    address = 0, command = 0,
                    extras = mapOf(
                        "temp" to action.targetCelsius.toInt().toString(),
                        "mode" to if (action.mode == ClimateMode.Cool) "COOL" else "HEAT",
                        "fan" to "0",
                        "on" to "true"
                    )
                )
            is UniversalAction.SetMode ->
                DeviceState.IrCommand(
                    protocolName = "DAIKIN",
                    address = 0, command = 0,
                    extras = mapOf(
                        "temp" to "25",
                        "mode" to action.mode.name.uppercase(),
                        "fan" to "0",
                        "on" to (action.mode != ClimateMode.Off).toString()
                    )
                )
            is UniversalAction.SetFanSpeed ->
                DeviceState.IrCommand(
                    protocolName = "DAIKIN",
                    address = 0, command = 0,
                    extras = mapOf(
                        "temp" to "25",
                        "mode" to "COOL",
                        "fan" to (action.level * 3).toInt().toString(),
                        "on" to "true"
                    )
                )
            else -> null // Navigation, Media, Custom not supported via IR defaults
        }
    }

    override suspend fun subscribe(deviceId: DeviceId): AdapterResult {
        return AdapterResult.Error(
            ErrorCode.UnsupportedOperation,
            "IR is fire-and-forget; no state subscription."
        )
    }

    override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok

    override suspend fun stop(): AdapterResult {
        transmitter = null
        _state.value = AdapterState.Released
        return AdapterResult.Ok
    }
}
