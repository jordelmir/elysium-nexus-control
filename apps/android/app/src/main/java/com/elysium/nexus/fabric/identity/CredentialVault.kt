package com.elysium.nexus.fabric.identity

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol

/**
 * §70 Credential Vault.
 *
 * The vault stores protocol-specific credentials
 * (pairing tokens, API keys, certificates) in
 * the Android Keystore. Room stores only
 * *references* (alias + metadata), never the
 * secrets themselves.
 *
 * ## Security model
 *
 * - Secrets live in the Android Keystore (hardware-
 *   backed on devices with TEE/SE).
 * - Room stores: alias, protocol, deviceId,
 *   createdAt, expiresAt, label.
 * - The vault never logs secrets.
 * - The vault never serializes secrets to JSON/disk.
 * - Process death: Keystore survives; Room references
 *   are re-resolved on next access.
 *
 * ## Why a sealed hierarchy
 *
 * Different protocols require different credential
 * shapes. A sealed hierarchy gives exhaustive-when
 * checking: if a new protocol needs credentials,
 * the compiler forces the vault to handle it.
 */
sealed class Credential {

    /** The protocol this credential belongs to. */
    abstract val protocol: Protocol

    /** The target device. */
    abstract val deviceId: DeviceId

    /** When this credential was created. */
    abstract val createdAtMs: Long

    /** Optional expiration (null = never expires). */
    abstract val expiresAtMs: Long?

    /** Whether this credential is currently valid. */
    val isValid: Boolean
        get() {
            if (expiresAtMs == null) return true
            return System.currentTimeMillis() < expiresAtMs!!
        }

    // ── Protocol-specific credentials ─────────────

    /**
     * Matter pairing code + salt.
     */
    data class MatterPairing(
        override val deviceId: DeviceId,
        val pairingCode: String,
        val salt: ByteArray,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.Matter
        init {
            require(pairingCode.isNotBlank()) { "Matter pairing code must be non-blank." }
        }
    }

    /**
     * Zigbee network key + install code.
     */
    data class ZigbeeNetwork(
        override val deviceId: DeviceId,
        val networkKey: ByteArray,
        val installCode: ByteArray,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.Zigbee
    }

    /**
     * Z-Wave S2 authentication + DSK.
     */
    data class ZWaveS2(
        override val deviceId: DeviceId,
        val authKey: ByteArray,
        val dsk: String,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.ZWave
        init {
            require(dsk.isNotBlank()) { "Z-Wave DSK must be non-blank." }
        }
    }

    /**
     * BLE bonding key.
     */
    data class BleBonding(
        override val deviceId: DeviceId,
        val bondKey: ByteArray,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.Ble
    }

    /**
     * WiFi WPA2/WPA3 credential.
     */
    data class WiFiCredential(
        override val deviceId: DeviceId,
        val ssid: String,
        val psk: String,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.WiFi
        init {
            require(ssid.isNotBlank()) { "WiFi SSID must be non-blank." }
            require(psk.isNotBlank()) { "WiFi PSK must be non-blank." }
        }
    }

    /**
     * MQTT username/password.
     */
    data class MqttAuth(
        override val deviceId: DeviceId,
        val username: String,
        val password: String,
        val clientId: String,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.Mqtt
    }

    /**
     * ONVIF username/password.
     */
    data class OnvifAuth(
        override val deviceId: DeviceId,
        val username: String,
        val password: String,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.Onvif
    }

    /**
     * Vendor API token (REST / WebSocket).
     */
    data class VendorToken(
        override val deviceId: DeviceId,
        val vendorProtocol: Protocol,
        val token: String,
        val refreshToken: String? = null,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = vendorProtocol
        init {
            require(token.isNotBlank()) { "Vendor token must be non-blank." }
        }
    }

    /**
     * Elysium Link pairing key.
     */
    data class ElysiumLinkKey(
        override val deviceId: DeviceId,
        val pairingKey: ByteArray,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.ElysiumLink
    }

    /**
     * Generic credential for protocols not yet
     * enumerated. The vault stores the raw bytes;
     * the protocol adapter interprets them.
     */
    data class Generic(
        override val deviceId: DeviceId,
        override val protocol: Protocol,
        val keyAlias: String,
        val metadata: Map<String, String> = emptyMap(),
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        init {
            require(keyAlias.isNotBlank()) { "Generic credential keyAlias must be non-blank." }
        }
    }
}

/**
 * Reference stored in Room. Points to a Keystore
 * entry but contains no secrets.
 */
data class CredentialReference(
    val keyAlias: String,
    val protocol: Protocol,
    val deviceId: DeviceId,
    val label: String,
    val createdAtMs: Long,
    val expiresAtMs: Long?,
    val isExpired: Boolean = false
)

/**
 * The Credential Vault interface. The Android
 * implementation uses the Android Keystore;
 * the Hub/Receiver use the secure element;
 * tests use an in-memory implementation.
 */
interface CredentialVault {
    /**
     * Store a credential securely. Returns a
     * [CredentialReference] for Room storage.
     */
    fun store(credential: Credential): CredentialReference

    /**
     * Retrieve a credential by its reference.
     * Returns null if the credential is missing
     * or expired.
     */
    fun retrieve(reference: CredentialReference): Credential?

    /**
     * Delete a credential. The Keystore entry
     * is permanently removed.
     */
    fun delete(reference: CredentialReference)

    /**
     * List all references for a device.
     */
    fun listForDevice(deviceId: DeviceId): List<CredentialReference>

    /**
     * List all references for a protocol.
     */
    fun listForProtocol(protocol: Protocol): List<CredentialReference>
}

/**
 * In-memory implementation for tests.
 */
class InMemoryCredentialVault : CredentialVault {
    private val store = mutableMapOf<String, Credential>()

    override fun store(credential: Credential): CredentialReference {
        val alias = "vault_${credential.protocol}_${credential.deviceId.value}_${System.currentTimeMillis()}"
        store[alias] = credential
        return CredentialReference(
            keyAlias = alias,
            protocol = credential.protocol,
            deviceId = credential.deviceId,
            label = credential::class.simpleName ?: "Unknown",
            createdAtMs = credential.createdAtMs,
            expiresAtMs = credential.expiresAtMs,
            isExpired = credential.isValid.not()
        )
    }

    override fun retrieve(reference: CredentialReference): Credential? {
        val credential = store[reference.keyAlias] ?: return null
        return if (credential.isValid) credential else null
    }

    override fun delete(reference: CredentialReference) {
        store.remove(reference.keyAlias)
    }

    override fun listForDevice(deviceId: DeviceId): List<CredentialReference> =
        store.entries
            .filter { it.value.deviceId == deviceId }
            .map { (alias, credential) ->
                CredentialReference(
                    keyAlias = alias,
                    protocol = credential.protocol,
                    deviceId = credential.deviceId,
                    label = credential::class.simpleName ?: "Unknown",
                    createdAtMs = credential.createdAtMs,
                    expiresAtMs = credential.expiresAtMs,
                    isExpired = credential.isValid.not()
                )
            }

    override fun listForProtocol(protocol: Protocol): List<CredentialReference> =
        store.entries
            .filter { it.value.protocol == protocol }
            .map { (alias, credential) ->
                CredentialReference(
                    keyAlias = alias,
                    protocol = credential.protocol,
                    deviceId = credential.deviceId,
                    label = credential::class.simpleName ?: "Unknown",
                    createdAtMs = credential.createdAtMs,
                    expiresAtMs = credential.expiresAtMs,
                    isExpired = credential.isValid.not()
                )
            }
}
