package com.elysium.nexus.tvnode.protocol

import com.elysium.nexus.tvnode.canonical.ClimateMode
import com.elysium.nexus.tvnode.canonical.DeviceId
import com.elysium.nexus.tvnode.canonical.Direction
import com.elysium.nexus.tvnode.canonical.UniversalAction
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TvLinkProtocol — the phone ↔ TV Node wire contract (PR2 slice 3, §11).
 *
 * This file is a pure-data module: no Android or network dependencies, so it
 * is JVM-testable from the standard test sourceset (same discipline as the
 * controller's `MacProtocol`).
 *
 * It owns the §11 "Universal Protocol" surface:
 *   - length-prefixed frame framing (byte-identical layout to MacProtocol,
 *     so the phone mirror reuses one codec),
 *   - the §11 envelope (protocolVersion, messageId, connectionId, deviceId,
 *     action, timestamp, deadline, sequenceNumber, capabilityContext,
 *     auth metadata),
 *   - the §11 TV response states — NEVER collapsed into a boolean —
 *   - the semantic action code (UniversalAction, NEVER an Android keycode).
 *
 * Wire format (same as MacProtocol):
 * ```
 * ┌────────────┬────────┬───────────────────┐
 * │ length u32 │ type u8│      payload       │
 * │ (big-end)  │        │ (length - 1 bytes) │
 * └────────────┴────────┴───────────────────┘
 * ```
 * `length` is the total frame size INCLUDING the type byte but EXCLUDING
 * the 4 length bytes themselves.
 *
 * Frame types are distinct from the MAC link (0x10..): the TV node never
 * talks to the Mac agent, but keeping the ranges separate prevents an
 * innocent frame-type collision if the two channels ever share a socket.
 */
object TvLinkProtocol {

    const val PROTOCOL_VERSION: Int = 1
    const val MAX_FRAME_SIZE: Int = 1_048_576

    /** Frame type bytes for the TV link (0x10..; MAC link owns 0x01..0x0F). */
    enum class FrameType(val byte: Byte) {
        HELLO(0x10),
        HELLO_ACK(0x11),
        NONCE_ECHO_ACK(0x12),
        CHANNEL_READY(0x13),
        ACTION(0x14),
        RESPONSE(0x15),
        HEARTBEAT(0x16),
        GOODBYE(0x17),
        ERROR(0x18);

        companion object {
            private val BY_INDEX = entries.associateBy { it.byte }
            fun fromByte(b: Byte): FrameType? = BY_INDEX[b]
        }
    }

    /**
     * §11 TV response states. A verdict is NEVER a boolean: the TV must
     * say clearly whether the action was merely received, accepted,
     * executed without proof, executed AND observed, rejected by policy,
     * or simply unsupported on this device.
     */
    enum class TvResponseState(val code: Byte) {
        RECEIVED(0x01),
        ACCEPTED(0x02),
        EXECUTED(0x03),
        OBSERVED(0x04),
        FAILED(0x05),
        UNSUPPORTED(0x06),
        PERMISSION_REQUIRED(0x07);

        companion object {
            private val BY_CODE = entries.associateBy { it.code }
            fun fromCode(b: Byte): TvResponseState? = BY_CODE[b]
        }
    }

    /** Semantic action codes — the wire names from §11 (+ full UniversalAction tree). */
    enum class TvActionCode(val byte: Byte) {
        NAVIGATE_UP(0x01),
        NAVIGATE_DOWN(0x02),
        NAVIGATE_LEFT(0x03),
        NAVIGATE_RIGHT(0x04),
        OK(0x05),
        BACK(0x06),
        HOME(0x07),
        MENU(0x08),
        VOLUME_UP(0x09),
        VOLUME_DOWN(0x0A),
        MUTE(0x0B),
        SET_VOLUME(0x0C),
        CHANNEL_UP(0x0D),
        CHANNEL_DOWN(0x0E),
        INPUT_SELECT(0x0F),
        MEDIA_PLAY(0x10),
        MEDIA_PAUSE(0x11),
        MEDIA_STOP(0x12),
        MEDIA_NEXT(0x13),
        MEDIA_PREVIOUS(0x14),
        POWER_ON(0x15),
        POWER_OFF(0x16),
        POWER_TOGGLE(0x17),
        SET_TEMPERATURE(0x18),
        SET_FAN_SPEED(0x19),
        SET_MODE(0x1A),
        TEXT_COMMIT(0x1B),
        SEARCH(0x1C),
        OPEN_APP(0x1D),
        CUSTOM(0x7F);

        companion object {
            private val BY_BYTE = entries.associateBy { it.byte }
            fun fromByte(b: Byte): TvActionCode? = BY_BYTE[b]
        }
    }

    /** A semantic action on the wire: code + optional int/string parameters. */
    data class TvWireAction(
        val code: TvActionCode,
        val intParam: Int = 0,
        val stringParam: String = ""
    )

    /**
     * §11 envelope — every field required by the order. Deterministic,
     * versioned binary layout (see [encodeEnvelope]/[decodeEnvelope]) so the
     * phone mirror can be byte-parity tested (golden vectors, Phase-32 pattern).
     */
    data class TvEnvelope(
        val protocolVersion: Int,
        val messageId: Long,
        val connectionId: Long,
        val deviceId: String,
        val action: TvWireAction,
        val timestampMillis: Long,
        val deadlineMillis: Long,
        val sequenceNumber: Long,
        val capabilityContext: String,
        val authMetadata: String
    )

    /** A RESPONSE frame body: state + the messageId it answers + optional detail. */
    data class TvResponseBody(
        val state: TvResponseState,
        val answerToMessageId: Long,
        val detail: String = ""
    )

    /** Decoded frame + how many bytes it consumed (mirrors MacProtocol results). */
    data class Frame(
        val type: FrameType,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return type == other.type && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int = 31 * type.hashCode() + payload.contentHashCode()
    }

    // ------------------------------------------------------------------
    // Framing (byte-identical twin of MacProtocol)
    // ------------------------------------------------------------------

    /** Encodes a frame: `u32 BE length (incl type) | u8 type | payload`. */
    fun encodeFrame(type: FrameType, payload: ByteArray = ByteArray(0)): ByteArray {
        require(payload.size <= MAX_FRAME_SIZE - 1) { "payload too large" }
        val buf = ByteBuffer.allocate(4 + 1 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(1 + payload.size)
        buf.put(type.byte)
        buf.put(payload)
        return buf.array()
    }

    /** Reads one length-prefixed frame from a byte array (streaming-ready). */
    fun readFrame(buffer: ByteArray): FrameParseResult {
        if (buffer.size < 5) return FrameParseResult.NeedMore
        val length = readUInt32BE(buffer, 0)
        if (length < 1 || length > MAX_FRAME_SIZE) {
            return FrameParseResult.Error("refusing frame of length $length (max $MAX_FRAME_SIZE)")
        }
        val total = 4 + length.toInt()
        if (buffer.size < total) return FrameParseResult.NeedMore
        val typeByte = buffer[4]
        val type = FrameType.fromByte(typeByte) ?: return FrameParseResult.Error("unknown frame type $typeByte")
        val payload = ByteArray(length.toInt() - 1)
        System.arraycopy(buffer, 5, payload, 0, payload.size)
        return FrameParseResult.Ok(Frame(type, payload), total)
    }

    sealed class FrameParseResult {
        data class Ok(val frame: Frame, val consumedBytes: Int) : FrameParseResult()
        data object NeedMore : FrameParseResult()
        data class Error(val reason: String) : FrameParseResult()
    }

    // ------------------------------------------------------------------
    // §11 envelope encoding / decoding (deterministic, versioned)
    // ------------------------------------------------------------------

    /**
     * Binary layout (all BE):
     *   u8  protocolVersion
     *   u64 messageId
     *   u64 connectionId
     *   u8  deviceIdLen + deviceId.utf8
     *   u8  actionCode
     *   i32 actionIntParam
     *   u16 actionStringLen + actionStringParam.utf8
     *   u64 timestampMillis
     *   u64 deadlineMillis
     *   u64 sequenceNumber
     *   u8  capabilityContextLen + capabilityContext.utf8
     *   u8  authMetadataLen + authMetadata.utf8
     *
     * Versioning: byte 0 gates the whole layout. Only v1 exists today; a v2
     * that reorders fields is allowed only via a new PROTOCOL_VERSION and the
     * pairing must negotiate it — never silently reinterpret v1 bytes.
     */
    fun encodeEnvelope(e: TvEnvelope): ByteArray {
        if (e.protocolVersion != PROTOCOL_VERSION) {
            throw IllegalArgumentException("unsupported envelope version ${e.protocolVersion}")
        }
        val deviceId = utf8(e.deviceId)
        val stringParam = utf8(e.action.stringParam)
        val capability = utf8(e.capabilityContext)
        val auth = utf8(e.authMetadata)
        validateLen("deviceId", deviceId.size, 255)
        validateLen("actionStringParam", stringParam.size, 65535)
        validateLen("capabilityContext", capability.size, 255)
        validateLen("authMetadata", auth.size, 255)

        val size = 1 + 8 + 8 + (1 + deviceId.size) + 1 + 4 + 2 + stringParam.size + 8 + 8 + 8 + (1 + capability.size) + (1 + auth.size)
        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buf.put(e.protocolVersion.toByte())
        buf.putLong(e.messageId)
        buf.putLong(e.connectionId)
        putLenString(buf, deviceId)
        buf.put(e.action.code.byte)
        buf.putInt(e.action.intParam)
        putLenU16(buf, stringParam)
        buf.putLong(e.timestampMillis)
        buf.putLong(e.deadlineMillis)
        buf.putLong(e.sequenceNumber)
        putLenString(buf, capability)
        putLenString(buf, auth)
        return buf.array()
    }

    /**
     * Decodes an envelope. Returns null on ANY malformed input (short read,
     * unknown version, unknown action code, overlong length) — fail-closed:
     * the caller must REJECT, never guess.
     */
    fun decodeEnvelope(bytes: ByteArray): TvEnvelope? {
        try {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            // Absolute minimum: version(1)+msgId(8)+connId(8)+devLen(1)+code(1)+
            // intParam(4)+strLen(2)+ts(8)+deadline(8)+seq(8)+capLen(1)+authLen(1) = 51.
            if (buf.remaining() < 51) return null
            val version = buf.get().toInt() and 0xFF
            if (version != PROTOCOL_VERSION) return null
            val messageId = buf.getLong()
            val connectionId = buf.getLong()
            val deviceId = readLenString(buf) ?: return null
            val actionCode = TvActionCode.fromByte(buf.get()) ?: return null
            val intParam = buf.getInt()
            val stringParam = readLenU16(buf) ?: return null
            val timestampMillis = buf.getLong()
            val deadlineMillis = buf.getLong()
            val sequenceNumber = buf.getLong()
            val capabilityContext = readLenString(buf) ?: return null
            val authMetadata = readLenString(buf) ?: return null
            if (buf.hasRemaining()) return null
            return TvEnvelope(
                protocolVersion = version,
                messageId = messageId,
                connectionId = connectionId,
                deviceId = deviceId,
                action = TvWireAction(actionCode, intParam, stringParam),
                timestampMillis = timestampMillis,
                deadlineMillis = deadlineMillis,
                sequenceNumber = sequenceNumber,
                capabilityContext = capabilityContext,
                authMetadata = authMetadata
            )
        } catch (e: Exception) {
            return null
        }
    }

    // ------------------------------------------------------------------
    // §11 response body encoding / decoding
    // ------------------------------------------------------------------

    /** RESPONSE payload: `u8 state | u64 answerToMessageId | u8 len + detail`. */
    fun encodeResponseBody(r: TvResponseBody): ByteArray {
        val detail = utf8(r.detail)
        validateLen("response detail", detail.size, 255)
        val buf = ByteBuffer.allocate(1 + 8 + 1 + detail.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(r.state.code)
        buf.putLong(r.answerToMessageId)
        putLenString(buf, detail)
        return buf.array()
    }

    /** Decodes a RESPONSE body; null on malformed input (fail-closed reject). */
    fun decodeResponseBody(bytes: ByteArray): TvResponseBody? {
        try {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            if (buf.remaining() < 10) return null
            val state = TvResponseState.fromCode(buf.get()) ?: return null
            val answerTo = buf.getLong()
            val detail = readLenString(buf) ?: return null
            if (buf.hasRemaining()) return null
            return TvResponseBody(state, answerTo, detail)
        } catch (e: Exception) {
            return null
        }
    }

    // ------------------------------------------------------------------
    // Semantic action codec — UNIVERSAL ACTION, NEVER an Android keycode
    // ------------------------------------------------------------------

    /**
     * Encodes the full UniversalAction sealed tree to wire form. Every leaf
     * the TV node can construct has a code — the codec never drops fields.
     */
    fun encodeAction(a: UniversalAction): TvWireAction = when (a) {
        is UniversalAction.Navigate -> when (a.direction) {
            Direction.Up -> TvWireAction(TvActionCode.NAVIGATE_UP)
            Direction.Down -> TvWireAction(TvActionCode.NAVIGATE_DOWN)
            Direction.Left -> TvWireAction(TvActionCode.NAVIGATE_LEFT)
            Direction.Right -> TvWireAction(TvActionCode.NAVIGATE_RIGHT)
        }
        is UniversalAction.Ok -> TvWireAction(TvActionCode.OK)
        is UniversalAction.Back -> TvWireAction(TvActionCode.BACK)
        is UniversalAction.Home -> TvWireAction(TvActionCode.HOME)
        is UniversalAction.Menu -> TvWireAction(TvActionCode.MENU)
        is UniversalAction.VolumeUp -> TvWireAction(TvActionCode.VOLUME_UP)
        is UniversalAction.VolumeDown -> TvWireAction(TvActionCode.VOLUME_DOWN)
        is UniversalAction.Mute -> TvWireAction(TvActionCode.MUTE)
        is UniversalAction.SetVolume -> TvWireAction(
            TvActionCode.SET_VOLUME,
            intParam = Math.round(a.level * 100f)
        )
        is UniversalAction.ChannelUp -> TvWireAction(TvActionCode.CHANNEL_UP)
        is UniversalAction.ChannelDown -> TvWireAction(TvActionCode.CHANNEL_DOWN)
        is UniversalAction.InputSelect -> TvWireAction(TvActionCode.INPUT_SELECT, stringParam = a.inputId)
        is UniversalAction.MediaPlay -> TvWireAction(TvActionCode.MEDIA_PLAY)
        is UniversalAction.MediaPause -> TvWireAction(TvActionCode.MEDIA_PAUSE)
        is UniversalAction.MediaStop -> TvWireAction(TvActionCode.MEDIA_STOP)
        is UniversalAction.MediaNext -> TvWireAction(TvActionCode.MEDIA_NEXT)
        is UniversalAction.MediaPrevious -> TvWireAction(TvActionCode.MEDIA_PREVIOUS)
        is UniversalAction.PowerOn -> TvWireAction(TvActionCode.POWER_ON)
        is UniversalAction.PowerOff -> TvWireAction(TvActionCode.POWER_OFF)
        is UniversalAction.PowerToggle -> TvWireAction(TvActionCode.POWER_TOGGLE)
        is UniversalAction.SetTemperature -> TvWireAction(
            TvActionCode.SET_TEMPERATURE,
            intParam = Math.round(a.targetCelsius * 10f)
        )
        is UniversalAction.SetFanSpeed -> TvWireAction(
            TvActionCode.SET_FAN_SPEED,
            intParam = Math.round(a.level * 100f)
        )
        is UniversalAction.SetMode -> TvWireAction(TvActionCode.SET_MODE, stringParam = a.mode.name)
        is UniversalAction.Custom -> TvWireAction(
            TvActionCode.CUSTOM,
            stringParam = a.key + if (a.payload.isEmpty()) "" else "|" + a.payload.entries.sortedBy { it.key }
                .joinToString(";") { "${it.key}=${it.value}" }
        )
    }

    /**
     * Decodes a wire action back to the UniversalAction tree. Returns null
     * for forward-compat codes the local tree cannot build yet (TEXT_COMMIT,
     * SEARCH, OPEN_APP) — the executor next slice will surface those as
     * UNSUPPORTED, never as a silent success.
     */
    fun decodeAction(w: TvWireAction, targetDeviceId: String): UniversalAction? = when (w.code) {
        TvActionCode.NAVIGATE_UP -> UniversalAction.Navigate(DeviceId(targetDeviceId), Direction.Up)
        TvActionCode.NAVIGATE_DOWN -> UniversalAction.Navigate(DeviceId(targetDeviceId), Direction.Down)
        TvActionCode.NAVIGATE_LEFT -> UniversalAction.Navigate(DeviceId(targetDeviceId), Direction.Left)
        TvActionCode.NAVIGATE_RIGHT -> UniversalAction.Navigate(DeviceId(targetDeviceId), Direction.Right)
        TvActionCode.OK -> UniversalAction.Ok(DeviceId(targetDeviceId))
        TvActionCode.BACK -> UniversalAction.Back(DeviceId(targetDeviceId))
        TvActionCode.HOME -> UniversalAction.Home(DeviceId(targetDeviceId))
        TvActionCode.MENU -> UniversalAction.Menu(DeviceId(targetDeviceId))
        TvActionCode.VOLUME_UP -> UniversalAction.VolumeUp(DeviceId(targetDeviceId))
        TvActionCode.VOLUME_DOWN -> UniversalAction.VolumeDown(DeviceId(targetDeviceId))
        TvActionCode.MUTE -> UniversalAction.Mute(DeviceId(targetDeviceId))
        TvActionCode.SET_VOLUME -> UniversalAction.SetVolume(DeviceId(targetDeviceId), w.intParam / 100f)
        TvActionCode.CHANNEL_UP -> UniversalAction.ChannelUp(DeviceId(targetDeviceId))
        TvActionCode.CHANNEL_DOWN -> UniversalAction.ChannelDown(DeviceId(targetDeviceId))
        TvActionCode.INPUT_SELECT -> UniversalAction.InputSelect(DeviceId(targetDeviceId), w.stringParam)
        TvActionCode.MEDIA_PLAY -> UniversalAction.MediaPlay(DeviceId(targetDeviceId))
        TvActionCode.MEDIA_PAUSE -> UniversalAction.MediaPause(DeviceId(targetDeviceId))
        TvActionCode.MEDIA_STOP -> UniversalAction.MediaStop(DeviceId(targetDeviceId))
        TvActionCode.MEDIA_NEXT -> UniversalAction.MediaNext(DeviceId(targetDeviceId))
        TvActionCode.MEDIA_PREVIOUS -> UniversalAction.MediaPrevious(DeviceId(targetDeviceId))
        TvActionCode.POWER_ON -> UniversalAction.PowerOn(DeviceId(targetDeviceId))
        TvActionCode.POWER_OFF -> UniversalAction.PowerOff(DeviceId(targetDeviceId))
        TvActionCode.POWER_TOGGLE -> UniversalAction.PowerToggle(DeviceId(targetDeviceId))
        TvActionCode.SET_TEMPERATURE -> UniversalAction.SetTemperature(
            DeviceId(targetDeviceId),
            w.intParam / 10f,
        )
        TvActionCode.SET_FAN_SPEED -> UniversalAction.SetFanSpeed(DeviceId(targetDeviceId), w.intParam / 100f)
        TvActionCode.SET_MODE -> UniversalAction.SetMode(DeviceId(targetDeviceId), ClimateMode.valueOf(w.stringParam))
        TvActionCode.TEXT_COMMIT -> null
        TvActionCode.SEARCH -> null
        TvActionCode.OPEN_APP -> null
        TvActionCode.CUSTOM -> decodeCustom(w, targetDeviceId)
    }

    private fun decodeCustom(w: TvWireAction, targetDeviceId: String): UniversalAction {
        val parts = w.stringParam.split("|", limit = 2)
        val key = parts[0]
        val payload = if (parts.size == 2) {
            parts[1].split(";").mapNotNull { kv ->
                val i = kv.indexOf('=')
                if (i > 0) kv.substring(0, i) to kv.substring(i + 1) else null
            }.toMap()
        } else {
            emptyMap()
        }
        return UniversalAction.Custom(DeviceId(targetDeviceId), key, payload)
    }

    // ------------------------------------------------------------------
    // Small binary helpers
    // ------------------------------------------------------------------

    private fun readUInt32BE(buf: ByteArray, offset: Int): Long =
        ((buf[offset].toLong() and 0xFF) shl 24) or
            ((buf[offset + 1].toLong() and 0xFF) shl 16) or
            ((buf[offset + 2].toLong() and 0xFF) shl 8) or
            (buf[offset + 3].toLong() and 0xFF)

    private fun utf8(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)

    private fun putLenString(buf: ByteBuffer, bytes: ByteArray) {
        buf.put(bytes.size.toByte())
        buf.put(bytes)
    }

    private fun readLenString(buf: ByteBuffer): String? {
        if (buf.remaining() < 1) return null
        val len = buf.get().toInt() and 0xFF
        if (buf.remaining() < len) return null
        val bytes = ByteArray(len)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun putLenU16(buf: ByteBuffer, bytes: ByteArray) {
        buf.putShort(bytes.size.toShort())
        buf.put(bytes)
    }

    private fun readLenU16(buf: ByteBuffer): String? {
        if (buf.remaining() < 2) return null
        val len = buf.getShort().toInt() and 0xFFFF
        if (buf.remaining() < len) return null
        val bytes = ByteArray(len)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun validateLen(name: String, len: Int, max: Int) {
        if (len > max) throw IllegalArgumentException("$name too long: $len bytes (max $max)")
    }
}
