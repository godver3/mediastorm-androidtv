package com.strmr.mobile.ui.connection

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.strmr.mobile.connection.ConnectionSelector
import com.strmr.mobile.connection.WifiSsid
import kotlinx.coroutines.launch

/**
 * Asks for location access when Wi-Fi name detection needs it ([WifiSsid.requestAccess]),
 * explaining why first: Android shows the Wi-Fi's name only to apps with precise location.
 * Placed once at the root of the app.
 */
@Composable
fun WifiPermissionPrompt() {
    val wanted by WifiSsid.wantsPermission.collectAsState()
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { ConnectionSelector.select("settings changed") }
    }
    if (!wanted) return
    AlertDialog(
        onDismissRequest = { WifiSsid.wantsPermission.value = false },
        title = { Text("Detect Your Home Wi-Fi?") },
        text = {
            Text("Your server switches addresses between your home Wi-Fi and elsewhere. Android shows the Wi-Fi's name only " +
                "to apps with location access; MediaStorm uses it for this alone.")
        },
        confirmButton = {
            TextButton(onClick = {
                WifiSsid.wantsPermission.value = false
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = { WifiSsid.wantsPermission.value = false }) { Text("Not Now") } },
    )
}

/** The app-wide hosts this lane adds at the root: the download source picker, the Wi-Fi prompt and offline Downloads. */
@Composable
fun ConnectionHosts() {
    OfflineDownloads()
    com.strmr.mobile.ui.downloads.DownloadPickerHost()
    WifiPermissionPrompt()
}
