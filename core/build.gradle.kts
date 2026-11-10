import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Shared by the Android TV app (:app) and the phone and tablet app (:mobile): the API client,
// session, stores, player engine and its rules, iroh, Dolby Vision. No Compose for TV here
// (androidx.tv.material3): each app draws its own UI.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Debug-only test values (server, account, profile) that prefill the sign-in screens; never
// committed and never in release builds: see secrets.properties in the README.
val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(key: String): String =
    "\"" + (secrets.getProperty(key) ?: "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val prefillKeys = mapOf(
    "DEBUG_SERVER_URL" to "serverUrl", "DEBUG_USERNAME" to "username", "DEBUG_PASSWORD" to "password",
    "DEBUG_PROFILE_NAME" to "profileName", "DEBUG_PROFILE_PIN" to "profilePin",
)

android {
    namespace = "com.strmr.core"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 25
        consumerProguardFiles("consumer-rules.pro")
        // The ABIs the vendored native libraries ship for.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        // The Dolby Vision bridge links libdovi when scripts/sync-vendor.sh has copied it in.
        externalNativeBuild {
            cmake { arguments += "-DSTRMR_LIBDOVI_ROOT=${file("libdovi").absolutePath}" }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildTypes {
        debug { prefillKeys.forEach { (field, key) -> buildConfigField("String", field, secret(key)) } }
        release { prefillKeys.keys.forEach { buildConfigField("String", it, "\"\"") } }
    }
    // Test fixtures both modules' tests use (sports API samples).
    sourceSets["test"].kotlin.srcDir("src/sharedTest/java")
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        // java.time on Fire OS 6 (Android 7.1), which predates it.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    api(platform("androidx.compose:compose-bom:2025.05.00"))
    // Compose runtime state in the stores, and the shared drawing helpers.
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material:material-icons-core")
    api("androidx.activity:activity-compose:1.10.1")
    api("io.coil-kt:coil-compose:2.7.0")
    // The Android TV home screen's channels and Watch Next row (launcher/); idle on phones.
    api("androidx.tvprovider:tvprovider:1.1.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    // Playback: ExoPlayer on the app's OkHttp client, PlayerView for the video surface.
    val media3 = "1.10.1"
    api("androidx.media3:media3-exoplayer:$media3")
    // Live channels the server tunes into an HLS session.
    api("androidx.media3:media3-exoplayer-hls:$media3")
    api("androidx.media3:media3-datasource-okhttp:$media3")
    api("androidx.media3:media3-ui:$media3")
    api("androidx.media3:media3-ui-compose:$media3")
    // The nullness annotations in the vendored Matroska extractor (player/dv7/mkv).
    compileOnly("org.checkerframework:checker-qual:3.43.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
