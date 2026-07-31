package com.elysium.nexus.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.elysium.nexus.core.engine.CanonicalInputEngine
import com.elysium.nexus.core.engine.EngineState
import com.elysium.nexus.core.filter.StickConfig
import com.elysium.nexus.core.latency.LatencyTracker
import com.elysium.nexus.core.model.UniversalControllerState
import com.elysium.nexus.core.profile.Profile
import com.elysium.nexus.databases.compatibility.CompatibilityDatabase
import com.elysium.nexus.databases.compatibility.RoomCompatibilityRepository
import com.elysium.nexus.databases.profile.InMemoryProfileRepository
import com.elysium.nexus.databases.profile.ProfileRepository
import com.elysium.nexus.input.TouchSurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

/**
 * The first `Activity`. The first end-to-end milestone.
 *
 * `MASTER_ORDER.md` §45 calls for the first deliverable to be a
 * small, measurable, *deterministic* slice: an APK on Honor
 * Magic V2 that emits generic input and neutralizes on abrupt
 * disconnect. Phase 0.7 is that slice, *minus* the transport
 * (which is Phase 2+). The activity:
 *
 *  1. Creates the [CanonicalInputEngine].
 *  2. Drives the engine through the §32 state machine from
 *     `Idle` to `Active`, simulating the transport layer
 *     for now.
 *  3. Creates a [TouchSurfaceView] that owns the touch
 *     pipeline and wires its callback to
 *     `engine.submitTouchPoint`.
 *  4. Sets the view as the activity's content.
 *  5. Subscribes to the engine's [CanonicalInputEngine.state]
 *     and logs every emission to logcat.
 *  6. On `onDestroy`, drives the engine to `Disconnected`,
 *     calls `engine.neutralize()` for the §38 abrupt path,
 *     and cancels the activity's scope.
 *
 * The activity does **not** know about Bluetooth, USB, or the
 * transport. It is a self-contained session: when the
 * activity is up, the engine is `Active`; when the activity
 * is down, the engine is `Disconnected` and the canonical
 * state is neutral. The transport layer (Phase 2+) will
 * replace the activity's "drive the state machine" with a
 * real transport.
 *
 * ## Why `ComponentActivity`
 *
 * `ComponentActivity` is the modern Android base class
 * (recommended over the deprecated `AppCompatActivity` for
 * activities that do not need `AppCompat`'s back-compat
 * shims). The brand theme is `Theme.Material.Light.NoActionBar`,
 * which is a platform theme, so the agent-memory rule "if
 * the host is `ComponentActivity` themed `Theme.Material.*`
 * use platform `android.app.AlertDialog.Builder`" applies
 * the day we add a dialog.
 *
 * ## Why a custom `CoroutineScope` and not `lifecycleScope`
 *
 * The activity's scope is a `SupervisorJob() +
 * Dispatchers.Main.immediate`. We do not pull in
 * `androidx.lifecycle:lifecycle-runtime-ktx` for 0.7
 * because we do not need its extra machinery yet. When
 * the engine becomes a Hilt `@Singleton` in Phase 1+, the
 * engine's scope is owned by the Hilt graph, and the
 * activity's `viewModelScope` / `lifecycleScope` will be
 * the right place for activity-level work.
 */
class MainActivity : ComponentActivity() {

    private val tag = "ElysiumNexus"
    private var engine: CanonicalInputEngine? = null
    private var latencyTracker: LatencyTracker? = null
    private var activityScope: CoroutineScope? = null
    private var driverJob: Job? = null
    private var latencyJob: Job? = null
    private var touch: TouchSurfaceView? = null
    private var profileRepository: ProfileRepository? = null
    private val profileFlow: MutableStateFlow<Profile?> = MutableStateFlow(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(tag, "MainActivity.onCreate — Phase 1.0 first-Compose milestone")

        // 1. Create the engine. The engine's own scope is
        //    separate from the activity's scope: the engine
        //    has no internal coroutines in 0.7 (its `scope`
        //    parameter is reserved for future engine-internal
        //    jobs), so we use a minimal scope here.
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val latencyTracker = LatencyTracker()
        val engine = CanonicalInputEngine(
            leftStickConfig = StickConfig(),
            rightStickConfig = StickConfig(),
            scope = engineScope,
            latencyTracker = latencyTracker
        )
        this.engine = engine
        this.latencyTracker = latencyTracker

        // 2. Drive the engine through the §32 state machine.
        //    Phase 2+ replaces this with a real transport;
        //    for 0.7 the activity is the transport.
        driveEngineToActive(engine)

        // 3. Create the touch surface. The view is the
        //    Android-specific shell around the dispatcher
        //    (Phase 0.5). Its callback is wired directly to
        //    the engine's `submitTouchPoint`.
        val touch = TouchSurfaceView(this).apply {
            // Use the brand `brand_ink` for the surface
            // background. We avoid XML resources here
            // because the view is constructed programmatically
            // for 0.7; the resource-based theming lands in
            // Phase 1+ with the editor.
            setBackgroundColor(Color.parseColor("#0F0F12"))
            onTouchPointChange = { id, point, t0Ns ->
                engine.submitTouchPoint(id, point, t0Ns)
            }
        }
        // Phase 1.0: the activity's content is a
        // `FrameLayout` that hosts the Compose view
        // (the `MainScreen`) at the bottom and the touch
        // surface on top. The touch surface is the LAST
        // child added; Android dispatches touch to the
        // last child first, so the touch view receives
        // every MotionEvent. The Compose view is below
        // the touch view; its background is transparent
        // except where widgets draw, so the touch
        // surface is visible. Phase 1.1+ replaces this
        // with a single Compose surface that hosts the
        // touch surface via `AndroidView`.
        val root = FrameLayout(this).apply {
            val matchParent = FrameLayout.LayoutParams.MATCH_PARENT
            // Compose view first (added first, drawn first,
            // touched last).
            val composeView = ComposeView(this@MainActivity)
            addView(
                composeView,
                FrameLayout.LayoutParams(matchParent, matchParent)
            )
            // Touch surface second (added second, drawn
            // second, touched first).
            addView(
                touch,
                FrameLayout.LayoutParams(matchParent, matchParent)
            )
        }
        setContentView(root)

        // Phase 1.1: the editor observes the profile via
        // `profileFlow.collectAsState()`. The Compose
        // view is rebuilt every time the user drags a
        // control. The initial profile is the default
        // (loaded from the in-memory repo in step 7
        // above); a future phase swaps the in-memory
        // impl for the Room impl and reads the persisted
        // profile on first launch.
        (root.getChildAt(0) as? ComposeView)?.setContent {
            val profile by profileFlow.collectAsState()
            val scope = activityScope
            val repo = profileRepository
            profile?.let { current ->
                MainScreen(
                    engine = engine,
                    profile = current,
                    onProfileUpdated = { updated ->
                        scope?.launch {
                            repo?.upsert(updated)
                            profileFlow.value = updated
                        }
                    },
                    onNeutralize = { engine.neutralize() }
                )
            }
        }

        // 4. Create the activity's scope and observe the
        //    engine's state. Every emission is logged to
        //    logcat with the latency from the previous
        //    emission, which is the cheapest first cut of
        //    the §30 latency budget. The real measurement
        //    harness (T0..T8 instrumentation) lands in 0.8.
        val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        this.activityScope = activityScope
        driverJob = engine.state
            .onEach { state -> logState(state) }
            .launchIn(activityScope)

        // 5. The §30 latency budget reporter. Every second
        //    we log the current p50 / p95 of the touch
        //    processing path. The full T0..T8 harness
        //    (transport + receiver) lands in Phase 2+ /
        //    Phase 4.
        driverJob = activityScope.launch {
            while (true) {
                delay(1000L)
                val snapshot = latencyTracker.snapshot()
                if (snapshot.count > 0) {
                    Log.i(
                        tag,
                        "latency[count=${snapshot.count}]: " +
                            "p50=${snapshot.p50 / 1_000_000f}ms, " +
                            "p95=${snapshot.p95 / 1_000_000f}ms, " +
                            "p99=${snapshot.p99 / 1_000_000f}ms, " +
                            "max=${snapshot.max / 1_000_000f}ms"
                    )
                }
            }
        }

        // 7. Phase 1.1: the profile repository. For 1.1
        //    the implementation is in-memory; Phase 1.2
        //    swaps in the Room-backed implementation.
        //    The default profile is loaded on first
        //    launch and exposed as a StateFlow so the
        //    Compose UI can observe it. When the user
        //    drags a control in the editor, the activity
        //    updates the repository and pushes the new
        //    value into the flow; the editor recomposes.
        val profileRepo: ProfileRepository = InMemoryProfileRepository()
        this.profileRepository = profileRepo
        runBlocking {
            if (profileRepo.count() == 0) {
                profileRepo.upsert(
                    Profile.defaultProfile(now = System.currentTimeMillis())
                )
            }
            profileFlow.value = profileRepo.firstOrNull()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "MainActivity.onDestroy — §38 disconnect path")
        val engine = this.engine ?: return
        // Per §38: the engine neutralizes on every state-
        // machine transition out of `Active`. We drive the
        // state machine through Suspended → Reconnecting →
        // Disconnected, then call `neutralize()` for the
        // abrupt path. The host never sees a non-neutral
        // state during teardown.
        runCatching { engine.transitionTo(EngineState.Suspended) }
        runCatching { engine.transitionTo(EngineState.Reconnecting) }
        runCatching { engine.transitionTo(EngineState.Disconnected) }
        engine.neutralize()
        // Cancel the activity's scope. The engine's own
        // scope is cancelled too — it has no internal jobs
        // today, but the cancel is the safe default.
        driverJob?.cancel()
        activityScope?.cancel()
        this.engine = null
        this.latencyTracker = null
        this.activityScope = null
        this.driverJob = null
    }

    /**
     * Drive the engine from `Idle` to `Active` through the
     * §32 legal forward path. In Phase 2+ this is the
     * transport's job; for 0.7 the activity is the
     * transport.
     */
    private fun driveEngineToActive(engine: CanonicalInputEngine) {
        engine.transitionTo(EngineState.Discovering)
        engine.transitionTo(EngineState.Pairing)
        engine.transitionTo(EngineState.Authenticating)
        engine.transitionTo(EngineState.Negotiating)
        engine.transitionTo(EngineState.Connected)
        engine.transitionTo(EngineState.Active)
    }

    /**
     * Log a state emission to logcat with the wall-clock
     * latency from the previous emission. The latency is
     * an early indicator of pipeline health; the formal
     * §30 latency budget lands in 0.8.
     */
    private var lastLogMs: Long = System.currentTimeMillis()

    private fun logState(state: UniversalControllerState) {
        val now = System.currentTimeMillis()
        val deltaMs = now - lastLogMs
        lastLogMs = now
        Log.d(
            tag,
            "state[seq=${state.sequence}, ts=${state.timestampNs}, " +
                "Δt=${deltaMs}ms]: " +
                "buttons=${state.buttons.size()}, " +
                "dpad=${state.dpad}, " +
                "L=(${state.leftStick.x}, ${state.leftStick.y}), " +
                "R=(${state.rightStick.x}, ${state.rightStick.y}), " +
                "LT=${state.leftTrigger.value}, " +
                "RT=${state.rightTrigger.value}, " +
                "touches=${state.touches.size()}, " +
                "motion=${state.motion != null}"
        )
    }

    // Suppress an unused-warning: `measureTimeMillis` is
    // imported in anticipation of the §30 latency harness
    // in 0.8, where the real per-event timing lands. For
    // 0.7 the delta is computed from `System.currentTimeMillis()`
    // because the engine's emissions are paced by user
    // input, not by a hot loop.
    @Suppress("unused")
    private fun touchProbe(): Long = measureTimeMillis { /* reserved for 0.8 */ }
}
