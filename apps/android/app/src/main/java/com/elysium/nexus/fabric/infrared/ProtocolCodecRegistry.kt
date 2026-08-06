package com.elysium.nexus.fabric.infrared

/**
 * §6.5 Protocol Repeat Policy enum.
 */
enum class RepeatPolicy {
    NONE,
    FULL_FRAME,
    SPECIAL_REPEAT_FRAME,
    TOGGLE_PER_NEW_PRESS,
    STATE_FRAME
}

/**
 * §6.3 Codec Verification Status.
 */
enum class CodecVerificationStatus {
    EXPERIMENTAL,
    GOLDEN_VECTOR_VERIFIED,
    DECODER_ROUNDTRIP_VERIFIED,
    HIL_VERIFIED,
    CODEC_BLOCKED
}

/**
 * §6.3 Codec Specification Metadata.
 */
data class CodecSpec(
    val codecId: String,
    val protocol: IrProtocol,
    val aliases: Set<String>,
    val defaultCarrierHz: Int,
    val carrierHzRange: IntRange,
    val repeatPolicy: RepeatPolicy,
    val status: CodecVerificationStatus,
    val goldenVectorCount: Int
)

/**
 * §6.3 Protocol Codec Registry.
 *
 * Authoritative registry of supported IR protocol codecs. Codecs that have not passed
 * physical HIL or golden vector tests are marked [CodecVerificationStatus.CODEC_BLOCKED]
 * and cannot be used in production.
 */
object ProtocolCodecRegistry {
    private val registeredCodecs = mapOf(
        "NEC" to CodecSpec(
            codecId = "NEC",
            protocol = IrProtocol.Nec,
            aliases = setOf("NEC1", "NEC_38"),
            defaultCarrierHz = 38000,
            carrierHzRange = 36000..40000,
            repeatPolicy = RepeatPolicy.SPECIAL_REPEAT_FRAME,
            status = CodecVerificationStatus.GOLDEN_VECTOR_VERIFIED,
            goldenVectorCount = 12
        ),
        "NECx" to CodecSpec(
            codecId = "NECx",
            protocol = IrProtocol.NecExtended,
            aliases = setOf("NECEXT", "NEC_EXTENDED"),
            defaultCarrierHz = 38000,
            carrierHzRange = 36000..40000,
            repeatPolicy = RepeatPolicy.SPECIAL_REPEAT_FRAME,
            status = CodecVerificationStatus.GOLDEN_VECTOR_VERIFIED,
            goldenVectorCount = 10
        ),
        "SAMSUNG" to CodecSpec(
            codecId = "SAMSUNG",
            protocol = IrProtocol.Samsung,
            aliases = setOf("SAMSUNG32"),
            defaultCarrierHz = 38000,
            carrierHzRange = 36000..40000,
            repeatPolicy = RepeatPolicy.FULL_FRAME,
            status = CodecVerificationStatus.GOLDEN_VECTOR_VERIFIED,
            goldenVectorCount = 8
        ),
        "SIRC" to CodecSpec(
            codecId = "SIRC",
            protocol = IrProtocol.SonySirc,
            aliases = setOf("SONY", "SIRC12", "SIRC15", "SIRC20"),
            defaultCarrierHz = 40000,
            carrierHzRange = 38000..42000,
            repeatPolicy = RepeatPolicy.FULL_FRAME,
            status = CodecVerificationStatus.GOLDEN_VECTOR_VERIFIED,
            goldenVectorCount = 6
        ),
        "RC5" to CodecSpec(
            codecId = "RC5",
            protocol = IrProtocol.Rc5,
            aliases = setOf("RC5X"),
            defaultCarrierHz = 36000,
            carrierHzRange = 35000..38000,
            repeatPolicy = RepeatPolicy.TOGGLE_PER_NEW_PRESS,
            status = CodecVerificationStatus.GOLDEN_VECTOR_VERIFIED,
            goldenVectorCount = 5
        ),
        "RC6" to CodecSpec(
            codecId = "RC6",
            protocol = IrProtocol.Rc6,
            aliases = setOf("RC6_0"),
            defaultCarrierHz = 36000,
            carrierHzRange = 35000..38000,
            repeatPolicy = RepeatPolicy.TOGGLE_PER_NEW_PRESS,
            status = CodecVerificationStatus.GOLDEN_VECTOR_VERIFIED,
            goldenVectorCount = 4
        ),
        "KASEIKYO" to CodecSpec(
            codecId = "KASEIKYO",
            protocol = IrProtocol.Kaseikyo,
            aliases = setOf("PANASONIC"),
            defaultCarrierHz = 38000,
            carrierHzRange = 36000..40000,
            repeatPolicy = RepeatPolicy.FULL_FRAME,
            status = CodecVerificationStatus.GOLDEN_VECTOR_VERIFIED,
            goldenVectorCount = 4
        )
    )

    fun getCodec(codecId: String): CodecSpec? {
        val key = codecId.trim().uppercase()
        return registeredCodecs[key] ?: registeredCodecs.values.firstOrNull { key in it.aliases }
    }

    fun isCodecTransmittable(codecId: String): Boolean {
        val codec = getCodec(codecId) ?: return false
        return codec.status != CodecVerificationStatus.CODEC_BLOCKED
    }

    fun allRegistered(): List<CodecSpec> = registeredCodecs.values.toList()
}
