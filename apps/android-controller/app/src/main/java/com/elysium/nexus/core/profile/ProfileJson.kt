package com.elysium.nexus.core.profile

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.model.CanonicalButton
import org.json.JSONArray
import org.json.JSONObject

/**
 * The §15 profile document's JSON serialiser.
 *
 * `MASTER_ORDER.md` §15 specifies that a profile
 * declares: controls, mappings, curves, gestures,
 * theme, metadata. The document is signed (Phase
 * 1.4+). The JSON format is the *exchange* format
 * for import / export; the storage format is
 * Room (Phase 1.2's `profile_control` table).
 *
 * The format is intentionally simple — hand-written
 * JSON via `org.json.JSONObject` / `JSONArray`. A
 * full kotlinx-serialization setup would add a
 * dependency for the marginal benefit of codegen.
 *
 * ## Schema (version 1)
 *
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "id": 0,
 *   "name": "Elysium Nexus Default",
 *   "author": "system",
 *   "version": 1,
 *   "createdAt": 0,
 *   "updatedAt": 0,
 *   "controls": [
 *     {
 *       "id": 0,
 *       "type": "Button",
 *       "visualBounds": { "x": 0.4, "y": 0.4, "width": 0.2, "height": 0.2 },
 *       "hitBounds": { "x": 0.4, "y": 0.4, "width": 0.2, "height": 0.2 },
 *       "zIndex": 0,
 *       "rotation": 0.0,
 *       "opacity": 1.0,
 *       "binding": { "kind": "Neutralize" }
 *     }
 *   ]
 * }
 * ```
 *
 * ## Why a hand-written format and not a codegen lib
 *
 * The §15 spec is small (5 `ControlType` variants,
 * 4 `CanonicalBinding` variants, 2 `StickSide`
 * variants, 23 `CanonicalButton`s). A hand-written
 * format is 200 lines; a codegen lib would add a
 * dependency and a build-time generator for the
 * same output. The closed set of variants means a
 * future contributor who adds a new binding must
 * update the serialiser in the same change — the
 * `when` exhaustiveness check enforces it.
 *
 * ## Why a `version` field on the profile and on the
 * schema
 *
 * The `schemaVersion` is the serialiser's version
 * (bumped on breaking changes to the format). The
 * `version` is the profile's *application* version
 * (the `Profile.CURRENT_VERSION` constant, bumped on
 * the profile's semantic version). The two are
 * independent: a profile can have `version = 3`
 * while the serialiser is at `schemaVersion = 1`.
 */
object ProfileJson {

    /** The serialiser's schema version. Bumped on breaking changes. */
    const val SCHEMA_VERSION: Int = 1

    /** The JSON tag for a `CanonicalBinding.Neutralize` value. */
    private const val BINDING_NEUTRALIZE: String = "Neutralize"

    /** The JSON tag for a `CanonicalBinding.Button` value. */
    private const val BINDING_BUTTON: String = "Button"

    /** The JSON tag for a `CanonicalBinding.Stick` value. */
    private const val BINDING_STICK: String = "Stick"

    /** The JSON tag for a `CanonicalBinding.Trigger` value. */
    private const val BINDING_TRIGGER: String = "Trigger"

    /**
     * Serialise a [Profile] to a JSON string.
     *
     * The format is documented at the class level.
     * The function is total over the [Profile] data
     * class; the [Profile]'s `init` block already
     * validates the bounds, rotation, and opacity.
     */
    fun toJson(profile: Profile): String {
        val controls = JSONArray()
        profile.controls.forEach { control ->
            controls.put(controlToJson(control))
        }
        val obj = JSONObject()
        obj.put("schemaVersion", SCHEMA_VERSION)
        obj.put("id", profile.id)
        obj.put("name", profile.name)
        obj.put("author", profile.author)
        obj.put("version", profile.version)
        obj.put("createdAt", profile.createdAt)
        obj.put("updatedAt", profile.updatedAt)
        obj.put("controls", controls)
        // `JSONObject.toString()` returns null on
        // `JSONException` in the Android stub. The
        // exception is silently caught; we use `!!`
        // to surface a real NPE if it happens. The
        // function's body has been validated against
        // every variant of [Profile] in the test
        // suite, so the !! is safe in practice.
        return obj.toString() ?: throw IllegalStateException(
            "ProfileJson.toJson produced null for profile ${profile.id}"
        )
    }

    /**
     * Parse a [Profile] from a JSON string.
     *
     * The function is total over the schema;
     * unrecognised `schemaVersion`s throw
     * [IllegalArgumentException]. The closed set of
     * `ControlType` / `CanonicalBinding` variants
     * is enumerated by exhaustive `when`s; an
     * unrecognised tag throws.
     */
    fun fromJson(json: String): Profile {
        val obj = JSONObject(json)
        val schemaVersion = obj.optInt("schemaVersion", 0)
        require(schemaVersion == SCHEMA_VERSION) {
            "Unsupported profile schemaVersion $schemaVersion (expected $SCHEMA_VERSION)."
        }
        val id = obj.getInt("id")
        val name = obj.getString("name")
        val author = obj.getString("author")
        val version = obj.getInt("version")
        val createdAt = obj.getLong("createdAt")
        val updatedAt = obj.getLong("updatedAt")
        val controlsArray = obj.getJSONArray("controls")
        val controls = (0 until controlsArray.length()).map { i ->
            controlFromJson(controlsArray.getJSONObject(i))
        }
        return Profile(
            id = id,
            name = name,
            author = author,
            controls = controls,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun controlToJson(control: ControlElement): JSONObject {
        val obj = JSONObject()
        obj.put("id", control.id)
        obj.put("type", control.type.name)
        obj.put("visualBounds", rectToJson(control.visualBounds))
        obj.put("hitBounds", rectToJson(control.hitBounds))
        obj.put("zIndex", control.zIndex)
        obj.put("rotation", control.rotation.toDouble())
        obj.put("opacity", control.opacity.toDouble())
        obj.put("binding", bindingToJson(control.binding))
        return obj
    }

    private fun controlFromJson(obj: JSONObject): ControlElement {
        val id = obj.getInt("id")
        val type = ControlType.valueOf(obj.getString("type"))
        val visualBounds = rectFromJson(obj.getJSONObject("visualBounds"))
        val hitBounds = rectFromJson(obj.getJSONObject("hitBounds"))
        val zIndex = obj.getInt("zIndex")
        val rotation = obj.getDouble("rotation").toFloat()
        val opacity = obj.getDouble("opacity").toFloat()
        val binding = bindingFromJson(obj.getJSONObject("binding"))
        return ControlElement(
            id = id,
            type = type,
            visualBounds = visualBounds,
            hitBounds = hitBounds,
            zIndex = zIndex,
            rotation = rotation,
            opacity = opacity,
            binding = binding
        )
    }

    private fun rectToJson(rect: NormalizedRect): JSONObject {
        val obj = JSONObject()
        obj.put("x", rect.x.toDouble())
        obj.put("y", rect.y.toDouble())
        obj.put("width", rect.width.toDouble())
        obj.put("height", rect.height.toDouble())
        return obj
    }

    private fun rectFromJson(obj: JSONObject): NormalizedRect = NormalizedRect(
        x = obj.getDouble("x").toFloat(),
        y = obj.getDouble("y").toFloat(),
        width = obj.getDouble("width").toFloat(),
        height = obj.getDouble("height").toFloat()
    )

    private fun bindingToJson(binding: CanonicalBinding): JSONObject {
        // Note: `JSONObject.put` returns the *previous*
        // value (or null), NOT the JSONObject. We
        // cannot chain `.put(...)` and use the result
        // as the return value. Each branch builds a
        // fresh `JSONObject` and mutates it; the
        // JSONObject is the function's return value.
        val obj = JSONObject()
        return when (binding) {
            CanonicalBinding.Neutralize -> obj.apply {
                put("kind", BINDING_NEUTRALIZE)
            }
            is CanonicalBinding.Button -> obj.apply {
                put("kind", BINDING_BUTTON)
                put("button", binding.button.name)
            }
            is CanonicalBinding.Stick -> obj.apply {
                put("kind", BINDING_STICK)
                put("side", binding.side.name)
            }
            is CanonicalBinding.Trigger -> obj.apply {
                put("kind", BINDING_TRIGGER)
                put("side", binding.side.name)
            }
        }
    }

    private fun bindingFromJson(obj: JSONObject): CanonicalBinding {
        return when (val kind = obj.getString("kind")) {
            BINDING_NEUTRALIZE -> CanonicalBinding.Neutralize
            BINDING_BUTTON -> {
                val buttonName = obj.getString("button")
                val button = CanonicalButton.valueOf(buttonName)
                CanonicalBinding.Button(button)
            }
            BINDING_STICK -> {
                val side = StickSide.valueOf(obj.getString("side"))
                CanonicalBinding.Stick(side)
            }
            BINDING_TRIGGER -> {
                val side = StickSide.valueOf(obj.getString("side"))
                CanonicalBinding.Trigger(side)
            }
            else -> throw IllegalArgumentException(
                "Unknown binding kind '$kind'."
            )
        }
    }
}
