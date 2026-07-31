package com.elysium.nexus.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.elysium.nexus.R
import com.elysium.nexus.core.engine.CanonicalInputEngine
import com.elysium.nexus.core.engine.EngineState
import com.elysium.nexus.core.engine.TransportBinding
import com.elysium.nexus.core.filter.StickConfig
import com.elysium.nexus.core.haptics.AndroidHaptics
import com.elysium.nexus.core.haptics.Haptics
import com.elysium.nexus.core.haptics.SettingsAwareHaptics
import com.elysium.nexus.core.latency.LatencyTracker
import com.elysium.nexus.core.model.UniversalControllerState
import com.elysium.nexus.core.motion.AndroidMotionSensorSource
import com.elysium.nexus.core.motion.MotionSensorSource
import com.elysium.nexus.core.motion.NullMotionSensorSource
import com.elysium.nexus.core.posture.AndroidPostureObserver
import com.elysium.nexus.core.posture.NullPostureObserver
import com.elysium.nexus.core.posture.Posture
import com.elysium.nexus.core.posture.PostureObserver
import com.elysium.nexus.core.profile.AndroidProfileShareLauncher
import com.elysium.nexus.core.profile.Profile
import com.elysium.nexus.core.profile.ProfileShareBuilder
import com.elysium.nexus.core.settings.AndroidAppSettingsStore
import com.elysium.nexus.core.settings.AppSettings
import com.elysium.nexus.core.settings.AppSettingsStore
import com.elysium.nexus.core.transport.BluetoothHidTransport
import com.elysium.nexus.core.transport.ControllerTransport
import com.elysium.nexus.core.transport.LocalEchoTransport
import com.elysium.nexus.databases.profile.ProfileDatabase
import com.elysium.nexus.databases.profile.ProfileRepository
import com.elysium.nexus.databases.profile.RoomProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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
 *  3. Sets the activity's content to the Compose
 *     `MainScreen` via `setContent`. The
 *     [com.elysium.nexus.ui.editor.TouchSurfaceViewHost]
 *     is inside the Compose tree (Phase 1.3's
 *     [com.elysium.nexus.ui.editor.TouchSurfaceViewHost]
 *     pattern; the Phase 1.1+ `FrameLayout` was removed
 *     in Phase 1.3 when the touch surface moved into
 *     Compose).
 *  4. Subscribes to the engine's [CanonicalInputEngine.state]
 *     and logs every emission to logcat.
 *  5. On `onDestroy`, drives the engine to `Disconnected`,
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
    private var motionJob: Job? = null
    private var motionSource: MotionSensorSource? = null
    private var postureSource: PostureObserver? = null
    private var postureJob: Job? = null
    private var profileRepository: ProfileRepository? = null
    private var transportBinding: TransportBinding? = null
    private var transportJob: Job? = null
    private var shareLauncher: AndroidProfileShareLauncher? = null
    private var settingsStore: AppSettingsStore? = null
    private var haptics: Haptics? = null
    private val profileFlow: MutableStateFlow<Profile?> = MutableStateFlow(null)
    private val allProfilesFlow: MutableStateFlow<List<Profile>> = MutableStateFlow(emptyList())
    private val postureFlow: MutableStateFlow<Posture> = MutableStateFlow(Posture.UNKNOWN)
    private val transportFlow: MutableStateFlow<ControllerTransport?> = MutableStateFlow(null)
    private val settingsFlow: MutableStateFlow<AppSettings> = MutableStateFlow(AppSettings())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(tag, "MainActivity.onCreate — Phase 1.3 editor + AndroidView arbitration")

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

        // 3. Phase 1.2: the profile repository. The
        //    Room-backed implementation persists the
        //    profile across process death. The default
        //    profile is loaded on first launch
        //    (when the database is empty) and exposed
        //    as a StateFlow so the Compose UI can
        //    observe it. When the user drags a control
        //    in the editor, the activity updates the
        //    repository and pushes the new value into
        //    the flow; the editor recomposes.
        //
        // Phase 1.3: also expose the *list* of every
        // profile in the DB so the editor's
        // [com.elysium.nexus.ui.editor.ProfileSelector]
        // can render the user's library.
        val profileRepo: ProfileRepository = RoomProfileRepository(
            ProfileDatabase.getInstance(this).profileDao()
        )
        this.profileRepository = profileRepo
        runBlocking {
            if (profileRepo.count() == 0) {
                profileRepo.upsert(
                    Profile.defaultProfile(now = System.currentTimeMillis())
                )
            }
            profileFlow.value = profileRepo.firstOrNull()
            allProfilesFlow.value = profileRepo.all()
        }

        // 4. Phase 1.18 — the §15 settings store. The
        //    store is the source of truth for the
        //    user-tunable knobs (stick sensitivity,
        //    axis inversion, haptics on/off). The
        //    store is backed by SharedPreferences;
        //    the in-memory [MutableStateFlow] is the
        //    Compose-friendly view. The haptics is
        //    wrapped in [SettingsAwareHaptics] so a
        //    settings change immediately gates the
        //    next [HapticEvent].
        val settingsStore: AppSettingsStore = AndroidAppSettingsStore(this)
        this.settingsStore = settingsStore
        settingsFlow.value = settingsStore.current
        val haptics: Haptics = SettingsAwareHaptics(
            inner = AndroidHaptics(this),
            settingsFlow = settingsFlow
        )
        this.haptics = haptics

        // 5. Phase 1.16 — the default transport must
        //    be in scope *before* the setContent block
        //    so the composable lambda can reference
        //    it (the §17 multiplexer). We create the
        //    [LocalEchoTransport], start and connect
        //    it (the test-friendly transport, 0 ms
        //    latency), and stash it in the activity
        //    field for the onDestroy §38 path.
        val defaultTransport = LocalEchoTransport()
        runBlocking {
            defaultTransport.start()
            defaultTransport.connect()
        }
        transportFlow.value = defaultTransport

        // 5. Phase 1.3: set the activity's content
        //    directly to the Compose `MainScreen`. The
        //    [com.elysium.nexus.ui.editor.TouchSurfaceViewHost]
        //    is hosted *inside* the Compose tree via
        //    `AndroidView`. The activity no longer
        //    needs a `FrameLayout`; the Compose tree
        //    is the only content view. This is the
        //    Phase 1.3 fix for Bug #18: the touch
        //    surface is no longer dead.
        setContent {
            val profile by profileFlow.collectAsState()
            val allProfiles by allProfilesFlow.collectAsState()
            val posture by postureFlow.collectAsState()
            val currentTransport by transportFlow.collectAsState()
            val settings by settingsFlow.collectAsState()
            val scope = activityScope
            val repo = profileRepository
            profile?.let { current ->
                PostureAwareMainScreen(
                    engine = engine,
                    profile = current,
                    allProfiles = allProfiles,
                    transports = listOfNotNull(currentTransport),
                    currentTransport = currentTransport ?: defaultTransport,
                    onTransportSelected = { t ->
                        transportBinding?.setTransport(t)
                        transportFlow.value = t
                    },
                    posture = posture,
                    onProfileSelected = { id ->
                        scope?.launch {
                            val next = repo?.byId(id) ?: return@launch
                            profileFlow.value = next
                        }
                    },
                    onProfileUpdated = { updated ->
                        scope?.launch {
                            repo?.upsert(updated)
                            profileFlow.value = updated
                            allProfilesFlow.value = repo?.all() ?: emptyList()
                        }
                    },
                    onNewProfile = {
                        scope?.launch {
                            val newId = repo?.nextId() ?: return@launch
                            val now = System.currentTimeMillis()
                            val newProfile = Profile(
                                id = newId,
                                name = "Profile $newId",
                                author = "user",
                                controls = emptyList(),
                                createdAt = now,
                                updatedAt = now
                            )
                            repo.upsert(newProfile)
                            profileFlow.value = newProfile
                            allProfilesFlow.value = repo.all()
                        }
                    },
                    onDeleteProfile = {
                        scope?.launch {
                            val current = profileFlow.value ?: return@launch
                            val r = repo ?: return@launch
                            // Phase 1.5: refuse to delete the
                            // last profile. The "default"
                            // profile is the user's safety
                            // net; deleting it would leave
                            // the activity with no profile
                            // to render. The full rule
                            // (configurable, with a
                            // confirmation dialog) lands in
                            // Phase 1.6+.
                            if (r.count() <= 1) {
                                Log.w(tag, "Refusing to delete the last profile (id=${current.id}).")
                                return@launch
                            }
                            r.delete(current.id)
                            val next = r.firstOrNull()
                            profileFlow.value = next
                            allProfilesFlow.value = r.all()
                        }
                    },
                    onShareProfile = {
                        // Phase 1.17: the §15 share
                        // intent. We build a
                        // [com.elysium.nexus.core.profile.ProfileShare]
                        // artifact from the current
                        // profile and hand it to the
                        // [AndroidProfileShareLauncher].
                        // The launcher writes the JSON
                        // to the cache and returns a
                        // chooser Intent; we surface it
                        // via `startActivity`. Per §38
                        // we never crash the activity
                        // here: a null intent (I/O
                        // failure, missing FileProvider)
                        // is logged and dropped.
                        val current = profileFlow.value ?: return@PostureAwareMainScreen
                        val launcher = shareLauncher ?: return@PostureAwareMainScreen
                        val share = ProfileShareBuilder.build(current)
                        val intent = launcher.launch(
                            share = share,
                            chooserTitle = getString(R.string.share_profile_chooser_title)
                        )
                        if (intent != null) {
                            startActivity(intent)
                        } else {
                            Log.w(tag, "Share intent was null; sharing dropped.")
                        }
                    },
                    settings = settings,
                    onSettingsChange = { updated ->
                        settingsStore?.update(updated)
                        settingsFlow.value = updated
                        // Phase 1.18 also: trigger a
                        // haptic on settings change so
                        // the user knows the value was
                        // committed. The haptics
                        // respects the §15 toggle
                        // (hapticsEnabled); a change
                        // with hapticsEnabled=false
                        // is a no-op except for the
                        // value persistence.
                        haptics?.fire(com.elysium.nexus.core.haptics.HapticEvent.ProfileChanged)
                    },
                    onNeutralize = { engine.neutralize() }
                )
            }
        }

        // 5. Create the activity's scope and observe the
        //    engine's state. Every emission is logged to
        //    logcat with the latency from the previous
        //    emission, which is the cheapest first cut of
        //    the §30 latency budget. The real measurement
        //    harness (T0..T8 instrumentation) lands in 0.8.
        val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        this.activityScope = activityScope

        // Phase 1.13 — the §17 transport binding.
        //    The activity wires a default
        //    [LocalEchoTransport] (the test-friendly
        //    transport that records every frame). The
        //    engine's `state` flow is forwarded to the
        //    transport via [TransportBinding]. The
        //    activity's [TransportSelector] lets the
        //    user pick a different transport at runtime
        //    (Phase 1.14). The transport is the
        //    destination of the engine's `submit*`
        //    calls; the [LocalEchoTransport] is the
        //    test surface for the engine→transport
        //    pipeline.
        //
        // Note: `defaultTransport` is declared in step
        // 4 (above `setContent`) so the Compose
        // lambda can reference it. We only build the
        // [TransportBinding] here, after the activity's
        // own scope exists.
        val transportBinding = TransportBinding(defaultTransport)
        this.transportBinding = transportBinding
        // Forward every engine state to the
        // transport. The [LocalEchoTransport] just
        // records; the real transports send over
        // BT / USB / Wi-Fi.
        transportJob = engine.state
            .onEach { state -> transportBinding.forwardRealtime(state) }
            .launchIn(activityScope)

        // Phase 1.17: the §15 profile share
        // launcher. The launcher is a thin Android
        // adapter around
        // [com.elysium.nexus.core.profile.ProfileShareBuilder]
        // — it writes the JSON to the cache and
        // returns a chooser Intent. The activity
        // owns the launcher for its lifetime; the
        // launcher has no per-call state of its
        // own, so `onDestroy` only needs to null
        // the field.
        shareLauncher = AndroidProfileShareLauncher(this)

        driverJob = engine.state
            .onEach { state -> logState(state) }
            .launchIn(activityScope)

        // 6. The §30 latency budget reporter. Every second
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

        // 7. Phase 1.4 — the §14 motion / IMU source.
        //    The activity owns the source for the
        //    activity's lifetime. The source's
        //    `samples()` flow is collected on the
        //    activity's scope; each sample is
        //    forwarded to the engine via
        //    `engine.submitMotion`. On the emulator
        //    (no real IMU), the source returns an
        //    empty flow and no samples are forwarded
        //    — the engine's `motion` field stays
        //    `null` (the canonical neutral for motion).
        val motionSource: MotionSensorSource = try {
            AndroidMotionSensorSource(this)
        } catch (e: Throwable) {
            // The source may fail on devices without
            // a SensorManager (rare; the emulator
            // does have one). Fall back to the no-op
            // source so the activity still launches.
            Log.w(tag, "AndroidMotionSensorSource failed; using NullMotionSensorSource.", e)
            NullMotionSensorSource()
        }
        this.motionSource = motionSource
        motionJob = activityScope.launch {
            try {
                motionSource.samples().collect { sample ->
                    engine.submitMotion(sample)
                }
            } catch (e: Throwable) {
                Log.w(tag, "Motion sample collection failed; motion is dormant.", e)
            }
        }

        // 8. Phase 1.5 / 1.8 — the §16 foldable
        //    posture observer. The activity owns
        //    the source for the activity's lifetime.
        //    The source's `postures()` flow is
        //    collected on the activity's scope; each
        //    new posture updates the `postureFlow`,
        //    which the `PostureAwareMainScreen`
        //    observes. On a non-foldable device, the
        //    observer returns `UNKNOWN` for the
        //    lifetime of the activity.
        val postureSource: PostureObserver = try {
            AndroidPostureObserver(this, activityScope)
        } catch (e: Throwable) {
            // The source may fail on devices without
            // a `WindowInfoTracker` (rare). Fall back
            // to the no-op source.
            Log.w(tag, "AndroidPostureObserver failed; using NullPostureObserver.", e)
            NullPostureObserver()
        }
        this.postureSource = postureSource
        postureJob = activityScope.launch {
            try {
                postureSource.postures().collect { posture ->
                    postureFlow.value = posture
                }
            } catch (e: Throwable) {
                Log.w(tag, "Posture observation failed; posture is dormant.", e)
            }
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
        // §38: forward the neutralization to the
        // transport as a `releaseAll` event.
        // The [LocalEchoTransport] records the
        // event; the real transports emit a
        // "release all" report to the host.
        runBlocking { transportBinding?.transport?.value?.releaseAll() }
        // Cancel the activity's scope. The engine's own
        // scope is cancelled too — it has no internal jobs
        // today, but the cancel is the safe default.
        driverJob?.cancel()
        motionJob?.cancel()
        motionSource?.close()
        postureJob?.cancel()
        postureSource?.close()
        transportJob?.cancel()
        runBlocking { transportBinding?.transport?.value?.stop() }
        activityScope?.cancel()
        this.engine = null
        this.latencyTracker = null
        this.activityScope = null
        this.driverJob = null
        this.motionJob = null
        this.motionSource = null
        this.postureJob = null
        this.postureSource = null
        this.transportBinding = null
        this.transportJob = null
        this.shareLauncher = null
        this.settingsStore = null
        this.haptics = null
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
