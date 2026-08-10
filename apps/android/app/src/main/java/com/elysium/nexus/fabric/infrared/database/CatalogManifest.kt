package com.elysium.nexus.fabric.infrared.database

/**
 * V06-PTG-01 §2/§9: Catalog manifest identity — pure, JVM-testable.
 *
 * The packaged `ir_catalog.manifest.json` is the SINGLE authority for what a
 * catalog build is. No duplicated hash constants are allowed in code: the
 * installed database must match the manifest's `databaseSha256`, and every
 * artifact (db, manifest, stats, rejections) carries the same `catalogBuildId`.
 */
object CatalogManifest {

    const val MIN_SCHEMA_VERSION = 5

    /** Policy version participating in catalogBuildId (kept in sync with the Python builder). */
    const val BUILD_ID_POLICY_VERSION = "v0.6-ptg-1"
    const val BUILD_ID_PREFIX = "ptg-v1"

    /** Required top-level identity keys; a manifest missing any of them is not authoritative. */
    val REQUIRED_KEYS: List<String> = listOf(
        "catalogBuildId",
        "schemaVersion",
        "databaseSha256",
        "canonicalContentSha256",
        "sourceLockSha256",
        "rejectionManifestSha256",
        "licenseManifestSha256"
    )

    data class CatalogMetadata(
        val catalogBuildId: String,
        val schemaVersion: Int,
        val databaseSha256: String,
        val canonicalContentSha256: String,
        val sourceLockSha256: String,
        val rejectionManifestSha256: String,
        val licenseManifestSha256: String
    )

    sealed interface ParseResult {
        data class Success(val metadata: CatalogMetadata) : ParseResult
        data class Failure(val reason: String) : ParseResult
    }

    /**
     * Strict schema gate (§2 metadata compatibility): the manifest is the
     * authority, therefore a missing or too-old schema is a hard rejection —
     * there is no legacy hash fallback anymore.
     */
    fun isSchemaVersionAccepted(declaredSchemaVersion: Int?): Boolean =
        declaredSchemaVersion != null && declaredSchemaVersion >= MIN_SCHEMA_VERSION

    fun parse(jsonText: String): ParseResult {
        val values = ManifestJson.parseObject(jsonText)
            ?: return ParseResult.Failure("manifest is not a parseable JSON object")

        val missing = REQUIRED_KEYS.filter { values[it] == null }
        if (missing.isNotEmpty()) {
            return ParseResult.Failure("manifest missing required identity fields: ${missing.joinToString()}")
        }

        val buildId = (values["catalogBuildId"] as? ManifestJson.Value.Str)?.v?.trim().orEmpty()
        if (buildId.isEmpty()) return ParseResult.Failure("catalogBuildId is empty")

        val schemaVersion = (values["schemaVersion"] as? ManifestJson.Value.Num)?.v?.toInt()
        if (schemaVersion == null) return ParseResult.Failure("schemaVersion is not an integer")

        fun str(key: String): String = (values[key] as? ManifestJson.Value.Str)?.v?.trim().orEmpty()

        val fields = mapOf(
            "databaseSha256" to str("databaseSha256"),
            "canonicalContentSha256" to str("canonicalContentSha256"),
            "sourceLockSha256" to str("sourceLockSha256"),
            "rejectionManifestSha256" to str("rejectionManifestSha256"),
            "licenseManifestSha256" to str("licenseManifestSha256")
        )
        val blankHashes = fields.filter { it.value.isEmpty() }
        if (blankHashes.isNotEmpty()) {
            return ParseResult.Failure("manifest hashes must be non-blank: ${blankHashes.keys.joinToString()}")
        }

        return ParseResult.Success(
            CatalogMetadata(
                catalogBuildId = buildId,
                schemaVersion = schemaVersion,
                databaseSha256 = fields.getValue("databaseSha256"),
                canonicalContentSha256 = fields.getValue("canonicalContentSha256"),
                sourceLockSha256 = fields.getValue("sourceLockSha256"),
                rejectionManifestSha256 = fields.getValue("rejectionManifestSha256"),
                licenseManifestSha256 = fields.getValue("licenseManifestSha256")
            )
        )
    }
}

/**
 * Minimal strict JSON object parser for the catalog manifest. The manifest is
 * our own artifact (flat object of strings/ints + a nested `counts` object);
 * this deliberately does NOT pull a general JSON dependency into the fabric.
 * Fails (null) on malformed input — fail-closed.
 */
internal object ManifestJson {

    sealed interface Value {
        data class Str(val v: String) : Value
        data class Num(val v: Long) : Value
    }

    fun parseObject(text: String): Map<String, Value>? {
        val s = text.trim()
        if (!s.startsWith("{") || !s.endsWith("}")) return null

        val result = LinkedHashMap<String, Value>()
        var i = 1
        val n = s.length
        while (i < n) {
            while (i < n && s[i].isWhitespace() || (i < n && s[i] == ',')) i++
            if (i >= n || s[i] == '}') break

            if (s[i] != '"') return null
            val key = readString(s, i) ?: return null
            i = key.second
            while (i < n && s[i].isWhitespace()) i++
            if (i >= n || s[i] != ':') return null
            i++
            while (i < n && s[i].isWhitespace()) i++
            if (i >= n) return null

            when (s[i]) {
                '"' -> {
                    val v = readString(s, i) ?: return null
                    result[key.first] = Value.Str(v.first)
                    i = v.second
                }
                '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    val start = i
                    while (i < n && (s[i].isDigit() || s[i] == '-' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
                    val numText = s.substring(start, i)
                    val num = numText.toLongOrNull() ?: return null
                    result[key.first] = Value.Num(num)
                }
                else -> return null
            }
        }
        return result
    }

    /** Reads a quoted JSON string starting at [start] (which must point at '"'). */
    private fun readString(s: String, start: Int): Pair<Pair<String, Int>, Int>? {
        var i = start + 1
        val sb = StringBuilder()
        val n = s.length
        while (i < n) {
            val c = s[i]
            if (c == '"') return Pair(Pair(sb.toString(), i + 1), i + 1)
            if (c == '\\') {
                i++
                if (i >= n) return null
                sb.append(
                    when (s[i]) {
                        '"' -> '"'
                        '\\' -> '\\'
                        '/' -> '/'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> return null
                    }
                )
                i++
            } else {
                sb.append(c)
                i++
            }
        }
        return null
    }
}