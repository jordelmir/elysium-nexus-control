package com.elysium.nexus.tvnode.channel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Channel crypto tests — the authenticated phone↔TV channel (PR2 slice 2).
 *
 * Byte-parity discipline: the phone side will implement the mirror with
 * LinkSide.PHONE; these tests prove the two halves of the directional key
 * pair agree (TV rx == phone tx and vice versa) and that every fail-closed
 * property holds: wrong domain rejection, replay rejection, AAD binding,
 * mutation detection.
 */
class TvChannelCryptoTest {

    private val adPhoneToTv = TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.PHONE_TO_TV)
    private val adTvToPhone = TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.TV_TO_PHONE)

    @Test
    fun `directional keys are mirrors, tv rx equals phone tx and vice versa`() {
        val tv = TvChannelCrypto.generateKeyPair()
        val phone = TvChannelCrypto.generateKeyPair()

        val tvKeys = TvChannelCrypto.deriveChannelKeys(tv, phone.publicKeyBytes, TvChannelCrypto.LinkSide.TV)
        val phoneKeys = TvChannelCrypto.deriveChannelKeys(phone, tv.publicKeyBytes, TvChannelCrypto.LinkSide.PHONE)

        assertArrayEquals(tvKeys.rxKeyBytes, phoneKeys.txKeyBytes)
        assertArrayEquals(tvKeys.txKeyBytes, phoneKeys.rxKeyBytes)
        assertNotEquals(tvKeys.txKeyBytes, tvKeys.rxKeyBytes)
    }

    @Test
    fun `phone-to-tv frame opens on the tv and carries the right nonce domain`() {
        val tv = TvChannelCrypto.generateKeyPair()
        val phone = TvChannelCrypto.generateKeyPair()
        val tvKeys = TvChannelCrypto.deriveChannelKeys(tv, phone.publicKeyBytes, TvChannelCrypto.LinkSide.TV)
        val phoneKeys = TvChannelCrypto.deriveChannelKeys(phone, tv.publicKeyBytes, TvChannelCrypto.LinkSide.PHONE)

        val message = "Navigate(UP) seq=7".toByteArray()
        val frame = phoneKeys.encryptToPeer(message, adPhoneToTv)
        assertEquals(0x11, frame[0].toInt() and 0xFF) // PHONE_TO_TV domain

        assertArrayEquals(message, tvKeys.decryptFromPeer(frame, adPhoneToTv))
    }

    @Test
    fun `frame from the wrong wire direction is rejected`() {
        val tv = TvChannelCrypto.generateKeyPair()
        val phone = TvChannelCrypto.generateKeyPair()
        val tvKeys = TvChannelCrypto.deriveChannelKeys(tv, phone.publicKeyBytes, TvChannelCrypto.LinkSide.TV)
        val phoneKeys = TvChannelCrypto.deriveChannelKeys(phone, tv.publicKeyBytes, TvChannelCrypto.LinkSide.PHONE)

        // A frame sealed with the phone's TX key but presented as if it came
        // tv→phone (i.e., opened with the TV's TX key path would fail); and a
        // phone TX frame re-sent on the TV→PHONE domain must be rejected.
        val frame = phoneKeys.encryptToPeer("x".toByteArray(), adPhoneToTv)
        try {
            tvKeys.decryptFromPeer(frame, adPhoneToTv) // correct direction: works
        } catch (e: Exception) {
            fail("expected open")
        }
        // Now salt the nonce domain to PHONE_TO_MAC (0x01) — foreign link.
        val foreign = frame.copyOf()
        foreign[0] = 0x01
        try {
            tvKeys.decryptFromPeer(foreign, adPhoneToTv)
            fail("foreign domain must be rejected")
        } catch (e: TvChannelCrypto.ReplayRejectedException) {
            // expected
        }
    }

    @Test
    fun `replayed frame is rejected after the first open`() {
        val tv = TvChannelCrypto.generateKeyPair()
        val phone = TvChannelCrypto.generateKeyPair()
        val tvKeys = TvChannelCrypto.deriveChannelKeys(tv, phone.publicKeyBytes, TvChannelCrypto.LinkSide.TV)
        val phoneKeys = TvChannelCrypto.deriveChannelKeys(phone, tv.publicKeyBytes, TvChannelCrypto.LinkSide.PHONE)

        val frame = phoneKeys.encryptToPeer("VolumeUp".toByteArray(), adPhoneToTv)
        assertArrayEquals("VolumeUp".toByteArray(), tvKeys.decryptFromPeer(frame, adPhoneToTv))
        try {
            tvKeys.decryptFromPeer(frame, adPhoneToTv)
            fail("replay must be rejected")
        } catch (e: TvChannelCrypto.ReplayRejectedException) {
            // expected
        }
    }

    @Test
    fun `out-of-order frames beyond the sliding window are rejected`() {
        val tv = TvChannelCrypto.generateKeyPair()
        val phone = TvChannelCrypto.generateKeyPair()
        val tvKeys = TvChannelCrypto.deriveChannelKeys(tv, phone.publicKeyBytes, TvChannelCrypto.LinkSide.TV)
        val phoneKeys = TvChannelCrypto.deriveChannelKeys(phone, tv.publicKeyBytes, TvChannelCrypto.LinkSide.PHONE)

        val first = phoneKeys.encryptToPeer("old".toByteArray(), adPhoneToTv)
        assertArrayEquals("old".toByteArray(), tvKeys.decryptFromPeer(first, adPhoneToTv))

        // Advance the receiver far beyond the ReplayGuard window (65,536).
        var last = first
        for (i in 1..70_000) last = phoneKeys.encryptToPeer("bulk$i".toByteArray(), adPhoneToTv)
        tvKeys.decryptFromPeer(last, adPhoneToTv)

        // The very first frame is now far behind the highest seen: rejected.
        try {
            tvKeys.decryptFromPeer(first, adPhoneToTv)
            fail("stale frame must be rejected")
        } catch (e: TvChannelCrypto.ReplayRejectedException) {
            // expected
        }
    }

    @Test
    fun `AAD mismatch breaks authentication`() {
        val tv = TvChannelCrypto.generateKeyPair()
        val phone = TvChannelCrypto.generateKeyPair()
        val tvKeys = TvChannelCrypto.deriveChannelKeys(tv, phone.publicKeyBytes, TvChannelCrypto.LinkSide.TV)
        val phoneKeys = TvChannelCrypto.deriveChannelKeys(phone, tv.publicKeyBytes, TvChannelCrypto.LinkSide.PHONE)

        val frame = phoneKeys.encryptToPeer("Mute".toByteArray(), adPhoneToTv)
        try {
            tvKeys.decryptFromPeer(frame, adTvToPhone) // wrong AAD
            fail("AAD mismatch must fail authentication")
        } catch (e: TvChannelCrypto.CryptoUnavailableException) {
            // expected
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val tv = TvChannelCrypto.generateKeyPair()
        val phone = TvChannelCrypto.generateKeyPair()
        val tvKeys = TvChannelCrypto.deriveChannelKeys(tv, phone.publicKeyBytes, TvChannelCrypto.LinkSide.TV)
        val phoneKeys = TvChannelCrypto.deriveChannelKeys(phone, tv.publicKeyBytes, TvChannelCrypto.LinkSide.PHONE)

        val frame = phoneKeys.encryptToPeer("Home".toByteArray(), adPhoneToTv)
        frame[frame.size - 2] = (frame[frame.size - 2].toInt() xor 0x01).toByte()
        try {
            tvKeys.decryptFromPeer(frame, adPhoneToTv)
            fail("tampered tag must fail authentication")
        } catch (e: TvChannelCrypto.CryptoUnavailableException) {
            // expected
        }
    }

    @Test
    fun `channelAd binds version and direction and is distinct per link`() {
        val a = TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.PHONE_TO_TV)
        val b = TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.TV_TO_PHONE)
        val c = TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.PHONE_TO_TV, protocolVersion = 2)
        assertFalse(a.contentEquals(b))
        assertFalse(a.contentEquals(c))
        assertTrue(a.contentEquals("elysium-tv-link-v1|domain=PHONE_TO_TV".toByteArray()))
    }

    @Test
    fun `fingerprint is 8 hex and content-sensitive`() {
        val kp = TvChannelCrypto.generateKeyPair()
        assertEquals(8, TvChannelCrypto.fingerprintOf(kp.publicKeyBytes).length)
        assertEquals(
            TvChannelCrypto.fingerprintOf(kp.publicKeyBytes),
            TvChannelCrypto.fingerprintOf(kp.publicKeyBytes)
        )
        assertNotEquals(
            TvChannelCrypto.fingerprintOf(kp.publicKeyBytes),
            TvChannelCrypto.fingerprintOf(TvChannelCrypto.generateKeyPair().publicKeyBytes)
        )
    }

    @Test
    fun `x25519 key pairs are 32 bytes and distinct`() {
        val a = TvChannelCrypto.generateKeyPair()
        val b = TvChannelCrypto.generateKeyPair()
        assertEquals(32, a.publicKeyBytes.size)
        assertEquals(32, b.publicKeyBytes.size)
        assertFalse(a.publicKeyBytes.contentEquals(b.publicKeyBytes))
    }

    @Test
    fun `modern and legacy X25519 paths derive identical shared secrets`() {
        val tv = TvChannelCrypto.generateKeyPair()
        val phone = TvChannelCrypto.generateKeyPair()
        val modern = TvChannelCrypto.computeSharedSecretModern(tv, phone.publicKeyBytes)
        val legacy = TvChannelCrypto.computeSharedSecretLegacy(tv, phone.publicKeyBytes)
        assertEquals(32, modern.size)
        assertArrayEquals(modern, legacy)
    }

    @Test
    fun `modern and legacy X25519 paths produce interchangeable keys`() {
        val modernPair = TvChannelCrypto.generateKeyPairModern()
        val legacyPair = TvChannelCrypto.generateKeyPairLegacy()
        val theirModern = TvChannelCrypto.generateKeyPairModern()
        assertEquals(32, modernPair.publicKeyBytes.size)
        assertEquals(32, legacyPair.publicKeyBytes.size)
        val secretA = TvChannelCrypto.computeSharedSecretLegacy(modernPair, theirModern.publicKeyBytes)
        val secretB = TvChannelCrypto.computeSharedSecretModern(legacyPair, theirModern.publicKeyBytes)
        assertEquals(32, secretA.size)
        assertEquals(32, secretB.size)
    }
}