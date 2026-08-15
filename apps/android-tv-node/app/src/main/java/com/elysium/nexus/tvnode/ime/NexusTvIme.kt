package com.elysium.nexus.tvnode.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.elysium.nexus.tvnode.application.TvNodeApp

/**
 * NexusTvIme — the TV-facing IME (TV-FABRIC.7).
 *
 * The phone types into the TV after NORMAL, EXPLICIT user grant of this
 * IME in system input settings. All input flows over the secure pairing
 * channel (never over plain text), and the IME only ever COMMITS text to
 * the focused InputConnection — it cannot hook keys without the IME
 * being the active input target.
 */
class NexusTvIme : InputMethodService() {

    private val app: TvNodeApp get() = application as TvNodeApp

    override fun onCreateInputView(): View {
        // Minimal stateless view: this IME is a transport, not a keyboard.
        return View(this)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // Secure fields: mark so the TV avoids echoing the content.
        attribute?.apply {
            if (imeOptions and (EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN) == 0) {
                imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            }
        }
        app.onImeReady(this)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        app.onImeFinalized()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // We consume nothing here; hard keys stay with the system remote.
        return super.onKeyDown(keyCode, event)
    }

    /** Commits text to the focused field. */
    fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        ic.finishComposingText()
    }

    /** Deletes one char (backspace semantics). */
    fun deleteBackward(steps: Int = 1) {
        val ic = currentInputConnection ?: return
        repeat(steps) { ic.deleteSurroundingText(1, 0) }
    }

    /** Sends ENTER to the focus target. */
    fun sendEnter() {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}