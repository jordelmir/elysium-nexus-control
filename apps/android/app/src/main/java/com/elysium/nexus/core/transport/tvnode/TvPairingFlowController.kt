package com.elysium.nexus.core.transport.tvnode

/**
 * Master Order v0.10 Phase 22 — REAL QR UX (phone side): pairing flow.
 *
 * Pure state machine that turns a scanned QR payload into an established
 * control link, using ONLY injected, real capabilities:
 *
 *   1. parse the QR payload (strict — malformed => never proceeds),
 *   2. resolve the TV host via NSD discovery (host+port),
 *   3. connect + confirm with the code the USER typed (never auto-guessed),
 *   4. become Ready(serverFullIdentity) or fail with an honest reason.
 *
 * No fake success exists: every step must report success for the flow to
 * advance, and any failure lands in a terminal Failed state with a reason.
 */
class TvPairingFlowController(
    private val gateway: Gateway
) {

    interface Gateway {
        /** Resolves the TV endpoint; null when discovery found nothing. */
        fun resolveTv(): DiscoveredTv?

        /** Opens a link (host/port) and returns the pinned identity. */
        fun connect(host: String, port: Int): TvLinkHandle?
    }

    interface TvLinkHandle {
        /** Submits the user-typed pairing code; true only on verified+established. */
        fun confirmWithCode(code: String): Boolean

        /** The pinned 64-hex identity of the server once the channel is established. */
        fun fullIdentity(): String
    }

    data class DiscoveredTv(
        val host: String,
        val port: Int
    )

    sealed class State {
        object Idle : State()
        data class AwaitingCode(val deviceId: String) : State()
        data class Ready(val serverFullIdentity: String) : State()
        data class Failed(val reason: String) : State()
    }

    private var state: State = State.Idle
    private var pendingHandle: TvLinkHandle? = null

    fun state(): State = state

    /**
     * Feeds a scanned QR payload. Returns the next state; on success the
     * caller must show the code-entry UI (the 6-digit code NEVER lives in
     * the QR — [AwaitingCode] proves possession of both before pairing).
     */
    fun onQrScanned(qrContent: String?): State {
        if (state is State.Ready || state is State.Failed) return state
        val payload = com.elysium.nexus.tvnode.pairing.QrPairingPayload.parse(qrContent ?: "")
            ?: return fail("malformed or expired QR payload")
        val tv = gateway.resolveTv() ?: return fail("TV not found on the local network")
        val handle = gateway.connect(tv.host, tv.port) ?: return fail("cannot connect to ${tv.host}:${tv.port}")
        pendingHandle = handle
        state = State.AwaitingCode(payload.deviceId)
        return state
    }

    /** Submits the user-typed 6-digit code. Terminal states stick. */
    fun onCodeEntered(code: String): State {
        val handle = pendingHandle ?: return fail("no pending pairing session")
        if (code.length != 6 || code.any { !it.isDigit() }) {
            return fail("code must be 6 digits")
        }
        return if (handle.confirmWithCode(code)) {
            state = State.Ready(handle.fullIdentity())
            state
        } else {
            fail("pairing code rejected by the TV")
        }
    }

    private fun fail(reason: String): State {
        state = State.Failed(reason)
        pendingHandle = null
        return state
    }
}