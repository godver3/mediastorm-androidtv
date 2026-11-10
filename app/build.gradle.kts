import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.strmr.tv"
    compileSdk = 36

    defaultConfig {
        // Its own ID, so it installs beside the React Native TV build (com.strmr.app).
        applicationId = "com.strmr.tv"
        // Android 7.1: Fire OS 6, the first Fire TV Stick 4K.
        minSdk = 25
        targetSdk = 36
        // The release script passes the published build + 1 (scripts/build-release.sh), so the
        // repo stays clean; local builds are 1 and never look newer than a published one.
        versionCode = (project.findProperty("strmrVersionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
        // Where the app looks for a newer build (AppUpdater); a test server can stand in.
        val manifestUrl = project.findProperty("strmrUpdateManifestUrl") as String?
            ?: "https://github.com/godver3/mediastorm/releases/download/androidtv-native-latest/mediastorm-androidtv.json"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$manifestUrl\"")
    }
    buildTypes {
        release {
            // Sideloaded for now (the Fire TV, the emulator): the debug key, so it installs over a
            // debug build and keeps its sign-in.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // Test fixtures both modules' tests use (sports API samples).
    sourceSets["test"].kotlin.srcDir("../core/src/sharedTest/java")
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

// The 500-line cap on Kotlin files (every module) (scripts/check-file-size.sh), before every build.
val checkFileSize by tasks.registering(Exec::class) {
    workingDir = rootDir
    commandLine("bash", "scripts/check-file-size.sh")
    onlyIf { rootDir.resolve("scripts/check-file-size.sh").exists() }
}
tasks.named("preBuild") { dependsOn(checkFileSize) }

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Text fields only: Compose for TV has none, and the IME handles input on TV.
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-material:1.0.1")
    // Applies the baseline profiles Compose and the other AndroidX libraries ship, so their
    // code is compiled ahead of time on a sideloaded install instead of interpreted at first.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    testImplementation("junit:junit:4.13.2")
    // The FFmpeg audio decoders (TrueHD, DTS…), the same build the React Native app ships;
    // scripts/sync-vendor.sh copies it to core/libs. ExoPlayer loads it by name, so builds without it
    // still work, minus those codecs.
    fileTree("../core/libs") { include("*.aar") }.takeIf { !it.isEmpty }?.let { implementation(it) }
}
