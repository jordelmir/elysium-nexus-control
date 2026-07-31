package com.elysium.nexus.core.profile

import com.elysium.nexus.core.model.CanonicalButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests for [ControlElement] + [Profile] — the §15
 * data model.
 */
class ControlElementTest {

    @Test
    fun controlElementDefaultsAreSane() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect.CENTERED_SMALL,
            binding = CanonicalBinding.Button(CanonicalButton.South)
        )
        assertEquals(0, c.id)
        assertEquals(ControlType.Button, c.type)
        assertEquals(0f, c.rotation, 0f)
        assertEquals(1f, c.opacity, 0f)
        assertEquals(c.visualBounds, c.hitBounds) // default = visual
    }

    @Test
    fun negativeIdIsRejected() {
        try {
            ControlElement(
                id = -1,
                type = ControlType.Button,
                visualBounds = NormalizedRect.CENTERED_SMALL,
                binding = CanonicalBinding.Neutralize
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun outOfRangeRotationIsRejected() {
        try {
            ControlElement(
                id = 0,
                type = ControlType.Button,
                visualBounds = NormalizedRect.CENTERED_SMALL,
                binding = CanonicalBinding.Neutralize,
                rotation = 400f
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun outOfRangeOpacityIsRejected() {
        try {
            ControlElement(
                id = 0,
                type = ControlType.Button,
                visualBounds = NormalizedRect.CENTERED_SMALL,
                binding = CanonicalBinding.Neutralize,
                opacity = 1.5f
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun movedByClampsToBounds() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.5f, 0.5f, 0.2f, 0.2f),
            binding = CanonicalBinding.Neutralize
        )
        // Move by +0.5 in x — would push x to 1.0, which
        // would make x + width = 1.2 > 1. Clamp to x = 0.8.
        val moved = c.movedBy(dx = 0.5f, dy = 0f)
        assertEquals(0.8f, moved.visualBounds.x, 1e-6f)
        assertEquals(0.5f, moved.visualBounds.y, 1e-6f)
        assertEquals(0.2f, moved.visualBounds.width, 1e-6f)
    }

    @Test
    fun movedByNegativeClampsToZero() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f),
            binding = CanonicalBinding.Neutralize
        )
        val moved = c.movedBy(dx = -0.5f, dy = 0f)
        assertEquals(0f, moved.visualBounds.x, 1e-6f)
    }

    @Test
    fun movedByPreservesSize() {
        val c = ControlElement(
            id = 0,
            type = ControlType.Button,
            visualBounds = NormalizedRect(0.3f, 0.3f, 0.15f, 0.15f),
            binding = CanonicalBinding.Neutralize
        )
        val moved = c.movedBy(dx = 0.1f, dy = 0.1f)
        assertEquals(0.15f, moved.visualBounds.width, 1e-6f)
        assertEquals(0.15f, moved.visualBounds.height, 1e-6f)
    }
}
