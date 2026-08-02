package com.elysium.nexus.fabric.adapter.infrared

import com.elysium.nexus.fabric.adapter.AdapterResult
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.ReadResult
import com.elysium.nexus.fabric.adapter.ScanResult
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Infrared adapter.
 *
 * Transmits IR commands via the phone's IR
 * blaster (if present) or the Nexus Hub's
 * IR transmitter. Uses the existing
 * [com.elysium.nexus.fabric.infrared.IrLearner]
 * for learning and the IR database for
 * playback.
 *
 * ## Why a stub
 *
 * The IR transmitter hardware abstraction
 * (Android ConsumerIrManager / Hub IR LED)
 * is wired in Phase 3+. The adapter
 * interface is ready; the implementation
 * connects to the existing IrLearner + IrProtocol.
 */
class InfraredAdapter : DeviceAdapter {

    override val protocol: Protocol = Protocol.DirectIr
    override val label: String = "Infrared"
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.Level,
        Capability.MediaTransport,
        Capability.Volume,
        Capability.Channel,
        Capability.InputSource
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    override suspend fun start(): AdapterResult { _state.value = AdapterState.Active; return AdapterResult.Ok }
    override suspend fun scan(timeoutMs: Long): ScanResult = ScanResult.Error(ErrorCode.HardwareUnavailable, "IR scan requires an IR blaster (phone) or Hub IR transmitter.")
    override suspend fun read(deviceId: DeviceId): ReadResult = ReadResult.Error(ErrorCode.UnsupportedOperation, "IR is transmit-only; no state to read.")
    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult = WriteResult.Error(ErrorCode.NotStarted, "IR adapter is a stub.")
    override suspend fun subscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Error(ErrorCode.UnsupportedOperation, "IR is fire-and-forget; no state subscription.")
    override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
    override suspend fun stop(): AdapterResult { _state.value = AdapterState.Released; return AdapterResult.Ok }
}
