import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
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

android {
    namespace = "com.strmr.tv"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        // Its own ID, so it installs beside the React Native TV build (com.strmr.app).
        applicationId = "com.strmr.tv"
        minSdk = 28
        targetSdk = 36
        // The release script passes the published build + 1 (scripts/build-release.sh), so the
        // repo stays clean; local builds are 1 and never look newer than a published one.
        versionCode = (project.findProperty("strmrVersionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
        // Where the app looks for a newer build (AppUpdater); a test server can stand in.
        val manifestUrl = project.findProperty("strmrUpdateManifestUrl") as String?
            ?: "https://github.com/godver3/mediastorm/releases/download/androidtv-native-latest/mediastorm-androidtv.json"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$manifestUrl\"")
        // The Dolby Vision bridge links libdovi when scripts/sync-vendor.sh has copied it in.
        // The ABIs the vendored native libraries ship for.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
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
        debug {
            buildConfigField("String", "DEBUG_SERVER_URL", secret("serverUrl"))
            buildConfigField("String", "DEBUG_USERNAME", secret("username"))
            buildConfigField("String", "DEBUG_PASSWORD", secret("password"))
            buildConfigField("String", "DEBUG_PROFILE_NAME", secret("profileName"))
            buildConfigField("String", "DEBUG_PROFILE_PIN", secret("profilePin"))
        }
        release {
            // Sideloaded for now (the Fire TV, the emulator): the debug key, so it installs over a
            // debug build and keeps its sign-in.
            signingConfig = signingConfigs.getByName("debug")
            listOf("DEBUG_SERVER_URL", "DEBUG_USERNAME", "DEBUG_PASSWORD", "DEBUG_PROFILE_NAME", "DEBUG_PROFILE_PIN")
                .forEach { buildConfigField("String", it, "\"\"") }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// The 500-line cap on Kotlin files (scripts/check-file-size.sh), before every build.
val checkFileSize by tasks.registering(Exec::class) {
    workingDir = rootDir
    commandLine("bash", "scripts/check-file-size.sh")
    onlyIf { rootDir.resolve("scripts/check-file-size.sh").exists() }
}
tasks.named("preBuild") { dependsOn(checkFileSize) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // Text fields only: Compose for TV has none, and the IME handles input on TV.
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.tv:tv-material:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    // Applies the baseline profiles Compose and the other AndroidX libraries ship, so their
    // code is compiled ahead of time on a sideloaded install instead of interpreted at first.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // The Android TV home screen's channels and Watch Next row (launcher/), as the Expo app.
    implementation("androidx.tvprovider:tvprovider:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Playback: ExoPlayer on the app's OkHttp client, PlayerView for the video surface and
    // captions, and the Compose surface for the details page's trailer.
    val media3 = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    // Live channels the server tunes into an HLS session.
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-ui-compose:$media3")
    // The nullness annotations in the vendored Matroska extractor (player/dv7/mkv).
    compileOnly("org.checkerframework:checker-qual:3.43.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // The FFmpeg audio decoders (TrueHD, DTS…), the same build the React Native app ships;
    // scripts/sync-vendor.sh copies it here. ExoPlayer loads it by name, so builds without it
    // still work, minus those codecs.
    fileTree("libs") { include("*.aar") }.takeIf { !it.isEmpty }?.let { implementation(it) }
}
