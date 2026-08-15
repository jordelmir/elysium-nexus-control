package com.elysium.nexus.tvnode.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.elysium.nexus.tvnode.canonical.Direction
import com.elysium.nexus.tvnode.application.TvNodeApp

/**
 * NexusAccessibilityService — the user-granted observation + global-key
 * route (TV-FABRIC.5).
 *
 * Honest contract:
 * - We OBSERVE key events and window focus; we never consume events by
 *   default (onKeyEvent returns false → the event flows to the app).
 * - Global HOME/BACK and DPAD actions are ONLY available on API 33+
 *   (GLOBAL_ACTION_DPAD_*) and are NEVER executed without an explicit
 *   pending user intent from the phone (execution is gated in the
 *   action executor).
 * - We do not perform clicks/gestures programmatically on third-party
 *   apps: gesture injection is reserved for lab/engineering channels.
 */
class NexusAccessibilityService : AccessibilityService() {

    private val app: TvNodeApp get() = application as TvNodeApp

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Surface the granted state to the identity/manifest layer.
        app.onAccessibilityGranted(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()
                app.onForegroundWindow(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Structural change; used by the oracle to detect UI
                // feedback after an IR candidate (e.g. volume bar).
                app.onContentChanged(event.packageName?.toString())
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event != null) {
            // Observable, never consumed: the remote/focus owner still
            // receives the event. This is what powers the key-observation
            // oracle without breaking normal TV use.
            app.onObservedKey(event)
        }
        // Not consumed → no hijack; honest by design.
        return false
    }

    /** API 33+: TV-style global actions (power, home, dpad, volume). */
    fun performGlobalTvAction(action: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return performGlobalAction(action)
    }

    /** Honest HOME for the leanback launcher (API 24+). */
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /** DPAD via global actions is high-risk; keep it behind an explicit flag. */
    fun dispatchDpad(direction: Direction): Boolean {
        if (!app.dpadGlobalEnabled) return false
        val action = when (direction) {
            Direction.Up -> GLOBAL_ACTION_DPAD_UP
            Direction.Down -> GLOBAL_ACTION_DPAD_DOWN
            Direction.Left -> GLOBAL_ACTION_DPAD_LEFT
            Direction.Right -> GLOBAL_ACTION_DPAD_RIGHT
        }
        return performGlobalTvAction(action)
    }
}