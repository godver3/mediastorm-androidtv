package com.strmr.tv.ui.sports.cycling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsCyclingFormat
import com.strmr.tv.sports.SportsCyclingListing
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.tv.ui.CardFocusScale
import com.strmr.tv.ui.cardFocusBorder
import com.strmr.tv.ui.sports.SportsAmber
import com.strmr.tv.ui.sports.SportsCardColor
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsStatusPill

/**
 * A cycling race's day on the Sports hub (Expo `SportsHubShowcase` cycling card, a
 * `FieldEventCard`): "CYCLING" and the day's state (REST DAY, SCHEDULE…), the race, the stage,
 * its distance and terrain (on a rest day "Racing resumes …"), then the stage's first two
 * finishers with their gaps once results are in. Hide Scores drops the finishers.
 */
@Composable
fun SportsCyclingCard(listing: SportsCyclingListing, spoilers: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rows = SportsCyclingFormat.cardRows(listing, spoilers)
    val pill = SportsCyclingFormat.cardPill(listing)
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(),
        // The body colour on the card too: a card stretched to its row's height stays one surface.
        colors = CardDefaults.colors(containerColor = SportsCardColor, focusedContainerColor = SportsCardColor),
        modifier = modifier.width(300.dp),
    ) {
        Column(Modifier.fillMaxSize().background(SportsCardColor).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(SportsCyclingFormat.cardLabel(listing.race).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                     color = SportsMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                SportsStatusPill(pill, amber = listing.lifecycle == SportsLifecycle.CANCELED)
            }
            Text(SportsRaceFormat.cardTitle(listing.race.name), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                 maxLines = 2, overflow = TextOverflow.Ellipsis)
            SportsCyclingFormat.cardSubtitle(listing).ifEmpty { null }?.let {
                Text(it, fontSize = 12.sp, color = SportsMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(SportsCyclingFormat.cardProgress(listing), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (rows.isEmpty()) Text(SportsCyclingFormat.cardEmpty(listing, spoilers), fontSize = 12.sp, color = SportsMuted, maxLines = 2)
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${row.rank}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SportsMuted, modifier = Modifier.width(16.dp))
                    Text(row.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    row.value?.let { Text(it, fontSize = 12.sp, color = SportsMuted, maxLines = 1) }
                }
            }
            if (listing.stage?.results?.stale == true) Text("Updates delayed", fontSize = 11.sp, color = SportsAmber)
        }
    }
}
