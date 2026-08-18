package com.elysium.nexus.tvnode.application

import com.elysium.nexus.tvnode.credential.TvCredentialVault
import com.elysium.nexus.tvnode.pairing.PairingSession
import com.elysium.nexus.tvnode.transport.CodeConfirmPairingGate
import com.elysium.nexus.tvnode.transport.PairingConfirm
import com.elysium.nexus.tvnode.transport.PairingGate

/**
 * SessionAwarePairingGate — app-level pairing gate that reads the CURRENT
 * pairing session at authorize time (the session starts and ends on the TV
 * screen, long after the listener bound its port).
 *
 * Delegates every decision to [CodeConfirmPairingGate] semantics: pinned
 * peers reconnect without a code; first pairing needs a live session, the
 * QR nonce and the on-screen code; anything else is Denied (fail-closed).
 */
class SessionAwarePairingGate(
    private val vault: TvCredentialVault,
    private val sessionProvider: () -> PairingSession?
) : PairingGate {

    override fun authorize(peerIdentity: String, confirm: PairingConfirm?): PairingGate.Verdict =
        CodeConfirmPairingGate(vault, sessionProvider()).authorize(peerIdentity, confirm)
}