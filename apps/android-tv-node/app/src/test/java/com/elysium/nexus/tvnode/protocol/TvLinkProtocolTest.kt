package com.elysium.nexus.tvnode.protocol

import com.elysium.nexus.tvnode.canonical.DeviceId
import com.elysium.nexus.tvnode.canonical.Direction
import com.elysium.nexus.tvnode.canonical.UniversalAction
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire protocol tests — the §11 Universal Protocol surface on the TV Node
 * (PR2 slice 3). Byte-parity discipline: these layouts are golden vectors for
 * the phone mirror; if the controller ever produces a different byte stream
 * for the same envelope, the wire contract has diverged.
 */
class TvLinkProtocolTest {

    // ------------------------------------------------------------------
    // Framing
    // ------------------------------------------------------------------

    @Test
    fun `frame length includes the type byte but not its own length field`() {
        val payload = "Navigate(UP)".toByteArray()
        val wire = TvLinkProtocol.encodeFrame(TvLinkProtocol.FrameType.ACTION, payload)
        assertEquals(4 + 1 + payload.size, wire.size)
        val len = ((wire[0].toLong() and 0xFF) shl 24) or
            ((wire[1].toLong() and 0xFF) shl 16) or
            ((wire[2].toLong() and 0xFF) shl 8) or
            (wire[3].toLong() and 0xFF)
        assertEquals(1 + payload.size, len.toInt())
    }

    @Test
    fun `action frame round-trips through the streaming reader`() {
        val payload = TvLinkProtocol.encodeEnvelope(sampleEnvelope())
        val wire = TvLinkProtocol.encodeFrame(TvLinkProtocol.FrameType.ACTION, payload)
        val result = TvLinkProtocol.readFrame(wire)
        when (result) {
            is TvLinkProtocol.FrameParseResult.Ok -> {
                assertEquals(TvLinkProtocol.FrameType.ACTION, result.frame.type)
                assertArrayEquals(payload, result.frame.payload)
                assertEquals(wire.size, result.consumedBytes)
            }
            else -> throw AssertionError("expected Ok, got $result")
        }
    }

    @Test
    fun `partial frame reports need-more`() {
        val wire = TvLinkProtocol.encodeFrame(TvLinkProtocol.FrameType.HEARTBEAT)
        assertTrue(TvLinkProtocol.readFrame(wire.copyOfRange(0, 3)) is TvLinkProtocol.FrameParseResult.NeedMore)
        assertTrue(TvLinkProtocol.readFrame(wire.copyOfRange(0, 4)) is TvLinkProtocol.FrameParseResult.NeedMore)
    }

    @Test
    fun `unknown frame type is rejected`() {
        val wire = byteArrayOf(0, 0, 0, 2, 0x7F.toByte(), 0)
        val result = TvLinkProtocol.readFrame(wire)
        assertTrue(result is TvLinkProtocol.FrameParseResult.Error)
    }

    @Test
    fun `absurd frame length is rejected`() {
        val wire = byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val result = TvLinkProtocol.readFrame(wire)
        assertTrue(result is TvLinkProtocol.FrameParseResult.Error)
    }

    @Test
    fun `consecutive frames parse cleanly from one buffer`() {
        val f1 = TvLinkProtocol.encodeFrame(TvLinkProtocol.FrameType.HEARTBEAT)
        val e2 = TvLinkProtocol.encodeEnvelope(sampleEnvelope())
        val f2 = TvLinkProtocol.encodeFrame(TvLinkProtocol.FrameType.ACTION, e2)
        val buf = f1 + f2
        val r1 = TvLinkProtocol.readFrame(buf)
        val r2 = when (r1) {
            is TvLinkProtocol.FrameParseResult.Ok -> TvLinkProtocol.readFrame(buf.copyOfRange(r1.consumedBytes, buf.size))
            else -> throw AssertionError("first frame should parse")
        }
        when (r2) {
            is TvLinkProtocol.FrameParseResult.Ok -> assertEquals(TvLinkProtocol.FrameType.ACTION, r2.frame.type)
            else -> throw AssertionError("second frame should parse, got $r2")
        }
    }

    // ------------------------------------------------------------------
    // §11 envelope
    // ------------------------------------------------------------------

    @Test
    fun `envelope round-trips all section-11 fields`() {
        val original = sampleEnvelope()
        val bytes = TvLinkProtocol.encodeEnvelope(original)
        val decoded = TvLinkProtocol.decodeEnvelope(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `envelope with unicode device and context round-trips`() {
        val original = sampleEnvelope().copy(
            deviceId = "TV Sala — TCL 55P755\u00A0CR",
            capabilityContext = "foreground=com.netflix&lang=es",
            authMetadata = "attested:sha256:abc"
        )
        val decoded = TvLinkProtocol.decodeEnvelope(TvLinkProtocol.encodeEnvelope(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `envelope decode rejects trailing garbage`() {
        val bytes = TvLinkProtocol.encodeEnvelope(sampleEnvelope()) + byteArrayOf(0x00)
        assertNull(TvLinkProtocol.decodeEnvelope(bytes))
    }

    @Test
    fun `envelope decode rejects truncated input`() {
        // Below the 51-byte absolute minimum layout → must be rejected.
        val bytes = ByteArray(50)
        assertNull(TvLinkProtocol.decodeEnvelope(bytes))
    }

    @Test
    fun `envelope v2 is rejected until negotiated`() {
        val bytes = TvLinkProtocol.encodeEnvelope(sampleEnvelope())
        bytes[0] = 2
        assertNull(TvLinkProtocol.decodeEnvelope(bytes))
    }

    @Test
    fun `envelope is deterministic across encodes`() {
        val e = sampleEnvelope()
        assertArrayEquals(TvLinkProtocol.encodeEnvelope(e), TvLinkProtocol.encodeEnvelope(e))
    }

    // ------------------------------------------------------------------
    // §11 response states
    // ------------------------------------------------------------------

    @Test
    fun `all seven section-11 response states have distinct stable codes`() {
        val codes = TvLinkProtocol.TvResponseState.entries.map { it.code }.toSet()
        assertEquals(7, codes.size)
        assertEquals(7, TvLinkProtocol.TvResponseState.entries.size)
        TvLinkProtocol.TvResponseState.entries.forEach {
            assertEquals(it, TvLinkProtocol.TvResponseState.fromCode(it.code))
        }
    }

    @Test
    fun `response body round-trips state messageId and detail`() {
        val body = TvLinkProtocol.TvResponseBody(
            TvLinkProtocol.TvResponseState.OBSERVED,
            42L,
            "volume moved 20 -> 21"
        )
        assertEquals(body, TvLinkProtocol.decodeResponseBody(TvLinkProtocol.encodeResponseBody(body)))
    }

    @Test
    fun `response body decode rejects unknown state`() {
        val bytes = TvLinkProtocol.encodeResponseBody(
            TvLinkProtocol.TvResponseBody(TvLinkProtocol.TvResponseState.RECEIVED, 1L)
        )
        bytes[0] = 0x7F // not a §11 state
        assertNull(TvLinkProtocol.decodeResponseBody(bytes))
    }

    // ------------------------------------------------------------------
    // Semantic action codec (universal action, never an Android keycode)
    // ------------------------------------------------------------------

    @Test
    fun `navigate direction survives the wire codec`() {
        Direction.entries.forEach { d ->
            val action: UniversalAction = UniversalAction.Navigate(activeDeviceId(), d)
            val wire = TvLinkProtocol.encodeAction(action)
            val decoded = TvLinkProtocol.decodeAction(wire, activeDeviceId().value)
            assertWireRoundTrip(wire, decoded)
        }
    }

    @Test
    fun `full universal action tree survives the wire codec`() {
        val actions: List<UniversalAction> = listOf(
            UniversalAction.PowerOn(activeDeviceId()),
            UniversalAction.PowerOff(activeDeviceId()),
            UniversalAction.PowerToggle(activeDeviceId()),
            UniversalAction.VolumeUp(activeDeviceId()),
            UniversalAction.VolumeDown(activeDeviceId()),
            UniversalAction.Mute(activeDeviceId()),
            UniversalAction.SetVolume(activeDeviceId(), 0.42f),
            UniversalAction.ChannelUp(activeDeviceId()),
            UniversalAction.ChannelDown(activeDeviceId()),
            UniversalAction.InputSelect(activeDeviceId(), "hdmi2"),
            UniversalAction.MediaPlay(activeDeviceId()),
            UniversalAction.MediaPause(activeDeviceId()),
            UniversalAction.MediaStop(activeDeviceId()),
            UniversalAction.MediaNext(activeDeviceId()),
            UniversalAction.MediaPrevious(activeDeviceId()),
            UniversalAction.Ok(activeDeviceId()),
            UniversalAction.Back(activeDeviceId()),
            UniversalAction.Home(activeDeviceId()),
            UniversalAction.Menu(activeDeviceId()),
            UniversalAction.SetTemperature(activeDeviceId(), 21.5f),
            UniversalAction.SetFanSpeed(activeDeviceId(), 0.75f),
            UniversalAction.SetMode(activeDeviceId(), com.elysium.nexus.tvnode.canonical.ClimateMode.Cool),
            UniversalAction.Custom(activeDeviceId(), "app", mapOf("pkg" to "com.netflix")),
        )
        actions.forEach { action ->
            val wire = TvLinkProtocol.encodeAction(action)
            val decoded = TvLinkProtocol.decodeAction(wire, activeDeviceId().value)
            assertWireRoundTrip(wire, decoded)
        }
    }

    /**
     * The wire contract, not JVM-object identity: encode → decode → encode
     * must reproduce identical bytes. The UniversalAction includes
     * timestampNs/correlationId which are envelope concerns (messageId), NOT
     * wire action fields — so equality is asserted on the wire image.
     */
    private fun assertWireRoundTrip(wire: TvLinkProtocol.TvWireAction, decoded: UniversalAction?) {
        val reEncoded = decoded?.let { TvLinkProtocol.encodeAction(it) }
        assertEquals(wire, reEncoded)
    }

    @Test
    fun `forward-compat actions decode to null instead of a silent invention`() {
        val nullDecoding: List<TvLinkProtocol.TvActionCode> = listOf(
            TvLinkProtocol.TvActionCode.TEXT_COMMIT,
            TvLinkProtocol.TvActionCode.SEARCH,
            TvLinkProtocol.TvActionCode.OPEN_APP,
        )
        nullDecoding.forEach { code ->
            val decoded = TvLinkProtocol.decodeAction(
                TvLinkProtocol.TvWireAction(code, stringParam = "whatever"),
                activeDeviceId().value
            )
            assertNull("$code must decode to null, never a silent success", decoded)
        }
    }

    @Test
    fun `action codes are distinct across the whole enumeration`() {
        val bytes = TvLinkProtocol.TvActionCode.entries.map { it.byte }.toSet()
        assertEquals(TvLinkProtocol.TvActionCode.entries.size, bytes.size)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun sampleEnvelope(): TvLinkProtocol.TvEnvelope = TvLinkProtocol.TvEnvelope(
        protocolVersion = 1,
        messageId = 7L,
        connectionId = 1234L,
        deviceId = activeDeviceId().value,
        action = TvLinkProtocol.TvWireAction(TvLinkProtocol.TvActionCode.VOLUME_UP),
        timestampMillis = 1_752_700_000_000L,
        deadlineMillis = 1_752_700_001_000L,
        sequenceNumber = 3L,
        capabilityContext = "foreground=com.netflix",
        authMetadata = ""
    )

    private fun activeDeviceId(): DeviceId = DeviceId("6f1b8e2d-9a3c-4dae-b2c9-110022334455")
}
