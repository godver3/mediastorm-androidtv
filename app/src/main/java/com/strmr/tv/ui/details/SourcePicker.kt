package com.strmr.tv.ui.details

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.Episode
import com.strmr.tv.api.SourceHealth
import com.strmr.tv.api.SourceResult
import com.strmr.tv.api.searchSources
import com.strmr.tv.api.sourceHealth
import com.strmr.tv.player.ManualSourceTracks
import com.strmr.tv.player.SourceSwitchRules
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.BrandBackground

/**
 * Full-screen list of the backend's indexer / scraper results for one movie or episode, with
 * debrid cache / usenet health badges (pending ones long-polled until they settle). Long press
 * on a result opens its track panel ([SourceTracks]); the tracks chosen there are remembered
 * for this visit and also apply when the result is selected directly.
 */
@Composable
fun SourcePicker(title: DetailsTarget, episode: Episode?, onPick: (SourceResult, ManualSourceTracks?) -> Unit, onDismiss: () -> Unit) {
    val tracks = rememberSourceTracks()
    var results by remember { mutableStateOf<List<SourceResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val health = remember { mutableStateMapOf<Int, SourceHealth>() }
    val heading = episode?.let { "${title.name} · ${it.code}" } ?: title.name
    val first = remember { FocusRequester() }
    LaunchedEffect(results.isNotEmpty()) { if (results.isNotEmpty()) runCatching { first.requestFocus() } }

    LaunchedEffect(Unit) {
        val query = SourceSwitchRules.query(title, episode?.code)
        try {
            results = ApiClient.searchSources(query, title)
            results.forEach { r -> r.health?.let { health[r.index] = it } }
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
        // Long-polls each pending check until it completes, applying results by index.
        for (token in health.values.filter { it.state == "pending" }.mapNotNull { it.token }.toSet()) {
            var complete = false
            while (!complete) {
                val snapshot = runCatching { ApiClient.sourceHealth(token) }.getOrNull() ?: break
                snapshot.results.forEach { health[it.index] = it.health }
                complete = snapshot.complete
            }
        }
        // Anything still pending (poll failed or token expired) stops spinning.
        health.filterValues { it.state == "pending" }.keys.forEach { health[it] = SourceHealth("unknown") }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(BrandBackground), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 760.dp),
                contentPadding = PaddingValues(vertical = 36.dp, horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Text(heading, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (!loading && error == null && results.isNotEmpty()) {
                            Text(if (results.size == 1) "1 source" else "${results.size} sources", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                when {
                    loading -> item {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                            Text("Searching sources…", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    error != null -> item { Text(error!!, color = Color.White.copy(alpha = 0.6f)) }
                    results.isEmpty() -> item { Text("No sources found", color = Color.White.copy(alpha = 0.6f)) }
                    else -> items(results, key = { it.index }) { result ->
                        SourceRow(
                            result, health[result.index], tracks.hasChoice(result),
                            if (result == results.first()) Modifier.focusRequester(first) else Modifier,
                            onLongClick = { tracks.open(result) },
                        ) {
                            onDismiss()
                            onPick(result, tracks.manualFor(result))
                        }
                    }
                }
            }
        }
        tracks.Panel { result, manual ->
            onDismiss()
            onPick(result, manual)
        }
    }
}

@Composable
private fun SourceRow(result: SourceResult, health: SourceHealth?, tracksChosen: Boolean, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val attrs = result.attributes
    // Release name: the first line of the scraper's raw title when present.
    val release = (attrs["raw_title"] ?: result.title).lineSequence().firstOrNull() ?: result.title
    val badges = buildList {
        attrs["resolution"]?.takeIf { it.isNotEmpty() }?.let { add(if (it == "2160p") "4K" else it) }
        if (attrs["hasDV"] == "true") add("DV")
        attrs["hdr"]?.let { if (it.contains("HDR10+")) add("HDR10+") else if (it.contains("HDR")) add("HDR") }
        attrs["quality"]?.takeIf { it.isNotEmpty() }?.let { add(it) }
        attrs["codec"]?.takeIf { it.isNotEmpty() }?.let { add(it.uppercase()) }
    }
    val details = buildList {
        if (result.sizeBytes > 0) add(Formatter.formatShortFileSize(context, result.sizeBytes))
        add(if (result.serviceType == "usenet") "Usenet" else attrs["tracker"] ?: result.indexer)
        attrs["seeders"]?.takeIf { it.isNotEmpty() && result.serviceType != "usenet" }?.let { add("$it seeders") }
        attrs["audio"]?.takeIf { it.isNotEmpty() }?.let { add(it) }
        attrs["channels"]?.takeIf { it.isNotEmpty() }?.let { add(it) }
        attrs["languages"]?.takeIf { it.isNotEmpty() }?.let { add(it.replace(",", " ").replace("⚙", "").trim()) }
        if (tracksChosen) add("Tracks chosen")
    }.filter { it.isNotBlank() }
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        overlineContent = if (badges.isNotEmpty()) { { Text(badges.joinToString("  ·  "), fontWeight = FontWeight.Bold) } } else null,
        headlineContent = { Text(release, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details.joinToString("  ·  "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = health?.let { { HealthBadge(it) } },
        modifier = modifier,
    )
}

/** Debrid cache / usenet health: green when playable, red when not, a spinner while checking. */
@Composable
private fun HealthBadge(health: SourceHealth) {
    when (health.state) {
        "pending" -> CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        "healthy", "cached" -> Icon(Icons.Filled.CheckCircle, contentDescription = if (health.state == "cached") "Cached" else "Healthy", tint = Color(0xFF30D158))
        "unhealthy", "not_cached" -> Icon(Icons.Filled.Clear, contentDescription = if (health.state == "not_cached") "Not Cached" else "Unhealthy", tint = Color(0xFFFF453A))
        else -> Box(Modifier.size(24.dp))
    }
}
