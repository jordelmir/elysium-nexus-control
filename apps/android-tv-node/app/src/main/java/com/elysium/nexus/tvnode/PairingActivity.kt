package com.elysium.nexus.tvnode

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * PairingActivity — the leanback home of the TV Node.
 *
 * Functional preview (v0.1):
 * - Shows the pairing identity (device id + fact summary) on the TV.
 * - Demonstrates volume observation + execution through the honest
 *   ladder when the accessibility service is granted.
 * - QR + 6-digit code + secure channel wiring land in the next
 *   increment after the phone-side Bluetooth/HID transport.
 */
class PairingActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            text = buildString {
                append("Elysium Nexus TV Node — pairing identity\n\n")
                append(app().identity.deviceId.value)
                append("\napi=").append(android.os.Build.VERSION.SDK_INT)
                append(" tv=").append(app().identity.deviceFacts().isTv)
                append(" fixedVol=").append(app().identity.deviceFacts().volumeFixed)
                append("\naccessibility=").append(app().accessibility != null)
                append(" ime=").append(app().ime != null)
                append("\n\nVolume observation is honest-only: confirmed deltas, never guesses.")
            }
            textSize = 20f
        }
        setContentView(text)
    }

    private fun app(): com.elysium.nexus.tvnode.application.TvNodeApp =
        application as com.elysium.nexus.tvnode.application.TvNodeApp
}