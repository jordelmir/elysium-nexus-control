package com.elysium.nexus.core.profile

/**
 * The result of a [ProfileImporter.import] call.
 *
 * The importer is total: every input produces a
 * result. The result is either [Success] (a
 * fully-validated [Profile]) or [Failure]
 * (a reason the import was rejected).
 *
 * The shape mirrors the standard "parse, don't
 * validate" pattern: the [Success] is a real
 * [Profile] (the data class's `init` block has
 * already validated the bounds, the rotation,
 * the opacity). The [Failure] is a typed
 * envelope; the caller surfaces the reason to
 * the user.
 */
sealed class ProfileImportResult {
    /** The import succeeded; the [profile] is ready to persist. */
    data class Success(val profile: Profile) : ProfileImportResult()

    /**
     * The import failed; the [reason] is a
     * human-readable description of the failure
     * (and is also safe to log). The [cause] is
     * the underlying exception, if any; the field
     * is non-null when the failure is an
     * `IllegalArgumentException` from a parser
     * (e.g. `ProfileJson.fromJson`).
     */
    data class Failure(
        val reason: String,
        val cause: Throwable? = null
    ) : ProfileImportResult()
}

/**
 * The §15 profile import story.
 *
 * The importer is the symmetric counterpart to
 * [ProfileShareBuilder]. The builder turns a
 * [Profile] into a [ProfileShare] (a JSON
 * document); the importer turns a JSON document
 * into a [Profile]. The two halves are
 * mirror-images: the builder is total, the
 * importer is also total, the round-trip is
 * `Profile → JSON → Profile'` with
 * `Profile == Profile'` (modulo a fresh
 * `createdAt` / `updatedAt`).
 *
 * The importer does **not** assign a new `id`;
 * the repository owns the id strategy. The
 * importer returns the JSON's `id` as the
 * profile's `id` (the caller may overwrite
 * with `repository.nextId()` to avoid
 * collisions). The §15 "import preserves
 * identity" milestone is a follow-up; for now,
 * the caller decides.
 *
 * The importer stamps `createdAt` and
 * `updatedAt` with the import time. The
 * rationale: the JSON's timestamps are the
 * source's local clock; the receiver's
 * "this profile was created at" should be
 * the local clock of the moment the
 * receiver added the profile.
 *
 * ## Why a result envelope and not a `try` / `catch`
 *
 * The [ProfileJson.fromJson] function throws on
 * schema mismatch, on unrecognised control
 * types, on unrecognised bindings. Wrapping the
 * call in a `try` / `catch` and re-throwing as
 * a [ProfileImportResult.Failure] is the
 * standard "translate exceptions to typed
 * errors" pattern. The agent-memory rule
 * (`Result<Throwable>` vs typed error
 * envelopes) is exactly the same: a typed
 * envelope is a stronger contract than a
 * `Result<Throwable>`.
 */
object ProfileImporter {

    /**
     * Import a [Profile] from a JSON document.
     *
     * The function is total: every input
     * produces a [ProfileImportResult]. The
     * [now] parameter is the local clock at the
     * moment of import; the default is
     * `System.currentTimeMillis()`.
     */
    fun import(json: String, now: Long = System.currentTimeMillis()): ProfileImportResult {
        val parsed: Profile = try {
            ProfileJson.fromJson(json)
        } catch (e: IllegalArgumentException) {
            // ProfileJson throws IllegalArgumentException
            // for schema mismatches and for
            // unrecognised `ControlType` /
            // `CanonicalBinding` variants.
            return ProfileImportResult.Failure(
                reason = e.message ?: "Invalid profile JSON",
                cause = e
            )
        } catch (e: Throwable) {
            // org.json throws JSONException for
            // malformed JSON. The Android stub
            // sometimes returns default values
            // instead of throwing; the JVM test
            // uses the real `org.json:json`
            // reference impl.
            return ProfileImportResult.Failure(
                reason = "Malformed profile JSON: ${e.message ?: e::class.simpleName}",
                cause = e
            )
        }
        // The parsed profile is validated by
        // `Profile.init` (name, author, version,
        // timestamps). The JSON's id is the
        // source's local id; the caller (the
        // activity + repository) decides whether
        // to overwrite with a fresh id.
        return try {
            val imported = parsed.copy(
                createdAt = now,
                updatedAt = now
            )
            ProfileImportResult.Success(imported)
        } catch (e: IllegalArgumentException) {
            ProfileImportResult.Failure(
                reason = e.message ?: "Imported profile failed validation",
                cause = e
            )
        }
    }
}
