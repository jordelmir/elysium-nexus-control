package com.elysium.nexus.ui.editor

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlElement
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect
import com.elysium.nexus.core.profile.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [EditorActions] — the editor's pure-data
 * transformations.
 *
 * The Compose composables ([EditorCanvas],
 * [EditorToolbar], [ProfileSelector]) are the
 * Android adapters. The composables dispatch a
 * gesture or chip tap; [EditorActions] computes the
 * new profile. The test surface is the same data
 * that the production code uses, so the unit tests
 * verify the editor's *semantics* without going
 * through Compose's runtime (the Compose UI tests
 * landed in Phase 1.4+ after the Robolectric
 * activity-resolution regression is sorted out).
 *
 * The trade-off: these tests verify the *callback
 * wiring* (i.e. "Add button → callback fires with
 * the new control's data"). The *rendering* (i.e.
 * "the chip is on screen at bounds [x, y]") is
 * covered by the on-device end-to-end test
 * (`adb shell input tap`). Together, the two test
 * surfaces cover the editor's full behaviour.
 */
class EditorActionsTest {

    private fun emptyProfile(id: Int = 0): Profile = Profile(
        id = id,
        name = "test",
        author = "tester",
        controls = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun profileWithOneControl(
        id: Int = 0,
        control: ControlElement = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.4f, 0.4f, 0.2f, 0.2f),
            binding = CanonicalBinding.Neutralize
        )
    ): Profile = Profile(
        id = id,
        name = "test",
        author = "tester",
        controls = listOf(control),
        createdAt = 0L,
        updatedAt = 0L
    )

    // ---- addControl ----

    @Test
    fun addControlOnEmptyProfileStartsAtIdZero() {
        val profile = emptyProfile()
        val updated = EditorActions.addControl(profile, ControlKind.Button, now = 1000L)
        assertEquals(1, updated.controls.size)
        assertEquals(0, updated.controls[0].id)
        assertEquals(ControlType.Button, updated.controls[0].type)
        assertEquals(1000L, updated.updatedAt)
    }

    @Test
    fun addControlIncrementsId() {
        val profile = profileWithOneControl()
        val updated = EditorActions.addControl(profile, ControlKind.Stick, now = 2000L)
        assertEquals(2, updated.controls.size)
        assertEquals(1, updated.controls[1].id)
        assertEquals(ControlType.Stick, updated.controls[1].type)
    }

    @Test
    fun addButtonCreatesNeutralizeBinding() {
        val profile = emptyProfile()
        val updated = EditorActions.addControl(profile, ControlKind.Button, now = 0L)
        assertEquals(CanonicalBinding.Neutralize, updated.controls[0].binding)
    }

    @Test
    fun addStickCreatesLeftStickBinding() {
        val profile = emptyProfile()
        val updated = EditorActions.addControl(profile, ControlKind.Stick, now = 0L)
        assertEquals(CanonicalBinding.Stick(StickSide.Left), updated.controls[0].binding)
    }

    @Test
    fun addTriggerCreatesLeftTriggerBinding() {
        val profile = emptyProfile()
        val updated = EditorActions.addControl(profile, ControlKind.Trigger, now = 0L)
        assertEquals(CanonicalBinding.Trigger(StickSide.Left), updated.controls[0].binding)
    }

    @Test
    fun addControlNextIdReportsCorrectly() {
        val profile = profileWithOneControl()
        assertEquals(1, EditorActions.nextControlId(profile))
        val updated = EditorActions.addControl(profile, ControlKind.Button, now = 0L)
        assertEquals(2, EditorActions.nextControlId(updated))
    }

    // ---- removeControl ----

    @Test
    fun removeControlRemovesById() {
        val profile = Profile(
            id = 0,
            name = "test",
            author = "tester",
            controls = listOf(
                ControlElement(
                    id = 0,
                    type = ControlType.Button,
                    visualBounds = NormalizedRect.CENTERED_SMALL,
                    binding = CanonicalBinding.Neutralize
                ),
                ControlElement(
                    id = 1,
                    type = ControlType.Stick,
                    visualBounds = NormalizedRect.CENTERED_SMALL,
                    binding = CanonicalBinding.Stick(StickSide.Right)
                )
            ),
            createdAt = 0L,
            updatedAt = 0L
        )
        val updated = EditorActions.removeControl(profile, controlId = 0, now = 2000L)
        assertEquals(1, updated.controls.size)
        assertEquals(1, updated.controls[0].id)
        assertEquals(2000L, updated.updatedAt)
    }

    @Test
    fun removeControlWithMissingIdIsNoOp() {
        val profile = profileWithOneControl()
        val updated = EditorActions.removeControl(profile, controlId = 99, now = 2000L)
        assertEquals(1, updated.controls.size)
        // The updatedAt is still bumped — the operation
        // was attempted, even if it was a no-op.
        assertEquals(2000L, updated.updatedAt)
    }

    // ---- moveControl ----

    @Test
    fun moveControlUpdatesVisualBounds() {
        val profile = profileWithOneControl()
        val newBounds = NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f)
        val updated = EditorActions.moveControl(profile, 0, newBounds, now = 3000L)
        assertEquals(newBounds, updated.controls[0].visualBounds)
        assertEquals(3000L, updated.updatedAt)
    }

    @Test
    fun moveControlPreservesOtherFields() {
        val original = ControlElement(
            id = 0,
            type = ControlType.Stick,
            visualBounds = NormalizedRect(0.5f, 0.5f, 0.1f, 0.1f),
            binding = CanonicalBinding.Stick(StickSide.Left),
            rotation = 90f,
            opacity = 0.5f
        )
        val profile = profileWithOneControl(control = original)
        val newBounds = NormalizedRect(0.2f, 0.2f, 0.1f, 0.1f)
        val updated = EditorActions.moveControl(profile, 0, newBounds, now = 0L)
        val moved = updated.controls[0]
        assertEquals(ControlType.Stick, moved.type)
        assertEquals(CanonicalBinding.Stick(StickSide.Left), moved.binding)
        assertEquals(90f, moved.rotation, 1e-6f)
        assertEquals(0.5f, moved.opacity, 1e-6f)
    }

    // ---- resizeControl ----

    @Test
    fun resizeControlUpdatesBounds() {
        val profile = profileWithOneControl()
        val updated = EditorActions.resizeControl(profile, 0, newWidth = 0.3f, newHeight = 0.4f, now = 0L)
        assertEquals(0.3f, updated.controls[0].visualBounds.width, 1e-6f)
        assertEquals(0.4f, updated.controls[0].visualBounds.height, 1e-6f)
    }

    @Test
    fun resizeControlClampsToMinimum() {
        val profile = profileWithOneControl()
        val updated = EditorActions.resizeControl(profile, 0, newWidth = 0.001f, newHeight = 0.001f, now = 0L)
        assertEquals(0.05f, updated.controls[0].visualBounds.width, 1e-6f)
        assertEquals(0.05f, updated.controls[0].visualBounds.height, 1e-6f)
    }

    // ---- rotateControl ----

    @Test
    fun rotateControlUpdatesRotation() {
        val profile = profileWithOneControl()
        val updated = EditorActions.rotateControl(profile, 0, newRotation = 45f, now = 0L)
        assertEquals(45f, updated.controls[0].rotation, 1e-6f)
    }

    @Test
    fun rotateControlNormalisesNegativeDegrees() {
        val profile = profileWithOneControl()
        val updated = EditorActions.rotateControl(profile, 0, newRotation = -90f, now = 0L)
        assertEquals(270f, updated.controls[0].rotation, 1e-6f)
    }

    // ---- identity / immutability ----

    @Test
    fun addControlReturnsNewProfile() {
        val original = profileWithOneControl()
        val updated = EditorActions.addControl(original, ControlKind.Button, now = 0L)
        assertNotEquals(original, updated)
        assertEquals(1, original.controls.size)
        assertEquals(2, updated.controls.size)
    }

    @Test
    fun removeControlReturnsNewProfile() {
        val original = profileWithOneControl()
        val updated = EditorActions.removeControl(original, 0, now = 0L)
        assertNotEquals(original, updated)
        assertEquals(1, original.controls.size)
        assertEquals(0, updated.controls.size)
    }

    // ---- edge cases ----

    @Test
    fun createControlProducesCenteredBounds() {
        val control = EditorActions.createControl(0, ControlKind.Button)
        assertEquals(NormalizedRect.CENTERED_SMALL, control.visualBounds)
    }

    @Test
    fun createControlAssignsRequestedId() {
        val control = EditorActions.createControl(7, ControlKind.Stick)
        assertEquals(7, control.id)
    }

    @Test
    fun addThenRemoveRestoresOriginal() {
        val original = profileWithOneControl()
        val withAdd = EditorActions.addControl(original, ControlKind.Button, now = 1000L)
        val restored = EditorActions.removeControl(withAdd, withAdd.controls.last().id, now = 2000L)
        // The control list matches; updatedAt differs.
        assertEquals(original.controls, restored.controls)
    }

    @Test
    fun emptyProfileMoveControlThrowsBecauseNoControlExists() {
        val profile = emptyProfile()
        try {
            EditorActions.moveControl(profile, 0, NormalizedRect.CENTERED_SMALL, now = 0L)
            org.junit.Assert.fail("expected NoSuchElementException")
        } catch (_: NoSuchElementException) { }
    }
}
