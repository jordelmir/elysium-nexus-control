package com.elysium.nexus.tvnode.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Master Order v0.10 Phase 22 — REAL QR UX (TV side).
 *
 * Renders the pairing payload as a scannable QR matrix. The matrix generation
 * is pure JVM (ZXing `BitMatrix`) and unit-tested; the Android `Bitmap`
 * conversion is a thin 1:1 adapter on top — no pixel logic of our own.
 *
 * The QR contains bootstrap metadata ONLY ([QrPairingPayload.encode]); the
 * 6-digit code stays on-screen separately, so scanning alone never pairs.
 */
object QrPairingRenderer {

    /** Default edge size of the rendered square. */
    const val DEFAULT_SIZE_PX = 512

    /**
     * Renders the QR matrix for a session payload.
     * Returns null when the session has no live payload (expired/not open)
     * or the payload cannot be encoded — pairing never invents content.
     */
    fun renderMatrix(payload: QrPairingPayload?, size: Int = DEFAULT_SIZE_PX): com.google.zxing.common.BitMatrix? {
        if (payload == null) return null
        if (size < 128) return null
        return try {
            QRCodeWriter().encode(
                payload.encode(),
                BarcodeFormat.QR_CODE,
                size,
                size
            )
        } catch (e: Exception) {
            null
        }
    }
}