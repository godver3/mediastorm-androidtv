package com.strmr.tv.ui.sports.race

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.strmr.tv.sports.SportsCircuit
import com.strmr.tv.sports.SportsCircuitOutline
import com.strmr.tv.sports.SportsMotoGPEnrichment
import com.strmr.tv.ui.Accent
import com.strmr.tv.ui.pageBackground
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsNotice
import com.strmr.tv.ui.sports.SportsPanel
import kotlin.math.min

/**
 * A weekend's circuit (Expo `SportsCircuitMap`): community reference geometry drawn north up,
 * its length and attribution; Expand Map shows it full screen. Never live positions.
 */
@Composable
fun SportsCircuitMap(circuit: SportsCircuit) {
    val outline = remember(circuit) { circuit.outline() } ?: return
    var expanded by remember { mutableStateOf(false) }
    val length = circuit.referenceLengthMeters.takeIf { it > 0 }?.let { "%.3f km".format(it / 1000.0) }
    SportsPanel(title = null) {
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("Circuit guide")
            Text(circuit.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(circuit.location.ifEmpty { null }, "Reference layout").joinToString(" · "), fontSize = 12.sp, color = SportsMuted)
            Box {
                CircuitOutline(outline, Modifier.fillMaxWidth().height(140.dp))
                Text("N ↑", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted, modifier = Modifier.align(Alignment.TopEnd))
            }
            length?.let { Text("$it · Reference length", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Text(
                "Community reference geometry. This event's configuration may differ. Live car positions and telemetry are not connected.",
                fontSize = 11.sp, color = SportsMuted,
            )
            if (circuit.attribution.isNotEmpty()) Text(circuit.attribution, fontSize = 10.sp, color = SportsMuted)
            OutlinedButton(onClick = { expanded = true }) { Text("Expand Map") }
        }
    }
    if (expanded) {
        MapDialog(
            title = circuit.name,
            subtitle = listOfNotNull(circuit.location.ifEmpty { null }, "North is up", length?.let { "$it reference length" }).joinToString(" · "),
            note = "Reference layout; event configuration may differ. No live positions, sector boundaries or corner labels are supplied.",
            onClose = { expanded = false },
        ) { CircuitOutline(outline, Modifier.fillMaxWidth().weight(1f)) }
    }
}

/** A circuit map full screen, with a note under it; Close Map or Back closes it. */
@Composable
internal fun MapDialog(title: String, subtitle: String, note: String, onClose: () -> Unit, map: @Composable ColumnScope.() -> Unit) {
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().pageBackground().padding(horizontal = 80.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = SportsMuted)
            map()
            Text(note, fontSize = 12.sp, color = SportsMuted)
            Button(onClick = onClose, modifier = Modifier.focusRequester(close)) { Text("Close Map") }
        }
    }
}

/** The outline scaled to fit and centred: a wide dim track under a thin accent line (Expo's two polylines). */
@Composable
private fun CircuitOutline(outline: SportsCircuitOutline, modifier: Modifier) {
    Canvas(modifier) {
        val inset = 12.dp.toPx()
        val scale = min((size.width - 2 * inset) / outline.width, (size.height - 2 * inset) / outline.height)
        val left = (size.width - outline.width * scale) / 2
        val top = (size.height - outline.height * scale) / 2
        val path = Path()
        outline.points.forEachIndexed { i, (x, y) ->
            val px = (left + x * scale).toFloat()
            val py = (top + y * scale).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, Color.White.copy(alpha = 0.16f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The organizer's guide to a MotoGP circuit (Expo `SportsMotoGPCircuit` in its
 * `SportsOrganizerGuide`): the published diagram, then length, corners and the longest
 * straight; a notice while it loads or when it can't.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportsMotoGPGuide(guide: SportsMotoGPEnrichment?, failed: Boolean, venue: String?, onRetry: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (guide == null) {
        SportsNotice(
            if (failed) "Circuit guide unavailable" else "Loading circuit guide",
            if (failed) "Session results remain available. Try the circuit guide again." else "Fetching the organizer's circuit information.",
            loading = !failed, onRetry = onRetry.takeIf { failed },
        )
        return
    }
    val circuit = guide.circuit
    val map = circuit?.mapUrl?.takeIf { it.startsWith("https://") }
    SportsPanel(title = null) {
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("Organizer guide")
            Text(circuit?.name ?: venue ?: "Circuit guide", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(guide.season?.let { "$it event" }, "Official static diagram").joinToString(" · "), fontSize = 12.sp, color = SportsMuted)
            if (guide.stale) Text("Updates delayed · Showing the last verified guide.", fontSize = 12.sp, color = SportsMuted)
            if (map != null) {
                SubcomposeAsyncImage(
                    model = map, contentDescription = "Circuit map", contentScale = ContentScale.Fit,
                    error = { Text("Guide image unavailable", fontSize = 12.sp, color = SportsMuted) },
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF151518)),
                )
            } else {
                Text(guide.reason ?: "An inline diagram is not available for this selection.", fontSize = 12.sp, color = SportsMuted)
            }
            if (circuit != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOfNotNull(
                        circuit.lengthMeters?.let { "%.3f km".format(it / 1000.0) },
                        circuit.leftCorners?.let { "$it left corners" },
                        circuit.rightCorners?.let { "$it right corners" },
                        circuit.longestStraightMeters?.let { "$it m longest straight" },
                    ).forEach { Text(it, fontSize = 13.sp) }
                }
            }
            if (map != null) Text("Published circuit guide · No live positions", fontSize = 11.sp, color = SportsMuted)
            if (map != null) OutlinedButton(onClick = { expanded = true }) { Text("Expand Map") }
            if (guide.state != "available") OutlinedButton(onClick = onRetry) { Text("Retry Guide") }
        }
    }
    if (expanded && map != null) {
        MapDialog(
            title = circuit?.name ?: venue ?: "Circuit guide",
            subtitle = listOfNotNull(guide.season?.let { "$it event" }, "Official static diagram").joinToString(" · "),
            note = "Published circuit guide · No live positions",
            onClose = { expanded = false },
        ) {
            SubcomposeAsyncImage(
                model = map, contentDescription = "Circuit map", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF151518)),
            )
        }
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent, letterSpacing = 1.sp)
}
