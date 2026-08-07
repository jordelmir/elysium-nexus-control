// Elysium Nexus — :app module
//
// Phase 0.1: bare-minimum Gradle/AGP/Kotlin wiring. No production code
// yet beyond a single placeholder class. The goal of 0.1 is "the build
// is green from end to end", not "the app does something".
//
// Production code lands in 0.2+ (canonical engine, touch pipeline, HID
// transport).

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.cyclonedx)
}

android {
    namespace = "com.elysium.nexus"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.elysium.nexus.controller"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 5
        versionName = "0.5.0-engineering-preview"

        // No instrumentation runner until we add AndroidX Test. We add
        // it in 0.5 alongside the first Context-dependent test.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 minification + resource shrinking for release builds.
            // Signing uses the debug keystore until a proper release
            // identity is provisioned. The proguard-rules.pro file
            // contains keep rules for Room, Coroutines, Compose, and
            // enum serialization.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // Phase 1.0: Compose is the first UI surface. The
        // Compose compiler is wired by the Kotlin
        // Compose plugin (declared in libs.versions.toml).
        compose = true
    }

    kotlin {
        jvmToolchain(17)
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Lint is enabled; we add a "no warnings" rule once we have real
    // code. For 0.1 there is nothing to lint, but the configuration is
    // here so future iterations inherit it.
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
    }
}

// P0-12: Room schema export for migration testing
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // No third-party deps in 0.1. See ADR-0001.
    //
    // Phase 0.4 — earned `kotlinx-coroutines` because the engine
    // is the first component that needs StateFlow and a
    // CoroutineScope. The reason is documented in
    // docs/changelogs/PHASE_0_4_ENGINE.md.
    //
    // Phase 0.7 — earned `androidx.activity:activity-ktx`
    // because the first Activity (MainActivity) extends
    // ComponentActivity, the modern base class for activities
    // that do not need AppCompat shims. We use the
    // -ktx flavour so we have the Kotlin extensions
    // available when the activity evolves to use
    // viewModels / by viewModels in 1.x. The reason is
    // documented in docs/changelogs/PHASE_0_7_FIRST_ACTIVITY.md.
    //
    // Phase 1.0 — earned:
    //   - `androidx.activity:activity-compose` because the
    //     activity evolves to use `setContent { ... }` for
    //     the first Compose screen.
    //   - `androidx.compose:compose-bom` (BOM) + `ui` +
    //     `material3` + `ui-tooling-preview` because the
    //     first Compose UI screen (MainScreen) lands in
    //     1.0. The BOM pins the versions of every Compose
    //     artefact transitively, so we use it and let the
    //     individual libraries inherit their versions.
    //   - `androidx.room:room-runtime` + `room-ktx` because
    //     the compatibility database (Phase 0.9) is now
    //     persisted. We use the KSP processor instead of
    //     kapt because KSP is the modern recommendation
    //     and ships clean with Kotlin 2.2.21.
    //   - `androidx.compose.ui:ui-test-junit4` +
    //     `ui-test-manifest` for the Compose smoke test.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    // Phase 1.5: Jetpack WindowManager for the §16
    // foldable posture detection. The
    // `WindowInfoTracker` + `FoldingFeature` APIs
    // are the only platform-agnostic abstraction
    // for foldable hinges.
    implementation(libs.androidx.window)
    // Phase 1.17: §15 profile share intent. The
    // `FileProvider` lives in `androidx.core`; we
    // depend on the `-ktx` flavour for the Kotlin
    // extensions. The provider is declared in
    // AndroidManifest.xml with the authority
    // `${applicationId}.fileprovider`.
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Phase ULT.2: Material Icons Extended for
    // the visual polish — every chip / button
    // gets a proper icon, not just text.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Phase 1.4: the real `org.json` implementation.
    // The Android stub returns default values
    // (`null` / `0` / `""`) under
    // `unitTests.isReturnDefaultValues = true`, so
    // the JVM tests cannot exercise the real
    // serialisation. The real reference
    // implementation is API-compatible.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Phase 0.9 — the hid-descriptor-validator tool. A
// JavaExec task that runs the JVM-only validator
// without going through the Android install path.
// The validator asserts the descriptor is structurally
// well-formed; CI runs it on every build.
tasks.register<JavaExec>("runValidator") {
    group = "elysium"
    description = "Run the BASIC_GAMEPAD_V1 HID descriptor validator"
    mainClass.set("com.elysium.nexus.core.hid.HidDescriptorValidatorKt")
    dependsOn("compileDebugKotlin")
    classpath = files(tasks.named("compileDebugKotlin").get().outputs.files) +
        files(tasks.named("compileDebugJavaWithJavac").get().outputs.files) +
        configurations.getByName("debugRuntimeClasspath")
}
