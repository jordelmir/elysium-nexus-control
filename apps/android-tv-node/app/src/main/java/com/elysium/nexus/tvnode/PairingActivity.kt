package com.elysium.nexus.tvnode

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.elysium.nexus.tvnode.application.TvNodeApp

/**
 * PairingActivity — the leanback home of the TV Node.
 *
 * Functional preview (PR2):
 * - Shows the pairing identity (device id + fact summary) on the TV.
 * - Starts a pairing session: displays the 6-digit pairing code.
 * - QR rendering + authenticated channel wiring land in the next
 *   pairing slice (channel keys + Keystore credentials).
 * - Demonstrates volume observation + execution through the honest
 *   ladder when the accessibility service is granted.
 */
class PairingActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as TvNodeApp
        val session = app.startPairing()

        val text = TextView(this).apply {
            text = buildString {
                append("Elysium Nexus TV Node — pairing identity\n\n")
                append(app.identity.deviceId.value)
                append("\napi=").append(android.os.Build.VERSION.SDK_INT)
                append(" tv=").append(app.identity.deviceFacts().isTv)
                append(" fixedVol=").append(app.identity.deviceFacts().volumeFixed)
                append("\naccessibility=").append(app.accessibility != null)
                append(" ime=").append(app.ime != null)
                if (session != null) {
                    append("\n\nPairing code: ")
                    append(session.displayCode()?.value ?: "expired")
                }
                append("\n\nVolume observation is honest-only: confirmed deltas, never guesses.")
            }
            textSize = 20f
        }
        setContentView(text)
    }

    override fun onDestroy() {
        (application as TvNodeApp).onPairingFinished()
        super.onDestroy()
    }
}