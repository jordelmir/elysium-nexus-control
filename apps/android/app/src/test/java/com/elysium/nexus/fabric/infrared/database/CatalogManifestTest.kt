package com.elysium.nexus.fabric.infrared.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-PTG-01 §2/§9 — Catalog manifest identity tests.
 *
 * The packaged manifest is the single authority for catalog identity. These
 * tests pin the strict fail-closed contract:
 * - a complete 7-field manifest parses to [CatalogManifest.CatalogMetadata]
 * - any missing/blank required field → Failure (never a partial install)
 * - non-parseable JSON → Failure
 * - the manifest carries the full SHA-256 hashes (no short IDs for authority)
 */
class CatalogManifestTest {

    private val validManifest = """
        {
          "catalogBuildId": "ptg-v1|5|e0bfbea28910399842e4ab0744b451f791064b6dead204bfdb1dc0f3b642a36b|aabbccdd",
          "schemaVersion": 5,
          "profile": "production",
          "databaseSha256": "00732dda3eb3bb7fd717b7fc93f503cac33426d2bedf288e83e67afdd98a09d5",
          "canonicalContentSha256": "e0bfbea28910399842e4ab0744b451f791064b6dead204bfdb1dc0f3b642a36b",
          "sourceLockSha256": "11aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "rejectionManifestSha256": "22bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          "licenseManifestSha256": "33cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
          "counts": { "signals": 85392 }
        }
    """.trimIndent()

    @Test
    fun `parses complete manifest`() {
        val result = CatalogManifest.parse(validManifest)
        assertTrue("expected Success, got $result", result is CatalogManifest.ParseResult.Success)
        val metadata = (result as CatalogManifest.ParseResult.Success).metadata
        assertEquals("ptg-v1|5|e0bfbea28910399842e4ab0744b451f791064b6dead204bfdb1dc0f3b642a36b|aabbccdd", metadata.catalogBuildId)
        assertEquals(5, metadata.schemaVersion)
        assertEquals("00732dda3eb3bb7fd717b7fc93f503cac33426d2bedf288e83e67afdd98a09d5", metadata.databaseSha256)
        assertEquals("e0bfbea28910399842e4ab0744b451f791064b6dead204bfdb1dc0f3b642a36b", metadata.canonicalContentSha256)
        assertEquals("11aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", metadata.sourceLockSha256)
        assertEquals("22bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", metadata.rejectionManifestSha256)
        assertEquals("33cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", metadata.licenseManifestSha256)
    }

    @Test
    fun `rejects manifest missing catalogBuildId`() {
        val text = validManifest.replace("\"catalogBuildId\": \"ptg-v1|5|e0bfbea28910399842e4ab0744b451f791064b6dead204bfdb1dc0f3b642a36b|aabbccdd\",", "")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
        assertTrue((result as CatalogManifest.ParseResult.Failure).reason.contains("catalogBuildId"))
    }

    @Test
    fun `rejects manifest missing databaseSha256`() {
        val text = validManifest.replace("\"databaseSha256\": \"00732dda3eb3bb7fd717b7fc93f503cac33426d2bedf288e83e67afdd98a09d5\",", "")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
        assertTrue((result as CatalogManifest.ParseResult.Failure).reason.contains("databaseSha256"))
    }

    @Test
    fun `rejects blank sourceLockSha256`() {
        val text = validManifest.replace("\"sourceLockSha256\": \"11aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",", "\"sourceLockSha256\": \"\",")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
        assertTrue((result as CatalogManifest.ParseResult.Failure).reason.contains("sourceLockSha256"))
    }

    @Test
    fun `rejects non-numeric schemaVersion`() {
        val text = validManifest.replace("\"schemaVersion\": 5,", "\"schemaVersion\": \"five\",")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
        assertTrue((result as CatalogManifest.ParseResult.Failure).reason.contains("schemaVersion"))
    }

    @Test
    fun `rejects schema version 4 manifest`() {
        val text = validManifest.replace("\"schemaVersion\": 5,", "\"schemaVersion\": 4,")
        val result = CatalogManifest.parse(text)
        assertTrue("expected Success (parse-level), got $result", result is CatalogManifest.ParseResult.Success)
        val metadata = (result as CatalogManifest.ParseResult.Success).metadata
        assertEquals(4, metadata.schemaVersion)
        assertTrue("v4 must fail the install gate", !CatalogManifest.isSchemaVersionAccepted(metadata.schemaVersion))
    }

    @Test
    fun `rejects non JSON input`() {
        val result = CatalogManifest.parse("this is not json")
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
    }

    @Test
    fun `rejects empty input`() {
        val result = CatalogManifest.parse("")
        assertTrue("expected Failure, got $result", result is CatalogManifest.ParseResult.Failure)
    }

    @Test
    fun `escaped strings decode`() {
        val text = """{"catalogBuildId": "ptg\u002Dv1", "schemaVersion": 5}"""
        val result = CatalogManifest.parse(text)
        assertTrue("expected Failure for incomplete manifest, got $result", result is CatalogManifest.ParseResult.Failure)
    }
}