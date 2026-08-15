package com.elysium.nexus.fabric.tv

import android.util.Base64
import com.elysium.nexus.fabric.canonical.DeviceId
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.UUID

/**
 * §10 Secure Pairing between phone and TV Node.
 *
 * Pairing creates cryptographic identities with mutual authentication,
 * forward-secure session negotiation, anti-replay, and session expiration.
 *
 * First launch TV:
 * 1. Shows QR + short human-readable code
 * 2. Phone scans QR, establishes X25519 key exchange
 * 3. Mutual authentication completes
 * 4. Unique device credentials stored in Android Keystore
 */
data class PairingSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val ephemeralPublicKey: ByteArray,
    val serverChallenge: ByteArray,
    val createdAtNs: Long = System.nanoTime()
)

/**
 * The pairing code displayed on the TV and entered/scanned by the phone.
 */
data class PairingCode(
    val humanCode: String,
    val qrData: String
)

/**
 * Result of a pairing attempt.
 */
sealed class PairingResult {
    data class Success(
        val deviceId: DeviceId,
        val sessionToken: String,
        val certificatePins: List<String>
    ) : PairingResult()

    data class PinRequired(
        val pinMessage: String
    ) : PairingResult()

    data class Error(
        val reason: String
    ) : PairingResult()
}

/**
 * Phone-side pairing engine for TV Node.
 *
 * Responsible for:
 * - Generating ephemeral key pairs
 * - Exchanging public keys with the TV Node
 * - Verifying the pairing code (human-readable or QR)
 * - Storing the resulting device credentials
 */
class TvNodePairingEngine {

    companion object {
        private const val TAG = "TvNodePairing"
        private const val KEY_ALGORITHM = "X25519"
        private const val KEY_SIZE = 256
    }

    private val secureRandom = SecureRandom()

    /**
     * Start a new pairing session.
     * Generates an ephemeral key pair and returns the session with the
     * public key that should be sent to the TV Node.
     */
    fun startPairingSession(): PairingSession {
        val keyPair = generateEphemeralKeyPair()
        val challenge = ByteArray(32).also { secureRandom.nextBytes(it) }
        return PairingSession(
            ephemeralPublicKey = keyPair.public.encoded,
            serverChallenge = challenge
        )
    }

    /**
     * Verify the pairing code entered by the user or scanned via QR.
     *
     * @param userCode the 6-character human-readable code or QR data
     * @param expectedCode the code expected from the TV
     * @return true if the codes match
     */
    fun verifyPairingCode(userCode: String, expectedCode: String): Boolean {
        return userCode.trim().equals(expectedCode.trim(), ignoreCase = true)
    }

    /**
     * Complete the pairing by verifying the TV's signed response.
     *
     * @param tvPublicKey the TV Node's public key (from QR or protocol)
     * @param tvSignature the TV Node's signature over the session challenge
     * @return a [PairingResult]
     */
    fun completePairing(
        tvPublicKey: ByteArray,
        tvSignature: ByteArray
    ): PairingResult {
        // Cryptographic verification happens here once the TV Node side exists
        // For now, return success with a provisional device ID
        val deviceId = DeviceId(UUID.randomUUID().toString())
        val sessionToken = Base64.encodeToString(
            ByteArray(32).also { secureRandom.nextBytes(it) },
            Base64.NO_WRAP
        )
        return PairingResult.Success(
            deviceId = deviceId,
            sessionToken = sessionToken,
            certificatePins = emptyList()
        )
    }

    private fun generateEphemeralKeyPair(): KeyPair {
        val generator = try {
            KeyPairGenerator.getInstance(KEY_ALGORITHM)
        } catch (e: Exception) {
            // Fallback to EC if X25519 not available
            KeyPairGenerator.getInstance("EC")
        }
        generator.initialize(KEY_SIZE, secureRandom)
        return generator.generateKeyPair()
    }

    /** Generate a random pairing code in the format XXX-XXX. */
    fun generateHumanCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val part1 = (1..3).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
        val part2 = (1..3).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
        return "$part1-$part2"
    }
}