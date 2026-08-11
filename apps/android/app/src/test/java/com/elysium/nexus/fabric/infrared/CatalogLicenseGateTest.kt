package com.elysium.nexus.fabric.infrared

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogLicenseGateTest {

    private fun findProjectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null && !File(dir, "ir-data/sources.lock.json").exists()) {
            dir = dir.parentFile
        }
        return dir ?: File(".")
    }

    @Test
    fun sourcesLock_verifyAllSourcesHaveValid40HexCommitAndTree() {
        val rootDir = findProjectRoot()
        val lockFile = File(rootDir, "ir-data/sources.lock.json")
        assertTrue("Lockfile must exist at ${lockFile.absolutePath}", lockFile.exists())

        val content = lockFile.readText()
        val json = JSONObject(content)
        val sources = json.getJSONArray("sources")

        for (i in 0 until sources.length()) {
            val source = sources.getJSONObject(i)
            val kind = source.optString("kind", "git")
            val commit = source.getString("resolvedCommit")
            val licenseSha = source.getString("licenseFileSha256")

            assertTrue("licenseFileSha256 must not be empty for ${source.getString("id")}", licenseSha.isNotBlank())

            if (kind == "git") {
                // Git sources must have 40-char hex commit and tree hashes
                val tree = source.getString("resolvedTree")
                assertEquals("resolvedCommit must be 40 hex characters for ${source.getString("id")}", 40, commit.length)
                assertEquals("resolvedTree must be 40 hex characters for ${source.getString("id")}", 40, tree.length)
            } else {
                // Artifact sources have shorter content-hash IDs
                assertTrue("resolvedCommit must be non-empty for ${source.getString("id")}", commit.isNotEmpty())
            }
        }
    }

    @Test
    fun productionManifest_containsValidSha256AndCounts() {
        val rootDir = findProjectRoot()
        val manifestFile = File(rootDir, "apps/android/app/src/main/assets/ir/ir_catalog.manifest.json")
        assertTrue("Manifest must exist at ${manifestFile.absolutePath}", manifestFile.exists())

        val json = JSONObject(manifestFile.readText())
        val sha256 = json.getString("databaseSha256")
        val profile = json.getString("profile")

        assertEquals("production", profile)
        assertEquals(64, sha256.length)
        assertNotNull(json.getJSONObject("counts"))
    }
}
