plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * :tvlink — ONE copy of the TV Node wire truth, compiled into the controller
 * build (Master Order v0.10 Phase 21 / audit P0-4: no second truth engine).
 *
 * The wire protocol, channel crypto, pairing ceremony and listener classes
 * are owned by `apps/android-tv-node` and physically compiled here from the
 * same sources — the controller APK and the TV Node APK can never drift,
 * because there is only one source of truth for the bytes on the wire.
 *
 * Only the ANDROID-INDEPENDENT core is shared: canonical, protocol, channel,
 * transport, pairing and credential (incl. the Android Keystore vault).
 * App-level wiring (PairingActivity, TvNodeApp, discovery glue) stays
 * per-app.
 */

android {
    namespace = "com.elysium.nexus.tvnode"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets["main"].java.srcDirs(
        "../../android-tv-node/app/src/main/java/com/elysium/nexus/tvnode/canonical",
        "../../android-tv-node/app/src/main/java/com/elysium/nexus/tvnode/protocol",
        "../../android-tv-node/app/src/main/java/com/elysium/nexus/tvnode/channel",
        "../../android-tv-node/app/src/main/java/com/elysium/nexus/tvnode/transport",
        "../../android-tv-node/app/src/main/java/com/elysium/nexus/tvnode/pairing",
        "../../android-tv-node/app/src/main/java/com/elysium/nexus/tvnode/credential"
    )
    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // zxing-core is a pure-Java QR encoder used by the shared pairing UX
    // (QrPairingRenderer lives in the shared `pairing` package).
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}