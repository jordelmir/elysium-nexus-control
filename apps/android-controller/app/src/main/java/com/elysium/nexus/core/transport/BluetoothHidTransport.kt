package com.elysium.nexus.core.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.content.Context
import com.elysium.nexus.core.hid.HidDescriptor
import com.elysium.nexus.core.hid.HidReportEncoder
import com.elysium.nexus.core.model.UniversalControllerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * The §17 / §18 Bluetooth HID transport
 * (skeleton).
 *
 * `MASTER_ORDER.md` §18 says: "Usar Android
 * `BluetoothHidDevice` donde exista y funcione.
 * Descriptor propio: `Elysium Nexus Gamepad`. **No
 * falsificar dispositivo comercial.**"
 *
 * Phase 1.11 ships the **skeleton**: the transport
 * uses the existing [HidDescriptor] (the §18
 * `Elysium Nexus Gamepad` descriptor, generated
 * and validated by `tools/hid-descriptor-validator`)
 * and the existing [HidReportEncoder] (the input
 * report format). The skeleton's `sendRealtime`
 * encodes a [UniversalControllerState] into the
 * report and calls `BluetoothHidDevice.sendReport`.
 *
 * The full Bluetooth HID lifecycle (app
 * registration via `BluetoothHidDevice.registerApp`,
 * host connection, QoS, SDP) is the §17
 * multiplexer concern. The skeleton's `start` /
 * `pair` / `connect` methods are the lifecycle
 * hookpoints; a real Bluetooth pairing UI lands
 * in Phase 2 with the first real device.
 */
class BluetoothHidTransport(
    private val context: Context,
    private val scope: CoroutineScope
) : ControllerTransport {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: TransportState
        get() = _state.value

    override val capabilities: TransportCapabilities = TransportCapabilities(
        maxRealtimeFps = 250, // 4ms latency floor
        supportsReliable = true,
        latencyMs = 8, // BT HID, < 8ms typical
        label = "Bluetooth HID"
    )

    override suspend fun start(): TransportResult {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            return TransportResult.Error("Bluetooth is not enabled")
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
            return TransportResult.Error("BluetoothHidDevice requires API 28+")
        }
        // The real registration is
        // `adapter.bluetoothHidDevice.registerApp(...)`,
        // which is a callback-based API. The
        // skeleton skips the actual registration
        // (Phase 2+ wires the real one).
        _state.value = TransportState.INITIALISING
        return TransportResult.Ok
    }

    override suspend fun pair(): PairingResult {
        if (hidDevice == null) {
            return PairingResult.Error("Bluetooth HID app is not registered")
        }
        _state.value = TransportState.PAIRED
        return PairingResult.Ok
    }

    override suspend fun connect(): ConnectionResult {
        if (hostDevice == null) {
            return ConnectionResult.Error("No host connected")
        }
        _state.value = TransportState.CONNECTED
        return ConnectionResult.Ok
    }

    override suspend fun sendReliable(event: ReliableInputEvent): SendResult {
        // Bluetooth HID has no notion of "reliable
        // events" — the protocol is fire-and-forget
        // input reports. The skeleton's
        // `sendReliable` is a no-op (the event is
        // logged for debugging). The real
        // translation lands in Phase 2+: the
        // event maps to a state change in the
        // report (e.g. a profile change is a
        // virtual "system key" press).
        return SendResult.Ok
    }

    override suspend fun sendRealtime(state: UniversalControllerState): SendResult = withContext(Dispatchers.IO) {
        val device = hidDevice ?: return@withContext SendResult.Error("Not connected")
        val host = hostDevice ?: return@withContext SendResult.Error("No host connected")
        try {
            // The §18 `Elysium Nexus Gamepad`
            // descriptor uses a 13-byte input
            // report (1 report ID + 1 buttons +
            // 1 hat switch + 2 sticks + 2 triggers).
            // The encoder handles the canonical-to-
            // report mapping.
            val report = HidReportEncoder.encodeBasicGamepadV1(state)
            device.sendReport(
                host,
                HidDescriptor.BASIC_GAMEPAD_V1_REPORT_ID.toInt() and 0xff,
                report
            )
            SendResult.Ok
        } catch (e: Throwable) {
            SendResult.Error("sendRealtime failed: ${e.message}")
        }
    }

    override suspend fun releaseAll(): SendResult {
        // §38: emit a neutral frame.
        return sendRealtime(UniversalControllerState.neutral())
    }

    override suspend fun disconnect(): DisconnectResult {
        try {
            hidDevice?.unregisterApp()
        } catch (_: Throwable) { /* no-op */ }
        hidDevice = null
        hostDevice = null
        _state.value = TransportState.DISCONNECTED
        return DisconnectResult.Ok
    }

    override suspend fun stop(): TransportResult {
        disconnect()
        _state.value = TransportState.IDLE
        return TransportResult.Ok
    }

    /**
     * Called by the activity when a host connects.
     * The skeleton's API takes the [BluetoothDevice]
     * handle; the real call is
     * `BluetoothHidDevice.Callback.onConnectionStateChanged`.
     */
    fun onHostConnected(device: BluetoothHidDevice, host: BluetoothDevice) {
        hidDevice = device
        hostDevice = host
    }
}
