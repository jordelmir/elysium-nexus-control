package com.elysium.nexus.fabric.profile

import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InstalledIrProfileRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storageDir: File
    private lateinit var repository: InstalledIrProfileRepository

    @Before
    fun setUp() {
        storageDir = tempFolder.newFolder("profiles_test")
        repository = InstalledIrProfileRepository(storageDir)
    }

    @Test
    fun saveAndGetProfile_persistsDataCorrectly() {
        val binding = IrCommandBinding(
            signalId = "nec-lg-vol-up",
            physicalFingerprint = "7f8a9b1c2d3e4f",
            sourceId = "flipper-irdb",
            action = IrAction.VOLUME_UP
        )

        val profile = InstalledIrProfile(
            id = "test-profile-1",
            displayName = "LG Smart TV Remote",
            brand = "LG",
            deviceType = "TV",
            model = "OLED55C3",
            remoteModel = "AKB75095307",
            codeSetId = "code-set-lg-001",
            sourceRevision = "v0.3.0",
            commands = mapOf(IrAction.VOLUME_UP to binding),
            verifiedActions = setOf(IrAction.VOLUME_UP),
            verificationStatus = VerificationStatus.VERIFIED_LAB
        )

        repository.saveProfile(profile)

        // Reload repository from disk to test persistence
        val reloadedRepository = InstalledIrProfileRepository(storageDir)
        val retrieved = reloadedRepository.getProfile("test-profile-1")

        assertNotNull(retrieved)
        assertEquals("LG Smart TV Remote", retrieved?.displayName)
        assertEquals("LG", retrieved?.brand)
        assertEquals("code-set-lg-001", retrieved?.codeSetId)
        assertEquals(1, retrieved?.commands?.size)
        assertEquals("nec-lg-vol-up", retrieved?.commands?.get(IrAction.VOLUME_UP)?.signalId)
        assertEquals(VerificationStatus.VERIFIED_LAB, retrieved?.verificationStatus)
    }

    @Test
    fun deleteProfile_removesFromStorage() {
        val profile = InstalledIrProfile(
            id = "test-profile-to-delete",
            displayName = "Delete Me Remote",
            brand = "Sony",
            codeSetId = "sony-001",
            commands = emptyMap()
        )

        repository.saveProfile(profile)
        assertNotNull(repository.getProfile("test-profile-to-delete"))

        val deleted = repository.deleteProfile("test-profile-to-delete")
        assertTrue(deleted)
        assertNull(repository.getProfile("test-profile-to-delete"))

        // Confirm deletion persists on disk
        val reloadedRepository = InstalledIrProfileRepository(storageDir)
        assertNull(reloadedRepository.getProfile("test-profile-to-delete"))
    }
}
