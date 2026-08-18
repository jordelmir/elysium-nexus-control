plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.elysium.nexus.tvnode"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.elysium.nexus.tvnode"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-tvnode"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
    signingConfigs {
        // Phase 33 — TV Node release signing with an INDEPENDENT long-term
        // identity: TV Node never reuses the Controller keystore. Verified
        // env credentials only; the guard below fails the build otherwise.
        create("release") {
            val storePass = System.getenv("TV_NODE_RELEASE_STORE_PASSWORD")
            val keyPass = System.getenv("TV_NODE_RELEASE_KEY_PASSWORD")
            val alias = System.getenv("TV_NODE_RELEASE_KEY_ALIAS") ?: "elysium-nexus-tvnode"
            val ksFile = file("../tv-node-release.jks")
            if (ksFile.exists() && !storePass.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = ksFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    // Phase 33 — fail closed (mirror of Controller Phase 30 / Hard Rule #9):
    // `assembleRelease` / `bundleRelease` WITHOUT verified TV Node release
    // credentials emit nothing. An unsigned TV Node release must never exist.
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
                    "TV NODE RELEASE SIGNING BLOCKED (fail closed): expected ../tv-node-release.jks + " +
                        "TV_NODE_RELEASE_STORE_PASSWORD / TV_NODE_RELEASE_KEY_PASSWORD verified env vars. " +
                        "TV Node uses its own independent long-term signing identity. " +
                        "No unsigned TV Node release artifact will be produced."
                )
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}