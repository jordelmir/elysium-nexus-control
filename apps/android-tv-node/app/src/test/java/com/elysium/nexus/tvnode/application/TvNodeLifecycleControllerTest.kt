package com.elysium.nexus.tvnode.application

import org.junit.Assert.assertEquals
import org.junit.Test

class TvNodeLifecycleControllerTest {

    @Test
    fun `no listener bound never touches discovery`() {
        assertEquals(
            TvNodeLifecycleController.Verdict.Noop,
            TvNodeLifecycleController.decide(
                listenerBound = false,
                networkAvailable = true,
                discoveryRegistered = false
            )
        )
        assertEquals(
            TvNodeLifecycleController.Verdict.Noop,
            TvNodeLifecycleController.decide(
                listenerBound = false,
                networkAvailable = false,
                discoveryRegistered = true
            )
        )
    }

    @Test
    fun `re-registers discovery only when listener bound network up and registration missing`() {
        assertEquals(
            TvNodeLifecycleController.Verdict.ReRegisterDiscovery,
            TvNodeLifecycleController.decide(
                listenerBound = true,
                networkAvailable = true,
                discoveryRegistered = false
            )
        )
        // Registered + up: idempotent, no action.
        assertEquals(
            TvNodeLifecycleController.Verdict.Noop,
            TvNodeLifecycleController.decide(
                listenerBound = true,
                networkAvailable = true,
                discoveryRegistered = true
            )
        )
    }

    @Test
    fun `network loss stops discovery when registered`() {
        assertEquals(
            TvNodeLifecycleController.Verdict.StopDiscovery,
            TvNodeLifecycleController.decide(
                listenerBound = true,
                networkAvailable = false,
                discoveryRegistered = true
            )
        )
        assertEquals(
            TvNodeLifecycleController.Verdict.Noop,
            TvNodeLifecycleController.decide(
                listenerBound = true,
                networkAvailable = false,
                discoveryRegistered = false
            )
        )
    }
}