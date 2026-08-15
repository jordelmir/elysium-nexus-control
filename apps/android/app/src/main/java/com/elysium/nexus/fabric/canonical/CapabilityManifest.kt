package com.elysium.nexus.fabric.canonical

/**
 * §7 Capability Manifest — per-capability status for a device.
 *
 * Each TV (or any device) has a [CapabilityManifest] that declares what it
 * can actually do and at what trust/permission level. The UI is generated
 * from this manifest: unsupported capabilities must never show a
 * functional-looking button.
 */
data class CapabilityManifest(
    val entries: Map<Capability, CapabilityStatus> = emptyMap()
) {
    fun statusOf(capability: Capability): CapabilityStatus =
        entries[capability] ?: CapabilityStatus.UNVERIFIED
}

/**
 * Status of a single capability for a specific device.
 *
 * Ordered from most capable to least capable for comparison.
 */
enum class CapabilityStatus {
    /** The capability works with zero user permission, out of the box. */
    SUPPORTED,
    /** Works but requires user-granted permission (e.g. AccessibilityService). */
    USER_PERMISSION_REQUIRED,
    /** Available only in developer/lab mode (e.g. ADB). */
    DEVELOPER_ONLY,
    /** Available only with OEM system-level integration. */
    OEM_ONLY,
    /** Not supported by this device at all. */
    UNSUPPORTED,
    /** Not yet tested or verified. */
    UNVERIFIED
}

/**
 * Convenience accessors for common TV capabilities (§7 examples).
 */
object TvCapabilities {
    val TV_NODE_CORE = setOf(
        CapabilityMapEntry(Capability.OnOff, CapabilityStatus.SUPPORTED),
        CapabilityMapEntry(Capability.Volume, CapabilityStatus.SUPPORTED),
        CapabilityMapEntry(Capability.MediaTransport, CapabilityStatus.SUPPORTED),
        CapabilityMapEntry(Capability.SourceSelect, CapabilityStatus.SUPPORTED)
    )

    val ACCESSIBILITY_ENHANCED = setOf(
        CapabilityMapEntry(Capability.OnOff, CapabilityStatus.USER_PERMISSION_REQUIRED),
        CapabilityMapEntry(Capability.InputSource, CapabilityStatus.USER_PERMISSION_REQUIRED)
    )

    val HID_BLUETOOTH = setOf(
        CapabilityMapEntry(Capability.Volume, CapabilityStatus.SUPPORTED),
        CapabilityMapEntry(Capability.MediaTransport, CapabilityStatus.SUPPORTED)
    )

    val IR_ONLY = setOf(
        CapabilityMapEntry(Capability.OnOff, CapabilityStatus.SUPPORTED),
        CapabilityMapEntry(Capability.Volume, CapabilityStatus.SUPPORTED),
        CapabilityMapEntry(Capability.InputSource, CapabilityStatus.UNVERIFIED)
    )
}

data class CapabilityMapEntry(
    val capability: Capability,
    val status: CapabilityStatus
)