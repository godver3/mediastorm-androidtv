package com.strmr.mobile.ui.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.strmr.mobile.downloads.DownloadActions
import com.strmr.mobile.downloads.DownloadFormat
import com.strmr.mobile.downloads.DownloadRequest
import com.strmr.mobile.downloads.DownloadStorage
import com.strmr.mobile.downloads.downloadSources
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.SourceResult
import com.strmr.tv.player.SourceSwitchRules
import com.strmr.tv.ui.AppToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * The download source picker (the iPhone app's `MobileDownloadSourcePicker`), for whatever
 * [DownloadActions.pick] asked: the indexer results ranked for downloading, with their size and
 * health; for a season only packs covering it. Picking one resolves and queues it (a toast says
 * how it went), asking first for the notification permission the progress notification needs.
 * Placed once at the root of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPickerHost() {
    val request by DownloadActions.picking.collectAsState()
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val r = request ?: return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { DownloadActions.picking.value = null }, sheetState = sheet) {
        SourceList(r) { result ->
            scope.launch { sheet.hide() }.invokeOnCompletion { DownloadActions.picking.value = null }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            AppToast.show(if (r.season == null) "Preparing download…" else "Preparing episodes…")
            // Outlives the sheet: a season pack can take minutes to resolve.
            queueScope.launch { AppToast.show(DownloadActions.queue(result, r)) }
        }
    }
}

private val queueScope = MainScope()

@Composable
private fun SourceList(request: DownloadRequest, onPick: (SourceResult) -> Unit) {
    var results by remember(request) { mutableStateOf<List<SourceResult>?>(null) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    LaunchedEffect(request) {
        try {
            results = ApiClient.downloadSources(request.query, request.target, request.episode?.numbering, request.season != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Search failed"
        }
    }
    Text(request.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
         modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    val list = results
    when {
        error != null -> Message("Search Failed", error!!)
        list == null -> Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Searching sources…", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        list.isEmpty() -> Message(if (request.season == null) "No Sources Found" else "No Season Packs Found", null)
        else -> LazyColumn(Modifier.navigationBarsPadding()) {
            item {
                val header = request.season?.let { "Packs for all ${it.episodes.size} episodes" }
                    ?: if (list.size == 1) "1 source" else "${list.size} sources"
                Text(header, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            items(list, key = { it.index }) { result ->
                ListItem(
                    headlineContent = {
                        Text(SourceSwitchRules.releaseName(result.title, result.attributes["raw_title"]), maxLines = 2,
                             overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(SourceSwitchRules.detail(result.attributes, result.sizeBytes, result.serviceType, result.indexer,
                                                      result.health?.state))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(result) },
                )
            }
            item {
                Text("${DownloadFormat.bytes(DownloadStorage.availableBytes)} available on this device.",
                     style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                     modifier = Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun Message(title: String, detail: String?) {
    Box(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
        androidx.compose.foundation.layout.Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
