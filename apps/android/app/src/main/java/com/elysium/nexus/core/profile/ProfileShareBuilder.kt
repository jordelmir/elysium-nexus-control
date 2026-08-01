package com.elysium.nexus.core.profile

import java.util.Locale

/**
 * Pure builder that turns a [Profile] into a
 * [ProfileShare] artifact.
 *
 * The builder is the JVM-testeable half of the
 * §15 export story. The Android half lives in
 * [AndroidProfileShareLauncher].
 *
 * The filename convention is
 * `elysium-profile-{id}-{slug}.json` where `slug`
 * is the profile's name lower-cased, ASCII-only,
 * with runs of non-alphanumeric characters
 * replaced by `-`. The slug is capped at 32
 * characters; an empty slug is replaced by
 * `untitled`. The id is the profile's database id.
 *
 * The MIME type is the standard
 * `application/vnd.elysium.profile+json` (a
 * vendor-specific subtype of JSON). The MIME type
 * is the one that a fully-typed Android share
 * sheet will surface as a "Profile" target.
 *
 * ## Why a `slug` for the filename
 *
 * The profile's `name` is user-typed, free-form,
 * and possibly non-ASCII. A slug is the simplest
 * way to keep the filename portable across
 * filesystems (no spaces, no special characters,
 * a sane length cap). The original name lives
 * inside the JSON document; the filename is
 * presentation only.
 *
 * ## Why we do not sign here
 *
 * The §15 signature is a JSON field, computed by
 * the caller (the [ProfileSignature] family in
 * Phase 1.6+). The builder does not sign because
 * signing needs a key (Keystore) or a secret
 * (HMAC); the builder is a pure function.
 */
object ProfileShareBuilder {

    /** The MIME type emitted by [build]. */
    const val MIME_TYPE: String = "application/vnd.elysium.profile+json"

    /**
     * Build a [ProfileShare] for the given [profile].
     *
     * The function is total over the [Profile]
     * domain; the [Profile]'s `init` block has
     * already validated the bounds, rotation, and
     * opacity.
     */
    fun build(profile: Profile): ProfileShare {
        val json = ProfileJson.toJson(profile)
        val filename = filenameFor(profile)
        return ProfileShare(
            filename = filename,
            mimeType = MIME_TYPE,
            content = json
        )
    }

    /**
     * Compute the share filename for a profile.
     *
     * The filename is the public surface of the
     * artifact: visible in the user's "Recent
     * files" list, in the share sheet, and in the
     * "save to drive" target. The function is
     * exposed so callers (e.g. tests, importers)
     * can derive the same filename without going
     * through the full builder.
     */
    fun filenameFor(profile: Profile): String {
        val slug = slugOf(profile.name)
        return "elysium-profile-${profile.id}-$slug.json"
    }

    /**
     * Compute the slug of a profile name. The slug
     * is lower-case ASCII, runs of non-alphanumeric
     * characters become a single `-`, and the
     * result is capped at 32 characters. An empty
     * slug is replaced by `untitled`.
     *
     * Exposed for the test suite.
     */
    fun slugOf(name: String): String {
        val lowered = name.lowercase(Locale.ROOT)
        val builder = StringBuilder()
        var lastWasDash = false
        for (ch in lowered) {
            // We accept ASCII letters and digits
            // (the portable set). Everything else
            // (whitespace, punctuation, non-ASCII
            // letters) is a *separator*: it does not
            // appear in the slug; it triggers a dash
            // if the buffer is non-empty and we have
            // not just appended a dash. A separator
            // at the start of the name (or after a
            // run of separators) is dropped silently.
            val isAsciiAlnum = ch.isLetterOrDigit() && ch.code < 128
            if (isAsciiAlnum) {
                builder.append(ch)
                lastWasDash = false
            } else {
                if (builder.isNotEmpty() && !lastWasDash) {
                    builder.append('-')
                    lastWasDash = true
                }
            }
        }
        // Trim trailing dashes.
        while (builder.isNotEmpty() && builder.last() == '-') {
            builder.deleteCharAt(builder.length - 1)
        }
        val capped = if (builder.length > 32) builder.substring(0, 32) else builder.toString()
        return capped.ifEmpty { "untitled" }
    }
}
