package com.elysium.nexus.fabric.infrared.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-PHASE 5 — Schema-v5 gate policy tests.
 *
 * Proves the catalog-version acceptance contract:
 * - v5+ accepted; v4 and below rejected
 * - absent manifest version accepted (SHA-256 gate still applies)
 */
class CatalogSchemaVersionGateTest {

    @Test
    fun `accepts schema v5`() {
        assertTrue(isCatalogSchemaVersionAccepted(5))
    }

    @Test
    fun `accepts schema v6 and above`() {
        assertTrue(isCatalogSchemaVersionAccepted(6))
        assertTrue(isCatalogSchemaVersionAccepted(99))
    }

    @Test
    fun `rejects schema v4`() {
        assertFalse(isCatalogSchemaVersionAccepted(4))
    }

    @Test
    fun `rejects schema v0 through v4`() {
        for (v in 0..4) {
            assertFalse("v$v must be rejected", isCatalogSchemaVersionAccepted(v))
        }
    }

    @Test
    fun `rejects negative schema versions`() {
        assertFalse(isCatalogSchemaVersionAccepted(-1))
    }

    @Test
    fun `absent manifest version is accepted as last resort`() {
        // A missing manifest cannot be validated; the hard SHA-256 gate
        // (EXPECTED_MANIFEST_HASH) is the real enforcement — the schema gate
        // only rejects when a version is declared and too old.
        assertTrue(isCatalogSchemaVersionAccepted(null))
    }
}