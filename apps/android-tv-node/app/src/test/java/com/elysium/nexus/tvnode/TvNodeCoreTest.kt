package com.elysium.nexus.tvnode

import com.elysium.nexus.tvnode.access.TvAccessLevel
import com.elysium.nexus.tvnode.observe.TvActionExecutor
import com.elysium.nexus.tvnode.observe.TvEffector
import com.elysium.nexus.tvnode.observe.TvObservationEngine
import com.elysium.nexus.tvnode.observe.VolumeActionInterpreter
import com.elysium.nexus.tvnode.observe.VolumeObservation
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private fun vol(raw: Int, max: Int = 15, muted: Boolean = false, fixed: Boolean = false) = VolumeObservation(
    rawVolume = raw, maxVolume = max,
    level = if (max > 0) raw.toFloat() / max else 0f,
    isMuted = muted, isVolumeFixed = fixed
)

private class FakeEffector(var success: Boolean = true) : TvEffector {
    val calls = AtomicInteger(0)
    override fun adjustStreamVolume(direction: VolumeActionInterpreter.VolumeDirectionAction): Boolean {
        calls.incrementAndGet()
        return success
    }
    override fun supportsGlobalTvKeys(): Boolean = false
}

/** Mutable observation engine for the before/after choreography. */
private class MutableEngine : TvObservationEngine {
    var current: VolumeObservation? = null
    var sessionActive: Boolean = false
    override fun observeVolume(): VolumeObservation? = current
    override fun isMediaSessionActive(): Boolean = sessionActive
}

class VolumeActionInterpreterTest {

    @Test
    fun `volume up is confirmed only when raw volume strictly increased`() {
        assertEquals(
            VolumeActionInterpreter.Verdict.Confirmed,
            VolumeActionInterpreter.interpretVolumeChange(vol(5), vol(6), VolumeActionInterpreter.VolumeDirectionAction.Up)
        )
        assertEquals(
            VolumeActionInterpreter.Verdict.Unverified,
            VolumeActionInterpreter.interpretVolumeChange(vol(5), vol(5), VolumeActionInterpreter.VolumeDirectionAction.Up)
        )
        assertEquals(
            VolumeActionInterpreter.Verdict.Unverified,
            VolumeActionInterpreter.interpretVolumeChange(vol(5), vol(4), VolumeActionInterpreter.VolumeDirectionAction.Up)
        )
    }

    @Test
    fun `volume down is confirmed only when raw volume strictly decreased`() {
        assertEquals(
            VolumeActionInterpreter.Verdict.Confirmed,
            VolumeActionInterpreter.interpretVolumeChange(vol(6), vol(5), VolumeActionInterpreter.VolumeDirectionAction.Down)
        )
        assertEquals(
            VolumeActionInterpreter.Verdict.Unverified,
            VolumeActionInterpreter.interpretVolumeChange(vol(5), vol(6), VolumeActionInterpreter.VolumeDirectionAction.Down)
        )
    }

    @Test
    fun `mute is confirmed only when mute state actually changed`() {
        assertEquals(
            VolumeActionInterpreter.Verdict.Confirmed,
            VolumeActionInterpreter.interpretVolumeChange(vol(8, muted = false), vol(8, muted = true), VolumeActionInterpreter.VolumeDirectionAction.Mute)
        )
        assertEquals(
            VolumeActionInterpreter.Verdict.Unverified,
            VolumeActionInterpreter.interpretVolumeChange(vol(8, muted = true), vol(8, muted = true), VolumeActionInterpreter.VolumeDirectionAction.Mute)
        )
    }

    @Test
    fun `unmute is confirmed when mute state cleared`() {
        assertEquals(
            VolumeActionInterpreter.Verdict.Confirmed,
            VolumeActionInterpreter.interpretVolumeChange(vol(8, muted = true), vol(8, muted = false), VolumeActionInterpreter.VolumeDirectionAction.Mute)
        )
    }

    @Test
    fun `fixed volume TV makes every volume action unsupported`() {
        for (action in VolumeActionInterpreter.VolumeDirectionAction.entries) {
            assertEquals(
                VolumeActionInterpreter.Verdict.Unsupported,
                VolumeActionInterpreter.interpretVolumeChange(vol(5, fixed = true), vol(5, fixed = true), action)
            )
        }
    }

    @Test
    fun `missing observation sides are never guessed`() {
        assertEquals(
            VolumeActionInterpreter.Verdict.Unverified,
            VolumeActionInterpreter.interpretVolumeChange(null, vol(6), VolumeActionInterpreter.VolumeDirectionAction.Up)
        )
        assertEquals(
            VolumeActionInterpreter.Verdict.Unverified,
            VolumeActionInterpreter.interpretVolumeChange(vol(5), null, VolumeActionInterpreter.VolumeDirectionAction.Up)
        )
    }
}

class TvActionExecutorTest {

    @Test
    fun `executor fires once and confirms only with observed delta`() {
        val engine = MutableEngine()
        engine.current = vol(5)
        val effector = FakeEffector()
        val executor = TvActionExecutor(engine, effector, TvAccessLevel.ENHANCED_USER_GRANTED)

        val unverified = executor.executeVolume(VolumeActionInterpreter.VolumeDirectionAction.Up)
        assertEquals(VolumeActionInterpreter.Verdict.Unverified, unverified)
        assertEquals(1, effector.calls.get())

        engine.current = vol(6)
        assertEquals(
            VolumeActionInterpreter.Verdict.Confirmed,
            executor.executeVolume(VolumeActionInterpreter.VolumeDirectionAction.Up)
        )
        assertEquals(2, effector.calls.get())
    }

    @Test
    fun `executor refuses below enhanced access without touching the effector`() {
        val engine = MutableEngine()
        engine.current = vol(5)
        val effector = FakeEffector()
        val executor = TvActionExecutor(engine, effector, TvAccessLevel.STANDARD)
        assertEquals(
            VolumeActionInterpreter.Verdict.Refused,
            executor.executeVolume(VolumeActionInterpreter.VolumeDirectionAction.Up)
        )
        assertEquals(0, effector.calls.get())
    }

    @Test
    fun `executor never touches fixed volume TVs`() {
        val engine = MutableEngine()
        engine.current = vol(5, fixed = true)
        val effector = FakeEffector()
        val executor = TvActionExecutor(engine, effector, TvAccessLevel.ENHANCED_USER_GRANTED)
        assertEquals(
            VolumeActionInterpreter.Verdict.Unsupported,
            executor.executeVolume(VolumeActionInterpreter.VolumeDirectionAction.Up)
        )
        assertEquals(0, effector.calls.get())
    }

    @Test
    fun `effector failure never yields confirmation`() {
        val engine = MutableEngine()
        engine.current = vol(5)
        val effector = FakeEffector(success = false)
        val executor = TvActionExecutor(engine, effector, TvAccessLevel.ENHANCED_USER_GRANTED)
        assertEquals(
            VolumeActionInterpreter.Verdict.Unverified,
            executor.executeVolume(VolumeActionInterpreter.VolumeDirectionAction.Up)
        )
        assertEquals(1, effector.calls.get())
    }

    @Test
    fun `no observation available never yields confirmation`() {
        val engine = MutableEngine()
        engine.current = null
        val effector = FakeEffector()
        val executor = TvActionExecutor(engine, effector, TvAccessLevel.ENHANCED_USER_GRANTED)
        assertEquals(
            VolumeActionInterpreter.Verdict.Unverified,
            executor.executeVolume(VolumeActionInterpreter.VolumeDirectionAction.Up)
        )
        assertEquals(0, effector.calls.get())
    }
}