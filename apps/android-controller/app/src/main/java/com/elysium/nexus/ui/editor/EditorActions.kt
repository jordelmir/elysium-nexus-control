package com.elysium.nexus.ui.editor

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlElement
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect
import com.elysium.nexus.core.profile.Profile

/**
 * The editor's pure-data actions.
 *
 * `MASTER_ORDER.md` §15 calls for a controls editor
 * with "drag, scale, rotate, duplicate, group, lock,
 * align, distribute, opacity". Each of these is a
 * pure-data transformation on the [Profile] — the
 * function takes the current profile and returns a
 * new one with the change applied.
 *
 * The Compose composables ([EditorCanvas],
 * [EditorToolbar], [ProfileSelector]) are the
 * Android adapters. They observe the [Profile],
 * dispatch a gesture / chip tap, and call one of
 * the functions here to compute the new state.
 * The functions are **stateless** and **JVM-
 * testable** — the test surface is the same data
 * that the production code uses, so the unit tests
 * verify the editor's *semantics* without going
 * through Compose's runtime.
 *
 * ## Why a static class and not an `interface` or
 * `class` with DI
 *
 * The functions are pure: the only state they read
 * is the inputs. A `class` instance would have no
 * fields, so a `class` adds no value over a top-
 * level `object`. A `companion object` inside the
 * composable would couple the test to the
 * composable; a separate file keeps the test
 * surface decoupled.
 */
object EditorActions {

    /**
     * Build a fresh [ControlElement] with the given
     * [id] and [kind]. The position is centred
     * ([NormalizedRect.CENTERED_SMALL]); the user
     * drags the control to its final position.
     */
    fun createControl(id: Int, kind: ControlKind): ControlElement {
        val bounds = NormalizedRect.CENTERED_SMALL
        val binding: CanonicalBinding = when (kind) {
            ControlKind.Button -> CanonicalBinding.Neutralize
            ControlKind.Stick -> CanonicalBinding.Stick(StickSide.Left)
            ControlKind.Trigger -> CanonicalBinding.Trigger(StickSide.Left)
        }
        val type: ControlType = when (kind) {
            ControlKind.Button -> ControlType.Button
            ControlKind.Stick -> ControlType.Stick
            ControlKind.Trigger -> ControlType.Trigger
        }
        return ControlElement(
            id = id,
            type = type,
            visualBounds = bounds,
            binding = binding
        )
    }

    /**
     * Add a new control of [kind] to the [profile].
     * The new control's id is `max(existing) + 1`
     * (or 0 if the profile is empty). The
     * `updatedAt` is set to [now].
     */
    fun addControl(profile: Profile, kind: ControlKind, now: Long): Profile {
        val newId = (profile.controls.maxOfOrNull { it.id } ?: -1) + 1
        return profile.withControlAdded(createControl(newId, kind), now)
    }

    /**
     * Remove the control with [controlId] from the
     * [profile]. The `updatedAt` is set to [now].
     * If no such control exists, the profile is
     * returned unchanged.
     */
    fun removeControl(profile: Profile, controlId: Int, now: Long): Profile =
        profile.withControlRemoved(controlId, now)

    /**
     * Move the control with [controlId] to the new
     * [newVisualBounds]. The bounds are already
     * clamped (the editor's gesture pipeline does
     * the clamping). The `updatedAt` is set to
     * [now].
     */
    fun moveControl(
        profile: Profile,
        controlId: Int,
        newVisualBounds: NormalizedRect,
        now: Long
    ): Profile = profile.withControlReplaced(
        controlId = controlId,
        updated = profile.controls.first { it.id == controlId }
            .copy(visualBounds = newVisualBounds),
        now = now
    )

    /**
     * Resize the control with [controlId] to the
     * new [newWidth] and [newHeight] (normalised).
     * The bounds are clamped in
     * [ControlElement.resized]. The `updatedAt` is
     * set to [now].
     */
    fun resizeControl(
        profile: Profile,
        controlId: Int,
        newWidth: Float,
        newHeight: Float,
        now: Long
    ): Profile = profile.withControlReplaced(
        controlId = controlId,
        updated = profile.controls.first { it.id == controlId }
            .resized(newWidth = newWidth, newHeight = newHeight),
        now = now
    )

    /**
     * Rotate the control with [controlId] to the
     * new [newRotation] (degrees, normalised to
     * `[0, 360]` in [ControlElement.rotated]). The
     * `updatedAt` is set to [now].
     */
    fun rotateControl(
        profile: Profile,
        controlId: Int,
        newRotation: Float,
        now: Long
    ): Profile = profile.withControlReplaced(
        controlId = controlId,
        updated = profile.controls.first { it.id == controlId }
            .rotated(newRotation = newRotation),
        now = now
    )

    /**
     * @return the next id that
     * [addControl] will use for a new control.
     * Useful for tests that want to know what id
     * the new control will have without having to
     * inspect the profile after the call.
     */
    fun nextControlId(profile: Profile): Int =
        (profile.controls.maxOfOrNull { it.id } ?: -1) + 1
}
