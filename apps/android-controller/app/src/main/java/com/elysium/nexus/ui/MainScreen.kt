package com.elysium.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.R
import com.elysium.nexus.core.engine.CanonicalInputEngine
import com.elysium.nexus.core.model.UniversalControllerState
import com.elysium.nexus.core.profile.NormalizedRect
import com.elysium.nexus.core.profile.Profile
import com.elysium.nexus.core.profile.ProfileImportResult
import com.elysium.nexus.core.settings.AppSettings
import com.elysium.nexus.core.transport.ControllerTransport
import com.elysium.nexus.ui.editor.AlignmentAction
import com.elysium.nexus.ui.editor.ControlKind
import com.elysium.nexus.ui.editor.EditorActions
import com.elysium.nexus.ui.editor.EditorCanvas
import com.elysium.nexus.ui.editor.EditorToolbar
import com.elysium.nexus.ui.editor.ProfileImportDialog
import com.elysium.nexus.ui.editor.ProfileSelector
import com.elysium.nexus.ui.editor.TouchSurfaceViewHost
import com.elysium.nexus.ui.editor.TransportSelector
import com.elysium.nexus.ui.settings.SettingsDialog

/**
 * The first Compose UI screen of the project.
 *
 * `MASTER_ORDER.md` §15 (controls editor) and §11 (touch
 * surface) describe the production UI. The screen has
 * evolved across phases:
 *
 *  - Phase 1.0: a `Text`-only projection of the engine
 *    state + a `Button` that calls
 *    [CanonicalInputEngine.neutralize].
 *  - Phase 1.1: the `EditorCanvas` is layered on top of
 *    the engine projection. The user can drag the
 *    profile's controls around; the changes are
 *    persisted via the `ProfileRepository`.
 *  - Phase 1.2: the [EditorToolbar] is the top strip;
 *    the user can "Add button" / "Add stick" / "Add
 *    trigger" and "Save" / "Reset". The currently
 *    selected control is highlighted in the canvas.
 *  - Phase 1.3: the [TouchSurfaceViewHost] is *inside*
 *    the Compose tree via `AndroidView` (Bug #18 fix).
 *    The editor's `pointerInput` consumes touches
 *    inside control hitBoxes; everything else falls
 *    through to the touch surface. The
 *    [ProfileSelector] lists every profile in the DB.
 *    Long-press a control to delete it.
 *
 * Phase 1.4+ adds: opacity slider, the alignment /
 * distribution helpers, the import / export, the
 * signature, the §11 transport multiplexer.
 */
@Composable
fun MainScreen(
    engine: CanonicalInputEngine,
    profile: Profile,
    allProfiles: List<Profile>,
    transports: List<ControllerTransport>,
    currentTransport: ControllerTransport,
    onTransportSelected: (ControllerTransport) -> Unit,
    onProfileSelected: (Int) -> Unit,
    onProfileUpdated: (Profile) -> Unit,
    onNewProfile: () -> Unit = { },
    onDeleteProfile: () -> Unit = { },
    onShareProfile: () -> Unit = { },
    onImportProfile: (String) -> ProfileImportResult = { ProfileImportResult.Failure("Not wired") },
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit = { },
    onNeutralize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by engine.state.collectAsState()
    MainScreenContent(
        engine = engine,
        state = state,
        profile = profile,
        allProfiles = allProfiles,
        transports = transports,
        currentTransport = currentTransport,
        onTransportSelected = onTransportSelected,
        onProfileSelected = onProfileSelected,
        onControlMoved = { controlId, newVisualBounds ->
            onProfileUpdated(
                EditorActions.moveControl(
                    profile = profile,
                    controlId = controlId,
                    newVisualBounds = newVisualBounds,
                    now = System.currentTimeMillis()
                )
            )
        },
        onControlScaled = { controlId, newW, newH ->
            onProfileUpdated(
                EditorActions.resizeControl(
                    profile = profile,
                    controlId = controlId,
                    newWidth = newW,
                    newHeight = newH,
                    now = System.currentTimeMillis()
                )
            )
        },
        onControlRotated = { controlId, newRotation ->
            onProfileUpdated(
                EditorActions.rotateControl(
                    profile = profile,
                    controlId = controlId,
                    newRotation = newRotation,
                    now = System.currentTimeMillis()
                )
            )
        },
        onOpacityChange = { controlId, newOpacity ->
            onProfileUpdated(
                EditorActions.setOpacity(
                    profile = profile,
                    controlId = controlId,
                    newOpacity = newOpacity,
                    now = System.currentTimeMillis()
                )
            )
        },
        onControlAdded = { kind ->
            onProfileUpdated(
                EditorActions.addControl(
                    profile = profile,
                    kind = kind,
                    now = System.currentTimeMillis()
                )
            )
        },
        onControlDeleted = { controlId ->
            onProfileUpdated(
                EditorActions.removeControl(
                    profile = profile,
                    controlId = controlId,
                    now = System.currentTimeMillis()
                )
            )
        },
        onProfileUpdated = onProfileUpdated,
        onReset = {
            // Phase 1.2 reset: re-issue the profile's
            // own controls in their original positions.
            // The §15 "history" milestone (Phase 1.4+)
            // replaces this with a per-session history.
            onProfileUpdated(profile)
        },
        onNewProfile = onNewProfile,
        onDeleteProfile = onDeleteProfile,
        onShareProfile = onShareProfile,
        onImportProfile = onImportProfile,
        settings = settings,
        onSettingsChange = onSettingsChange,
        onNeutralize = onNeutralize,
        modifier = modifier
    )
}

/**
 * The stateless projection. Splitting this out makes
 * the composable previewable from Android Studio
 * without instantiating an engine.
 */
@Composable
private fun MainScreenContent(
    engine: CanonicalInputEngine,
    state: UniversalControllerState,
    profile: Profile,
    allProfiles: List<Profile>,
    transports: List<ControllerTransport>,
    currentTransport: ControllerTransport,
    onTransportSelected: (ControllerTransport) -> Unit,
    onProfileSelected: (Int) -> Unit,
    onControlMoved: (controlId: Int, newVisualBounds: NormalizedRect) -> Unit,
    onControlScaled: (controlId: Int, newWidth: Float, newHeight: Float) -> Unit,
    onControlRotated: (controlId: Int, newRotation: Float) -> Unit,
    onOpacityChange: (controlId: Int, newOpacity: Float) -> Unit,
    onControlAdded: (ControlKind) -> Unit,
    onControlDeleted: (controlId: Int) -> Unit,
    onProfileUpdated: (Profile) -> Unit = { },
    onReset: () -> Unit,
    onNewProfile: () -> Unit = { },
    onDeleteProfile: () -> Unit = { },
    onShareProfile: () -> Unit = { },
    onImportProfile: (String) -> ProfileImportResult = { ProfileImportResult.Failure("Not wired") },
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit = { },
    onNeutralize: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The selected control's id; null = no selection.
    // The selection is *local* state; the profile is
    // still the source of truth for the controls. The
    // selection is lost on process death (acceptable
    // for the editor's ephemeral state).
    var selectedId by remember { mutableStateOf<Int?>(null) }
    // Phase 1.18: the settings dialog visibility is
    // *local* state. The settings document itself is
    // the [settings] parameter, owned by the caller
    // (the [com.elysium.nexus.core.settings.AppSettingsStore]
    // is the source of truth). The dialog is
    // dismissed by tapping "Close" or any tap outside
    // the dialog's body (the default Compose
    // `onDismissRequest` behaviour).
    var showSettings by remember { mutableStateOf(false) }
    // Phase 1.22: the import dialog visibility is
    // *local* state. The dialog is dismissed by
    // tapping "Close" or any tap outside the
    // dialog's body. The result of the import is
    // reported by the caller via [onImportProfile];
    // a failure stays on the dialog with the
    // reason inline.
    var showImport by remember { mutableStateOf(false) }
    // The selected control's current opacity; null
    // when no control is selected. The toolbar's
    // opacity slider uses this to show the current
    // value; the slider's `onValueChange` calls
    // `onOpacityChange` which mutates the profile.
    val selectedOpacity: Float? = selectedId?.let { id ->
        profile.controls.firstOrNull { it.id == id }?.opacity
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F0F12) // brand_ink
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Phase 1.3: the profile selector. The user
            // can switch between every profile in the
            // database. The selector sits above the
            // toolbar.
            ProfileSelector(
                profiles = allProfiles,
                currentProfileId = profile.id,
                onProfileSelected = {
                    selectedId = null
                    onProfileSelected(it)
                },
                modifier = Modifier.fillMaxWidth()
            )
            // Phase 1.16: the transport selector. The
            // user can pick the active transport at
            // runtime. The selector is above the
            // toolbar; the current transport is
            // highlighted; tapping a chip switches.
            TransportSelector(
                transports = transports,
                currentTransport = currentTransport,
                onTransportSelected = onTransportSelected,
                modifier = Modifier.fillMaxWidth()
            )
            // The editor toolbar: Add / Save / Reset +
            // (when a control is selected) the opacity
            // slider + the alignment / distribution
            // chips.
            EditorToolbar(
                onAdd = onControlAdded,
                onSave = { /* Save is implicit in onProfileUpdated; this is a UX cue */ },
                onReset = {
                    selectedId = null
                    onReset()
                },
                onNewProfile = onNewProfile,
                onDeleteProfile = onDeleteProfile,
                onShare = onShareProfile,
                onImport = { showImport = true },
                onSettings = { showSettings = true },
                onAlign = { action ->
                    val now = System.currentTimeMillis()
                    val sid = selectedId
                    val updated = when (action) {
                        AlignmentAction.AlignLeft ->
                            if (sid != null) EditorActions.alignLeft(profile, sid, now)
                            else profile
                        AlignmentAction.AlignRight ->
                            if (sid != null) EditorActions.alignRight(profile, sid, now)
                            else profile
                        AlignmentAction.AlignTop ->
                            if (sid != null) EditorActions.alignTop(profile, sid, now)
                            else profile
                        AlignmentAction.AlignBottom ->
                            if (sid != null) EditorActions.alignBottom(profile, sid, now)
                            else profile
                        AlignmentAction.DistributeHorizontally ->
                            EditorActions.distributeHorizontally(profile, now)
                        AlignmentAction.DistributeVertically ->
                            EditorActions.distributeVertically(profile, now)
                    }
                    onProfileUpdated(updated)
                },
                onOpacityChange = { newOpacity ->
                    selectedId?.let { id -> onOpacityChange(id, newOpacity) }
                },
                selectedOpacity = selectedOpacity
            )
            Box(modifier = Modifier.fillMaxSize()) {
                // Phase 1.3: the touch surface is
                // hosted *inside* the Compose tree via
                // `AndroidView` (the [TouchSurfaceViewHost]
                // composable). It sits behind the
                // editor and receives every touch that
                // the editor's `pointerInput` does NOT
                // consume. This is the Bug #18 fix: the
                // touch surface is no longer dead.
                TouchSurfaceViewHost(
                    onTouchPointChange = { id, point, t0Ns ->
                        engine.submitTouchPoint(id, point, t0Ns)
                    }
                )
                // The editor canvas: the user's profile
                // rendered as draggable, scalable,
                // rotatable controls. The selected
                // control gets a paper-coloured outline.
                EditorCanvas(
                    profile = profile,
                    onMoved = onControlMoved,
                    onScaled = onControlScaled,
                    onRotated = onControlRotated,
                    onTapped = { id -> selectedId = id },
                    onLongPressed = { id ->
                        selectedId = null
                        onControlDeleted(id)
                    },
                    selectedId = selectedId
                )
                // The diagnostic overlay: small text in
                // the corner showing the engine state.
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.editor_title),
                        color = Color(0xFFF2F2F4),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${stringResource(R.string.editor_diag_seq)}=${state.sequence}, " +
                            "${stringResource(R.string.editor_diag_touches)}=${state.touches.size()}",
                        color = Color(0xFFAAAAAA),
                        fontSize = 10.sp
                    )
                }
                // The §38 Neutralize button: a floating
                // button in the bottom-right corner.
                Button(
                    onClick = onNeutralize,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.editor_neutralize))
                }
            }
        }
        // Phase 1.18: the settings dialog. The
        // dialog is hosted by the screen itself
        // (not by a separate scaffold) so the
        // editor's state (selection, profile,
        // touch surface) is preserved while the
        // dialog is up. The dialog is "live":
        // every change calls `onSettingsChange`,
        // which the caller funnels into the
        // [com.elysium.nexus.core.settings.AppSettingsStore].
        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onDismiss = { showSettings = false }
            )
        }
        // Phase 1.22: the import dialog. The
        // dialog is hosted by the screen
        // itself. The result of the import is
        // returned by [onImportProfile]; on
        // success the caller (the activity)
        // persists the profile and the dialog
        // dismisses; on failure the dialog
        // shows the reason inline and stays
        // open.
        if (showImport) {
            ProfileImportDialog(
                onImport = onImportProfile,
                onDismiss = { showImport = false }
            )
        }
    }
}

/**
 * Android Studio preview. A no-op that shows the
 * layout with a synthetic neutral state and the
 * default profile. Useful for screenshotting the
 * screen layout without an engine.
 */
@Preview
@Composable
private fun MainScreenPreview() {
    val state = UniversalControllerState.neutral()
    val profile = Profile.defaultProfile(now = 0L)
    val previewScope = rememberCoroutineScope()
    MainScreenContent(
        engine = CanonicalInputEngine(
            leftStickConfig = com.elysium.nexus.core.filter.StickConfig(),
            rightStickConfig = com.elysium.nexus.core.filter.StickConfig(),
            scope = previewScope
        ),
        state = state,
        profile = profile,
        allProfiles = listOf(profile),
        transports = listOf(com.elysium.nexus.core.transport.LocalEchoTransport()),
        currentTransport = com.elysium.nexus.core.transport.LocalEchoTransport(),
        onProfileSelected = { },
        onControlMoved = { _, _ -> },
        onControlScaled = { _, _, _ -> },
        onControlRotated = { _, _ -> },
        onOpacityChange = { _, _ -> },
        onControlAdded = { },
        onControlDeleted = { },
        onReset = { },
        onNewProfile = { },
        onDeleteProfile = { },
        onShareProfile = { },
        onImportProfile = { ProfileImportResult.Failure("Preview") },
        settings = com.elysium.nexus.core.settings.AppSettings(),
        onSettingsChange = { },
        onTransportSelected = { },
        onNeutralize = { }
    )
}
