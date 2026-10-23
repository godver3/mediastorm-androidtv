package com.strmr.tv.ui.calendar

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.strmr.tv.api.Artwork
import com.strmr.tv.calendar.CalendarItemDetail
import com.strmr.tv.calendar.CalendarModel

/** Width of the panel at the screen's right edge; the feed and timeline stop at its left. */
val CalendarPanelWidth = 280.dp

private val Surface = Color(0xFF06131D)
private val Divider = Color(0xFF1D425A)
private val PanelAccent = Color(0xFF55B8FF)
private val PrimaryText = Color(0xFFF4F8FC)
private val SecondaryText = Color(0xFFA4B4C2)
private val ChipSurface = Color(0xFF0D2B3D)
private val ChipBorder = Color(0xFF28617F)

/**
 * The focused release (Expo `CalendarDetailPanel`, TV layout): backdrop, the show's name over
 * an episode's title, the metadata line, up to four genres, three lines of synopsis and the
 * top four cast, on a panel that fades in from the feed. A spinner sits on the artwork while
 * the title's details load.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarDetailPanel(detail: CalendarItemDetail?, loading: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.horizontalGradient(0f to Color.Transparent, 0.28f to Surface.copy(alpha = 0.94f), 1f to Surface),
            RoundedCornerShape(topStart = 10.dp),
        ),
    ) {
        if (detail == null) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) Spinner()
                Text("Select a release to preview its details.", color = SecondaryText, fontSize = 12.sp)
            }
            return@Box
        }
        Column(Modifier.fillMaxSize().padding(start = 15.dp, end = 15.dp, top = 15.dp)) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1C1C1E))) {
                AsyncImage(
                    model = Artwork.url(detail.backdropUrl ?: detail.posterUrl, "w780"), contentDescription = null,
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize(),
                )
                if (loading) Spinner(Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            Column(Modifier.padding(horizontal = 4.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                val episodeTitle = detail.episodeTitle
                if (!detail.isMovie && episodeTitle != null) {
                    Text(detail.title.trim().uppercase(), color = PanelAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                         letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(episodeTitle ?: detail.title, color = PrimaryText, fontSize = 20.sp, lineHeight = 24.sp,
                     fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CalendarModel.metadataLine(detail).takeIf { it.isNotEmpty() }?.let {
                    Text(it, color = SecondaryText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val genres = detail.genres.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
                if (genres.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        genres.forEach { genre ->
                            Text(
                                genre, color = PrimaryText, fontSize = 11.sp, maxLines = 1,
                                modifier = Modifier.background(ChipSurface, RoundedCornerShape(4.dp))
                                    .border(1.dp, ChipBorder, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(detail.synopsis, color = PrimaryText, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val cast = detail.cast.take(4).map { it.name.trim() }.filter { it.isNotEmpty() }.joinToString(CalendarModel.SEPARATOR)
                if (cast.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Divider))
                        Text("Cast", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(cast, color = SecondaryText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Spinner(modifier: Modifier = Modifier) =
    CircularProgressIndicator(modifier.size(16.dp), color = PanelAccent, strokeWidth = 2.dp)
