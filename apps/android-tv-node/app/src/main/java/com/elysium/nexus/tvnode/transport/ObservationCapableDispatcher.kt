package com.elysium.nexus.tvnode.transport

import com.elysium.nexus.tvnode.canonical.TvObservationEngine
import com.elysium.nexus.tvnode.canonical.UniversalAction
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol

/**
 * Master Order v0.10 Phase 25 — volume observation seam for the software-only
 * IR oracle.
 *
 * Answers `OBSERVE_VOLUME` envelopes with an honest, timestamped volume
 * snapshot in the response detail (canonical form `vol=<raw>/<max>,muted=<b>`),
 * or `UNSUPPORTED` when no observation engine is available. All other actions
 * delegate to the wrapped dispatcher — never a silent success.
 */
class ObservationCapableDispatcher(
    private val observe: () -> TvObservationEngine?,
    private val delegate: TvActionDispatcher
) : TvActionDispatcher {

    override fun dispatch(
        envelope: TvLinkProtocol.TvEnvelope,
        action: UniversalAction?
    ): TvLinkProtocol.TvResponseBody {
        if (envelope.action.code != TvLinkProtocol.TvActionCode.OBSERVE_VOLUME) {
            return delegate.dispatch(envelope, action)
        }
        val observation = observe()?.observeVolume()
            ?: return TvLinkProtocol.TvResponseBody(
                state = TvLinkProtocol.TvResponseState.UNSUPPORTED,
                answerToMessageId = envelope.messageId,
                detail = "no observation engine — oracle observation unavailable"
            )
        return TvLinkProtocol.TvResponseBody(
            state = TvLinkProtocol.TvResponseState.EXECUTED,
            answerToMessageId = envelope.messageId,
            detail = "vol=${observation.rawVolume}/${observation.maxVolume},muted=${observation.isMuted}"
        )
    }
}