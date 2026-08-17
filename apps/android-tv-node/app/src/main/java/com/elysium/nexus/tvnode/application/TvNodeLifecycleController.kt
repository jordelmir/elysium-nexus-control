package com.elysium.nexus.tvnode.application

/**
 * Master Order v0.10 Phase 24 — TV Node lifecycle.
 *
 * The control surface is a REAL network object: it can die with the network,
 * its NSD registration can be lost, and the process can be recycled. This
 * controller decides — from observable state only — whether discovery must be
 * (re)registered, (re)started, or stopped. Pure decision logic, JVM-tested;
 * the thin Android wiring lives in [TvNodeApp].
 *
 * Rules (fail-closed):
 * - No listener bound  → never register discovery.
 * - Network available + listener bound + discovery not registered → RE_REGISTER.
 * - Network lost → STOP discovery (advertise nothing while unreachable).
 * - Nothing else mutates the surface (idempotent — re-deciding yields the
 *   same verdict until state changes).
 */
object TvNodeLifecycleController {

    sealed class Verdict {
        /** Start/register discovery now (surface up). */
        object ReRegisterDiscovery : Verdict()

        /** Unregister discovery (surface down). */
        object StopDiscovery : Verdict()

        /** Nothing to do. */
        object Noop : Verdict()
    }

    fun decide(
        listenerBound: Boolean,
        networkAvailable: Boolean,
        discoveryRegistered: Boolean
    ): Verdict = when {
        !listenerBound -> Verdict.Noop
        !networkAvailable -> if (discoveryRegistered) Verdict.StopDiscovery else Verdict.Noop
        !discoveryRegistered -> Verdict.ReRegisterDiscovery
        else -> Verdict.Noop
    }
}