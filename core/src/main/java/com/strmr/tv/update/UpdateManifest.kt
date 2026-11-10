package com.strmr.tv.update

import kotlinx.serialization.Serializable

/**
 * `mediastorm-androidtv.json`, published beside the APK by `scripts/build-release.sh` on the
 * `androidtv-native-latest` release of godver3/mediastorm. Only a higher [versionCode] than
 * the installed build is an update.
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    /** The APK, under a per-build asset name so a manifest never points at a replaced file. */
    val url: String,
    val sha256: String,
    val size: Long = 0,
    /** SHA-256 of the signing certificate; Android rejects an update signed by another key. */
    val signingCertSha256: String = "",
    /** Commit subjects since the previous build, shown in the update prompt. */
    val notes: List<String> = emptyList(),
    val publishedAt: String? = null,
    /** The build's baseline profiles (dex metadata), so the update is compiled as it installs. */
    val profiles: List<UpdateProfile> = emptyList(),
) {
    val label: String get() = "$versionName ($versionCode)"
}

/** A baseline profile `.dm` for the Android versions [minApi]..[maxApi]. */
@Serializable
data class UpdateProfile(val minApi: Int, val maxApi: Int, val url: String, val sha256: String)
