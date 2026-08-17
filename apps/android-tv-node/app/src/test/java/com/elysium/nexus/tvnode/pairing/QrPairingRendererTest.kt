package com.elysium.nexus.tvnode.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPairingRendererTest {

    private val payload = QrPairingPayload.parse(
        "elysium-pairing|v1|universal:test:abs|abcdef0123456789abcdef0123456789|1a2b3c4d"
    )!!

    @Test
    fun `renders a scannable square matrix for a live payload`() {
        val matrix = QrPairingRenderer.renderMatrix(payload, 256)
        assertNotNull(matrix)
        assertEquals(256, matrix!!.width)
        assertEquals(256, matrix.height)
        var dark = 0
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            if (matrix.get(x, y)) dark++
        }
        // A QR code is never blank: the finder patterns alone guarantee modules.
        assertTrue("QR matrix must contain dark modules, found $dark", dark > 100)
    }

    @Test
    fun `never renders for a null or expired payload`() {
        assertNull(QrPairingRenderer.renderMatrix(null))
        assertNull(QrPairingRenderer.renderMatrix(null, 128))
    }

    @Test
    fun `refuses unreasonable sizes`() {
        assertNull(QrPairingRenderer.renderMatrix(payload, 64))
    }

    @Test
    fun `matrix decodes back to the exact payload`() {
        // Round-trip proof: ZXing matrix -> decode -> same string content.
        val matrix = QrPairingRenderer.renderMatrix(payload, 320)!!
        val decoded = decodeMatrix(matrix)
        assertEquals("elysium-pairing|v1|universal:test:abs|abcdef0123456789abcdef0123456789|1a2b3c4d", decoded)
    }

    private fun decodeMatrix(matrix: com.google.zxing.common.BitMatrix): String? {
        return try {
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h) { i ->
                if (matrix.get(i % w, i / w)) 0 else 0xFFFFFF
            }
            val source = com.google.zxing.RGBLuminanceSource(w, h, pixels)
            val bitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
            com.google.zxing.qrcode.QRCodeReader().decode(bitmap).text
        } catch (e: Exception) {
            null
        }
    }
}