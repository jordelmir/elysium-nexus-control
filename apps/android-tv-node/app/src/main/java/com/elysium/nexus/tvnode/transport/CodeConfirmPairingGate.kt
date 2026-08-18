package com.elysium.nexus.tvnode.transport

import com.elysium.nexus.tvnode.credential.TvCredentialVault
import com.elysium.nexus.tvnode.pairing.PairingSession

/**
 * CodeConfirmPairingGate — the production pairing gate (PR2 slice 5, §10).
 *
 * Made of three decisions, executed strictly in order:
 *
 * 1. RECONNECT — if the peer's fingerprint is already pinned in the vault,
 *    the peer is a known previously-paired device and is authorized WITHOUT
 *    a code ("both sides pin in vault"). This is the durable path: after a
 *    first successful pairing the phone/TV pair never types codes again.
 *
 * 2. CODE + NONCE — otherwise the peer must present a [PairingConfirm]:
 *    - a live, OPEN, unexpired [PairingSession] must exist (single-use);
 *    - the QR nonce must match the session's QR payload FIRST (anti-replay:
 *      the confirm is bound to the exact QR shown on screen);
 *    - the 6-digit code must verify through the session
 *      ([PairingSession.verifyCode] is constant-time and increments the
 *      wrong-guess counter → brute-force limited).
 *    On success the peer fingerprint is durably pinned and the channel is
 *    authorized.
 *
 * 3. DENIED — any missing/wrong/mismatched input, no session, an expired or
 *    already-spent session, or an unpinned peer with nothing to prove.
 *    The server tears the link down (fail-closed, §10 "Unknown peer:
 *    REJECT", "Malformed frame: REJECT").
 *
 * Note (honest seam): the channel's cryptographic binding stays with the
 * wire handshake ([TvLinkHandshake] own ephemeral keys + AEAD possession
 * proof); this gate certifies the PAIRING CEREMONY (code + QR possession)
 * and the durable pin. The session's own [PairingSession.bindChannel] key-
 * derivation path is NOT used here (the handshake and the session currently
 * own separate ephemeral keypairs); unifying them is documented as future
 * work so no two key domains silently merge.
 */
class CodeConfirmPairingGate(
    private val vault: TvCredentialVault,
    private val session: PairingSession?
) : PairingGate {

    override fun authorize(
        peerIdentity: String,
        confirm: PairingConfirm?
    ): PairingGate.Verdict {
        // 1. Known pinned peer → authorized without a code (reconnect).
        if (vault.isPeerIdentityPinned(peerIdentity)) {
            return PairingGate.Verdict.Authorized
        }

        // 2. First pairing: need a live session + a valid QR nonce + code.
        val s = session ?: return PairingGate.Verdict.Denied("no pairing session")
        if (s.state != PairingSession.State.Open) {
            return PairingGate.Verdict.Denied("pairing not in progress: ${s.state}")
        }
        val qr = s.qrPayload() ?: return PairingGate.Verdict.Denied("pairing session expired")
        if (confirm == null) {
            return PairingGate.Verdict.Denied("peer did not prove the pairing code")
        }
        if (confirm.nonce != qr.nonce.value) {
            return PairingGate.Verdict.Denied("QR nonce mismatch — confirm not bound to this session")
        }
        val state = s.verifyCode(confirm.code)
        if (state != PairingSession.State.CodeVerified) {
            return PairingGate.Verdict.Denied("pairing code rejected (state=$state)")
        }

        // 3. First successful pairing: pin the peer durably. FAIL-CLOSED:
        //    the vault result MUST be Stored (or AlreadyPinned) — a failed
        //    durable pin means DENY, never authorize with an unpersisted pin
        //    (Master Order v0.10 Phase 17).
        val pinResult = try {
            vault.pinPeerIdentity(peerIdentity)
        } catch (e: Exception) {
            return PairingGate.Verdict.Denied("durable pin failed: ${e.message}")
        }
        return when (pinResult) {
            is TvCredentialVault.VaultResult.Stored -> PairingGate.Verdict.Authorized
            is TvCredentialVault.VaultResult.AlreadyPinned -> PairingGate.Verdict.Authorized
            is TvCredentialVault.VaultResult.NotFound ->
                PairingGate.Verdict.Denied("durable pin not created")
            is TvCredentialVault.VaultResult.Error ->
                PairingGate.Verdict.Denied("durable pin failed: ${pinResult.reason}")
        }
    }
}
