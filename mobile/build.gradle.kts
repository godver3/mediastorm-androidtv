import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The phone and tablet app: Material 3 screens on the shared core (:core).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.strmr.mobile"
    compileSdk = 36

    defaultConfig {
        // Its own ID, so it installs beside the React Native app (com.strmr.app) until it
        // replaces it.
        applicationId = "com.strmr.mobile"
        minSdk = 26
        targetSdk = 36
        // The release script passes the published build + 1, as for the TV app.
        versionCode = (project.findProperty("strmrVersionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
        // Where the app looks for a newer build (AppUpdater).
        val manifestUrl = project.findProperty("strmrUpdateManifestUrl") as String?
            ?: "https://github.com/godver3/mediastorm/releases/download/android-native-latest/mediastorm-android.json"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$manifestUrl\"")
    }
    buildTypes {
        release {
            // Sideloaded for now: the debug key, so it installs over a debug build.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    sourceSets["test"].kotlin.srcDir("../core/src/sharedTest/java")
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
    implementation(project(":core"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Background playback: the media notification, lock screen and Bluetooth controls.
    implementation("androidx.media3:media3-session:1.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // The FFmpeg audio decoders (TrueHD, DTS…): scripts/sync-vendor.sh copies them to core/libs.
    fileTree("../core/libs") { include("*.aar") }.takeIf { !it.isEmpty }?.let { implementation(it) }
}
