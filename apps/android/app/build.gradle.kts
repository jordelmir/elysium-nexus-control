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

    // V0.7 Phase 30: release signing config without hardcoded password fallbacks
    signingConfigs {
        create("release") {
            val storePass = System.getenv("RELEASE_STORE_PASSWORD")
            val keyPass = System.getenv("RELEASE_KEY_PASSWORD")
            val alias = System.getenv("RELEASE_KEY_ALIAS") ?: "elysium-nexus"
            val ksFile = file("../release.jks")
            if (ksFile.exists() && !storePass.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = ksFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    defaultConfig {
        applicationId = "com.elysium.nexus.controller"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 7
        versionName = "0.7.0-retail-truth"

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
            // V0.7 Phase 30: production signing only when verified env keys are present.
            isMinifyEnabled = true
            isShrinkResources = true
            val relConfig = signingConfigs.findByName("release")
            if (relConfig?.storeFile?.exists() == true && !relConfig.storePassword.isNullOrEmpty()) {
                signingConfig = relConfig
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // V0.7 Phase 30 (fail closed, verificado en batch del 2026-08-14):
    // `assembleRelease` SIN credenciales verificadas emite APK *unsigned*
    // en silencio (app-release-unsigned.apk). La Regla Comercial Hard #9
    // exige fail closed: release jamás se produce sin firma de release.
    gradle.taskGraph.whenReady {
        val releaseArtifactNames = setOf("assembleRelease", "bundleRelease")
        val needsReleaseArtifact = allTasks.any { it.name in releaseArtifactNames }
        if (needsReleaseArtifact) {
            val release = signingConfigs.findByName("release")
            val hasCredentials = release?.storeFile?.exists() == true &&
                !release.storePassword.isNullOrEmpty() &&
                !release.keyPassword.isNullOrEmpty()
            if (!hasCredentials) {
                throw GradleException(
                    "RELEASE SIGNING BLOCKED (fail closed): expected ../release.jks + " +
                        "RELEASE_STORE_PASSWORD / RELEASE_KEY_PASSWORD verified env vars. " +
                        "No unsigned release artifact will be produced."
                )
            }
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

    sourceSets {
        // P0-12 + V06-P4: expose exported Room schemas to androidTest
        // so MigrationTestHelper can validate migrations against the
        // canonical schema JSON files.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
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
    // P0.3: ViewModel Compose integration for process-death-safe state
    implementation(libs.androidx.lifecycle.viewmodel.compose)
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
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // V06-P4: MigrationTestHelper for Room migration tests
    androidTestImplementation(libs.androidx.room.testing)
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

// V0.6.3 Phase 14 / V0.7 XXVI: Verify IR catalog asset integrity before build.
// The LOCAL gate now enforces the SAME contract CI enforces:
// exact SHA-256 and exact byte size against ir_catalog.manifest.json.
// A wrong database of any size must fail locally, not just in CI.
tasks.register<Exec>("verifyIrCatalogAsset") {
    group = "elysium"
    description = "Verify ir_catalog.db SHA-256 + size exactly match ir_catalog.manifest.json before build"
    commandLine("sh", "-c",
        "set -e; " +
        "DB=src/main/assets/ir/ir_catalog.db; " +
        "MANIFEST=src/main/assets/ir/ir_catalog.manifest.json; " +
        "test -f \"\$DB\" || { echo 'ERROR: ir_catalog.db not found'; exit 1; }; " +
        "test -f \"\$MANIFEST\" || { echo 'ERROR: ir_catalog.manifest.json not found'; exit 1; }; " +
        "MAGIC=\$(xxd -l 16 -p \"\$DB\"); " +
        "test \"\$MAGIC\" = 53514c69746520666f726d6174203300 || { echo \"ERROR: SQLite magic mismatch: \$MAGIC\"; exit 1; }; " +
        "EXPECTED_SHA=\$(sed -n 's/.*\"databaseSha256\"[[:space:]]*:[[:space:]]*\"\\([a-f0-9]\\{64\\}\\)\".*/\\1/p' \"\$MANIFEST\" | head -1); " +
        "test -n \"\$EXPECTED_SHA\" || { echo 'ERROR: databaseSha256 not found in manifest'; exit 1; }; " +
        "if command -v sha256sum >/dev/null 2>&1; then ACTUAL_SHA=\$(sha256sum \"\$DB\" | awk '{print \$1}'); else ACTUAL_SHA=\$(shasum -a 256 \"\$DB\" | awk '{print \$1}'); fi; " +
        "test \"\$ACTUAL_SHA\" = \"\$EXPECTED_SHA\" || { echo \"ERROR: SHA-256 mismatch: manifest=\$EXPECTED_SHA actual=\$ACTUAL_SHA\"; exit 1; }; " +
        "EXPECTED_SIZE=\$(sed -n 's/.*\"databaseSizeBytes\"[[:space:]]*:[[:space:]]*\\([0-9][0-9]*\\).*/\\1/p' \"\$MANIFEST\" | head -1); " +
        "test -n \"\$EXPECTED_SIZE\" || { echo 'ERROR: databaseSizeBytes not found in manifest'; exit 1; }; " +
        "ACTUAL_SIZE=\$(stat -f%z \"\$DB\" 2>/dev/null || stat -c%s \"\$DB\"); " +
        "test \"\$ACTUAL_SIZE\" = \"\$EXPECTED_SIZE\" || { echo \"ERROR: size mismatch: manifest=\$EXPECTED_SIZE actual=\$ACTUAL_SIZE\"; exit 1; }; " +
        "echo \"verifyIrCatalogAsset: PASS sha=\$ACTUAL_SHA size=\$ACTUAL_SIZE magic=OK\""
    )
}
tasks.named("preBuild") { dependsOn("verifyIrCatalogAsset") }
