package com.elysium.nexus.core.profile

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.model.CanonicalButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [ProfileImporter].
 *
 * The importer is the symmetric counterpart to
 * [ProfileShareBuilder]. The test suite covers
 * the happy path, the schema-version mismatch,
 * the malformed-JSON path, the empty-input path,
 * and the round-trip through [ProfileShareBuilder].
 */
class ProfileImporterTest {

    @Test
    fun `import happy path`() {
        val profile = Profile(
            id = 7,
            name = "Imported Pack",
            author = "tester",
            controls = listOf(
                ControlElement(
                    id = 0,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect(0.4f, 0.4f, 0.2f, 0.2f),
                    hitBounds = NormalizedRect(0.4f, 0.4f, 0.2f, 0.2f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Neutralize
                )
            ),
            createdAt = 0L,
            updatedAt = 0L
        )
        val json = ProfileJson.toJson(profile)
        val result = ProfileImporter.import(json, now = 1000L)
        assertTrue(result is ProfileImportResult.Success)
        val imported = (result as ProfileImportResult.Success).profile
        assertEquals(profile.name, imported.name)
        assertEquals(profile.author, imported.author)
        assertEquals(profile.controls, imported.controls)
        // The importer stamps `createdAt` and `updatedAt` with `now`.
        assertEquals(1000L, imported.createdAt)
        assertEquals(1000L, imported.updatedAt)
    }

    @Test
    fun `import preserves the json's id`() {
        // The importer does not assign a new id;
        // the caller (the activity + repository)
        // owns the id strategy. The §15 round-trip
        // is "build → share → import → upsert
        // with a fresh id".
        val profile = Profile(
            id = 42,
            name = "Identity",
            author = "tester",
            controls = emptyList(),
            createdAt = 0L,
            updatedAt = 0L
        )
        val json = ProfileJson.toJson(profile)
        val result = ProfileImporter.import(json, now = 1000L)
        assertTrue(result is ProfileImportResult.Success)
        val imported = (result as ProfileImportResult.Success).profile
        assertEquals(42, imported.id)
    }

    @Test
    fun `import round trip with all binding variants`() {
        val original = Profile(
            id = 0,
            name = "All bindings",
            author = "tester",
            controls = listOf(
                ControlElement(
                    id = 0,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect(0.1f, 0.1f, 0.1f, 0.1f),
                    hitBounds = NormalizedRect(0.1f, 0.1f, 0.1f, 0.1f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Neutralize
                ),
                ControlElement(
                    id = 1,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect(0.2f, 0.1f, 0.1f, 0.1f),
                    hitBounds = NormalizedRect(0.2f, 0.1f, 0.1f, 0.1f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Button(CanonicalButton.South)
                ),
                ControlElement(
                    id = 2,
                    type = ControlType.Stick,
                    visualBounds = NormalizedRect(0.3f, 0.1f, 0.2f, 0.2f),
                    hitBounds = NormalizedRect(0.3f, 0.1f, 0.2f, 0.2f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Stick(StickSide.Left)
                ),
                ControlElement(
                    id = 3,
                    type = ControlType.Trigger,
                    visualBounds = NormalizedRect(0.6f, 0.1f, 0.1f, 0.2f),
                    hitBounds = NormalizedRect(0.6f, 0.1f, 0.1f, 0.2f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Trigger(StickSide.Left)
                )
            ),
            createdAt = 0L,
            updatedAt = 0L
        )
        val json = ProfileJson.toJson(original)
        val result = ProfileImporter.import(json, now = 0L)
        assertTrue(result is ProfileImportResult.Success)
        val imported = (result as ProfileImportResult.Success).profile
        // Modulo id / timestamps, the import equals
        // the original.
        assertEquals(original.name, imported.name)
        assertEquals(original.controls, imported.controls)
    }

    @Test
    fun `import rejects an empty string`() {
        val result = ProfileImporter.import("", now = 0L)
        assertTrue(result is ProfileImportResult.Failure)
    }

    @Test
    fun `import rejects malformed JSON`() {
        val result = ProfileImporter.import("not a json", now = 0L)
        assertTrue(result is ProfileImportResult.Failure)
    }

    @Test
    fun `import rejects an unsupported schema version`() {
        val json = """{ "schemaVersion": 999, "id": 0, "name": "x", "author": "y", "version": 1, "createdAt": 0, "updatedAt": 0, "controls": [] }"""
        val result = ProfileImporter.import(json, now = 0L)
        assertTrue(result is ProfileImportResult.Failure)
        val reason = (result as ProfileImportResult.Failure).reason
        assertNotNull(reason)
        assertTrue(
            "Reason should mention schema version: $reason",
            reason.contains("schemaVersion") || reason.contains("schema")
        )
    }

    @Test
    fun `import rejects an unrecognised control type`() {
        val json = """{ "schemaVersion": 1, "id": 0, "name": "x", "author": "y", "version": 1, "createdAt": 0, "updatedAt": 0, "controls": [ { "id": 0, "type": "NotARealType", "visualBounds": { "x": 0.1, "y": 0.1, "width": 0.1, "height": 0.1 }, "hitBounds": { "x": 0.1, "y": 0.1, "width": 0.1, "height": 0.1 }, "zIndex": 0, "rotation": 0.0, "opacity": 1.0, "binding": { "kind": "Neutralize" } } ] }"""
        val result = ProfileImporter.import(json, now = 0L)
        assertTrue(result is ProfileImportResult.Failure)
    }

    @Test
    fun `import rejects a profile with an invalid name`() {
        // Profile.init requires `name.isNotBlank()`.
        // A valid JSON envelope with an empty name
        // passes ProfileJson.fromJson, then fails
        // Profile's init. The importer catches
        // IllegalArgumentException and returns
        // Failure.
        val json = """{ "schemaVersion": 1, "id": 0, "name": "", "author": "y", "version": 1, "createdAt": 0, "updatedAt": 0, "controls": [] }"""
        val result = ProfileImporter.import(json, now = 0L)
        // The ProfileJson.fromJson call constructs
        // a Profile which throws on `name.isNotBlank()`.
        // That throw propagates as
        // `IllegalArgumentException`, which the
        // importer catches.
        assertTrue(result is ProfileImportResult.Failure)
    }

    @Test
    fun `import returns a typed failure envelope with a cause`() {
        val result = ProfileImporter.import("not a json", now = 0L)
        assertTrue(result is ProfileImportResult.Failure)
        val failure = result as ProfileImportResult.Failure
        assertNotNull(failure.cause)
        assertNotNull(failure.reason)
    }

    @Test
    fun `round trip share → import preserves the controls`() {
        val original = Profile.defaultProfile(now = 0L).copy(
            name = "Round Trip",
            controls = listOf(
                ControlElement(
                    id = 0,
                    type = ControlType.Stick,
                    visualBounds = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f),
                    hitBounds = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f),
                    zIndex = 0,
                    rotation = 0f,
                    opacity = 0.8f,
                    binding = CanonicalBinding.Stick(StickSide.Left)
                ),
                ControlElement(
                    id = 1,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect(0.6f, 0.6f, 0.1f, 0.1f),
                    hitBounds = NormalizedRect(0.6f, 0.6f, 0.1f, 0.1f),
                    zIndex = 1,
                    rotation = 0f,
                    opacity = 1f,
                    binding = CanonicalBinding.Button(CanonicalButton.North)
                )
            )
        )
        val share = ProfileShareBuilder.build(original)
        val result = ProfileImporter.import(share.content, now = 100L)
        assertTrue(result is ProfileImportResult.Success)
        val imported = (result as ProfileImportResult.Success).profile
        assertEquals(original.name, imported.name)
        assertEquals(original.controls, imported.controls)
        // Timestamps are local to the importer call.
        assertEquals(100L, imported.createdAt)
        assertEquals(100L, imported.updatedAt)
    }
}
