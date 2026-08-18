package com.elysium.nexus.core.oracle

import com.elysium.nexus.core.oracle.IROracleEngine.OracleCandidate
import com.elysium.nexus.core.oracle.IROracleEngine.OracleResult
import com.elysium.nexus.core.oracle.IROracleEngine.OracleVerdict
import com.elysium.nexus.core.oracle.IROracleEngine.TrialRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 25 — the software-only IR oracle challenge protocol, on pure JVM
 * seams: a scripted fake observer + fake transmitter that only move when the
 * "burst" is fired. No hardware, no mocking framework — the logic itself is
 * under test.
 */
class IROracleEngineTest {

    /** Fake TV: volume state that responds ONLY to a fired signal. */
    private class FakeTv(
        var rawVolume: Int = 10,
        var muted: Boolean = false,
        private val max: Int = 50
    ) : OracleObserver {
        var bursts = 0

        fun expectVolumeUpEffect() {
            if (rawVolume < max) rawVolume++
        }

        fun expectVolumeDownEffect() {
            if (rawVolume > 0) rawVolume--
        }

        fun expectMuteEffect() {
            muted = !muted
        }

        override fun observe(): VolumeProbeSnapshot? = VolumeProbeSnapshot(rawVolume, muted)
    }

    private class FakeTransmitter(
        private val tx: (String) -> Boolean = { true }
    ) : OracleTransmitter {
        var firedSignals = mutableListOf<String>()

        override fun transmit(signalId: String, carrierHz: Int, waveform: List<Int>): Boolean {
            firedSignals += signalId
            return tx(signalId)
        }
    }

    private fun tvSignalPair(
        tv: FakeTv,
        actionKey: String,
        onSignal: (String) -> Unit = {}
    ): Pair<FakeTransmitter, OracleCandidate> {
        val transmitter = FakeTransmitter { signal ->
            when (signal) {
                "vol-up" -> if (actionKey == IROracleEngine.ACTION_VOLUME_UP) tv.expectVolumeUpEffect()
                "vol-up-back" -> if (actionKey == IROracleEngine.ACTION_VOLUME_UP) tv.expectVolumeDownEffect()
                "vol-down" -> if (actionKey == IROracleEngine.ACTION_VOLUME_DOWN) tv.expectVolumeDownEffect()
                "vol-down-back" -> if (actionKey == IROracleEngine.ACTION_VOLUME_DOWN) tv.expectVolumeUpEffect()
                "mute" -> if (actionKey == IROracleEngine.ACTION_MUTE) tv.expectMuteEffect()
                "mute-back" -> if (actionKey == IROracleEngine.ACTION_MUTE) tv.expectMuteEffect()
            }
            onSignal(signal)
            true
        }
        val candidate = when (actionKey) {
            IROracleEngine.ACTION_VOLUME_UP -> OracleCandidate(
                IROracleEngine.ACTION_VOLUME_UP, "vol-up", "vol-up-back", 38_000, listOf(100, -100, 200)
            )
            IROracleEngine.ACTION_VOLUME_DOWN -> OracleCandidate(
                IROracleEngine.ACTION_VOLUME_DOWN, "vol-down", "vol-down-back", 38_000, listOf(100, -100, 200)
            )
            else -> OracleCandidate(
                IROracleEngine.ACTION_MUTE, "mute", "mute-back", 38_000, listOf(100, -100, 200)
            )
        }
        return transmitter to candidate
    }

    /** Scripted observer that reports a stale/fixed volume (no effect possible). */
    private class StaleTv(private val probe: VolumeProbeSnapshot) : OracleObserver {
        override fun observe(): VolumeProbeSnapshot? = probe
    }

    @Test
    fun `unanimous change plus reversal confirms`() {
        val tv = FakeTv(rawVolume = 10)
        val (tx, candidate) = tvSignalPair(tv, IROracleEngine.ACTION_VOLUME_UP)
        val engine = IROracleEngine(tx, tv)

        val result = engine.run(candidate, trials = 3)

        val verdict = result.verdict as OracleVerdict.Confirmed
        assertEquals(3, verdict.trialsTotal)
        assertEquals(3, verdict.trialsOk)
        assertEquals(3, result.trials.size)
        assertTrue(result.trials.all { it.passed })
        // every trial really sent both bursts (change + reversal)
        assertEquals(6, tx.firedSignals.size)
    }

    @Test
    fun `volume down follows the same causality ladder`() {
        val tv = FakeTv(rawVolume = 10)
        val (tx, candidate) = tvSignalPair(tv, IROracleEngine.ACTION_VOLUME_DOWN)
        val engine = IROracleEngine(tx, tv)

        val result = engine.run(candidate, trials = 2)

        val verdict = result.verdict as OracleVerdict.Confirmed
        assertEquals(2, verdict.trialsOk)
        assertEquals(4, tx.firedSignals.size)
    }

    @Test
    fun `mute confirms when the toggle flips and restores exactly`() {
        val tv = FakeTv(rawVolume = 10, muted = false)
        val (tx, candidate) = tvSignalPair(tv, IROracleEngine.ACTION_MUTE)
        val engine = IROracleEngine(tx, tv)

        val result = engine.run(candidate, trials = 2)

        val verdict = result.verdict as OracleVerdict.Confirmed
        assertEquals(2, verdict.trialsOk)
        assertEquals(
            "mute-back",
            tx.firedSignals.last()
        )
    }

    @Test
    fun `stale tv volume demotes the whole run to unconfirmed`() {
        val stale = StaleTv(VolumeProbeSnapshot(10, false))
        val transmitter = FakeTransmitter { true }
        val candidate = OracleCandidate(
            IROracleEngine.ACTION_VOLUME_UP, "vol-up", "vol-up-back", 38_000, listOf(100)
        )
        val engine = IROracleEngine(transmitter, stale)

        val result = engine.run(candidate, trials = 3)

        val verdict = result.verdict as OracleVerdict.Unconfirmed
        assertEquals(0, verdict.trialsOk)
        assertTrue(verdict.firstFailure.contains("no observed change"))
        assertTrue(result.trials.none { it.passed })
    }

    @Test
    fun `missing reversal fails the trial`() {
        val tv = FakeTv(rawVolume = 10)
        val transmitter = FakeTransmitter { signal ->
            if (signal != "vol-up-back") tv.expectVolumeUpEffect()
            true
        }
        val candidate = OracleCandidate(
            IROracleEngine.ACTION_VOLUME_UP, "vol-up", "vol-up-back", 38_000, listOf(100)
        )
        val engine = IROracleEngine(transmitter, tv)

        val result = engine.run(candidate, trials = 1)

        val verdict = result.verdict as OracleVerdict.Unconfirmed
        assertEquals(0, verdict.trialsOk)
        assertTrue(result.trials.single().failReason!!.contains("reversal"))
    }

    @Test
    fun `no observation lane is unsupported not unconfirmed`() {
        val blind = object : OracleObserver {
            override fun observe(): VolumeProbeSnapshot? = null
        }
        val transmitter = FakeTransmitter { true }
        val candidate = OracleCandidate(
            IROracleEngine.ACTION_VOLUME_UP, "vol-up", "vol-up-back", 38_000, listOf(100)
        )
        val engine = IROracleEngine(transmitter, blind)

        val result = engine.run(candidate, trials = 3)

        assertTrue(result.verdict is OracleVerdict.Unsupported)
        assertTrue((result.verdict as OracleVerdict.Unsupported).reason.contains("no observation lane"))
    }

    @Test
    fun `missing inversion signal is rejected before any burst`() {
        val tv = FakeTv(rawVolume = 10)
        val transmitter = FakeTransmitter { true }
        val candidate = OracleCandidate(
            IROracleEngine.ACTION_VOLUME_UP, "vol-up", "", 38_000, listOf(100)
        )
        val engine = IROracleEngine(transmitter, tv)

        val result = engine.run(candidate, trials = 3)

        assertTrue(result.verdict is OracleVerdict.Unsupported)
        assertTrue((result.verdict as OracleVerdict.Unsupported).reason.contains("reversal"))
        assertEquals(0, transmitter.firedSignals.size)
    }

    @Test
    fun `refused transmitter is unsupported with the honest reason`() {
        val tv = FakeTv(rawVolume = 10)
        val blocking = FakeTransmitter { false }
        val candidate = OracleCandidate(
            IROracleEngine.ACTION_VOLUME_UP, "vol-up", "vol-up-back", 38_000, listOf(100)
        )
        val engine = IROracleEngine(blocking, tv)

        val result = engine.run(candidate, trials = 1)

        assertTrue(result.verdict is OracleVerdict.Unsupported)
        assertTrue((result.verdict as OracleVerdict.Unsupported).reason.contains("transmitter refused"))
    }

    @Test
    fun `physicalSha256 is deterministic per candidate`() {
        val a = OracleCandidate("VOLUME_UP", "s1", "s1-back", 38_000, listOf(1, -1, 2))
        val b = OracleCandidate("VOLUME_UP", "s1", "s1-back", 38_000, listOf(1, -1, 2))
        val c = OracleCandidate("VOLUME_UP", "s1", "s1-back", 38_000, listOf(1, -1, 3))

        assertEquals(a.physicalSha256, b.physicalSha256)
        assertTrue(a.physicalSha256 != c.physicalSha256)
    }

    @Test
    fun `evidence event only from a unanimous confirmed run`() {
        val tv = FakeTv(rawVolume = 10)
        val (tx, candidate) = tvSignalPair(tv, IROracleEngine.ACTION_VOLUME_UP)
        val engine = IROracleEngine(tx, tv)

        val confirmed = engine.run(candidate, trials = 2)
        val event = engine.toEvidenceEvent(
            candidate, confirmed, tvDeviceId = "tv-1", catalogBuildId = "build-42"
        )

        assertEquals("software-oracle", event!!.source)
        assertEquals("VOLUME_UP", event.actionKey)
        assertEquals(candidate.physicalSha256, event.physicalSha256)
        assertEquals(2, event.trialsTotal)
        assertEquals(2, event.trialsOk)
        assertTrue(event.beforeRawVolume < event.afterRawVolume)
        // the reversal truly restored the prior state
        assertEquals(event.beforeRawVolume, event.restoredRawVolume)

        val stale = staleRun(tv)
        assertNull(engine.toEvidenceEvent(candidate, stale, "tv-1", "build-42"))
    }

    private fun staleRun(tv: FakeTv): OracleResult {
        // a run whose observer sees no change: unconfirmed by construction
        val stale = StaleTv(VolumeProbeSnapshot(10, false))
        val tx = FakeTransmitter { true }
        val c = OracleCandidate(IROracleEngine.ACTION_VOLUME_UP, "vol-up", "vol-up-back", 38_000, listOf(100))
        return IROracleEngine(tx, stale).run(c, trials = 2)
    }
}