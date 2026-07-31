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
}

android {
    namespace = "com.elysium.nexus"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.elysium.nexus.controller"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-foundation"

        // No instrumentation runner until we add AndroidX Test. We add
        // it in 0.5 alongside the first Context-dependent test.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // We do not sign release builds until Phase 4+. A release
            // build is a "this would package, but you have not provided
            // a keystore" build. That's intentional — the project does
            // not target Play Store (§1 of AGENTS.md), and we have no
            // signing identity to claim.
            isMinifyEnabled = false
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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
