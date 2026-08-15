package com.elysium.nexus.tvnode.transport

import com.elysium.nexus.tvnode.canonical.UniversalAction
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol

/**
 * TvActionDispatcher — the server-side seam between the wire and real
 * execution (PR2 slice 4).
 *
 * The transport hands a RECEIVED, AUTHENTICATED [TvLinkProtocol.TvEnvelope]
 * to this seam and expects a §11 response state back. The concrete
 * implementation lives with the observation/execution layer
 * ([TvActionExecutor] in `observe`), which returns an honest verdict — never
 * a guessed success (§1: test passed ≠ TV reacted).
 *
 * Kept as a pure interface here so the socket server is fully JVM-testable
 * (a stub dispatcher runs in the integration tests).
 */
interface TvActionDispatcher {
    /**
     * Dispatch one authenticated envelope.
     *
     * @param envelope the decoded §11 envelope
     * @param action   the decoded [UniversalAction], or null for forward-compat
     *                 codes the TV cannot build yet (must be answered
     *                 UNSUPPORTED, never silently dropped).
     * @return the §11 response body to send back on the wire.
     */
    fun dispatch(
        envelope: TvLinkProtocol.TvEnvelope,
        action: UniversalAction?
    ): TvLinkProtocol.TvResponseBody
}
