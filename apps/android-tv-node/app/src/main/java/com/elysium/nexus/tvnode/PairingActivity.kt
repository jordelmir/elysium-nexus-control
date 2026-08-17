package com.elysium.nexus.tvnode

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.elysium.nexus.tvnode.application.TvNodeApp
import com.elysium.nexus.tvnode.pairing.QrPairingRenderer

/**
 * PairingActivity — the leanback home of the TV Node.
 *
 * Master Order v0.10 Phase 22 — REAL QR UX:
 * - Renders the live pairing payload as a scannable QR (ZXing matrix).
 * - The 6-digit pairing code is displayed SEPARATELY below the QR; the QR
 *   alone never pairs (possession of both is required).
 * - When the session has no live payload (expired/not open), NO QR is drawn
 *   — a stale or invented QR is never shown.
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
                append("\n\nPairing code: ")
                append(session?.displayCode()?.value ?: "expired")
                append("\n\nVolume observation is honest-only: confirmed deltas, never guesses.")
            }
            textSize = 20f
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(text)
        }

        // Phase 22: render the live QR payload (null when expired/not open — never invented).
        val matrix = QrPairingRenderer.renderMatrix(session?.qrPayload(), QrPairingRenderer.DEFAULT_SIZE_PX)
        if (matrix != null) {
            val qr = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    qr.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            column.addView(ImageView(this).apply {
                setImageBitmap(qr)
                layoutParams = LinearLayout.LayoutParams(
                    QrPairingRenderer.DEFAULT_SIZE_PX,
                    QrPairingRenderer.DEFAULT_SIZE_PX
                )
            })
        }
        setContentView(column)
    }

    override fun onDestroy() {
        (application as TvNodeApp).onPairingFinished()
        super.onDestroy()
    }
}