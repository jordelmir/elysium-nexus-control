package com.elysium.nexus.tvnode.transport

/**
 * TEST-ONLY — NEVER SHIPPED, NEVER USED IN PRODUCTION.
 *
 * Master Order v0.10 Phase 16: the production `TvLinkServer` requires a
 * non-null [PairingGate]; this allow-all gate exists ONLY in the test source
 * set so the transport suite can exercise the wire pipeline without a
 * pairing ceremony. It must never move to the main source set.
 */
class AllowAllPairingGate : PairingGate {
    override fun authorize(peerIdentity: String, confirm: PairingConfirm?): PairingGate.Verdict =
        PairingGate.Verdict.Authorized
}