package com.strmr.tv.ui.sports.race

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.strmr.tv.sports.SportsRaceDriver
import com.strmr.tv.sports.SportsRaceFormat
import com.strmr.tv.sports.SportsRaceListing
import com.strmr.tv.ui.CardFocusScale
import com.strmr.tv.ui.cardFocusBorder
import com.strmr.tv.ui.sports.SportsAmber
import com.strmr.tv.ui.sports.SportsCardColor
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsStatusPill

/**
 * A race session on the Sports hub (Expo `SportsHubShowcase` race card and its
 * `FieldEventCard`): series and status, the weekend's title, "session · venue", how the
 * session stands, then the top two entries by position (three once final) with their timing.
 * Hide Scores keeps the schedule and drops the results.
 */
@Composable
fun SportsRaceCard(listing: SportsRaceListing, leagueName: String, spoilers: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val session = listing.session
    val started = session.isLive || session.isFinal
    val rows = if (spoilers || !started) emptyList() else listing.leaders
    val subtitle = listOfNotNull(session.sessionType?.ifEmpty { null }, session.place(listing.weekend)).joinToString(" · ")
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = CardFocusScale), border = cardFocusBorder(),
        // The body colour on the card too: a card stretched to its row's height stays one surface.
        colors = CardDefaults.colors(containerColor = SportsCardColor, focusedContainerColor = SportsCardColor),
        modifier = modifier.width(300.dp),
    ) {
        Column(
            Modifier.fillMaxSize().background(SportsCardColor).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(leagueName.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted)
                Spacer(Modifier.weight(1f))
                SportsStatusPill(listing.lifecycle)
            }
            Text(
                SportsRaceFormat.cardTitle(listing.weekend.title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = SportsMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                SportsRaceFormat.progress(session, spoilers), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (rows.isEmpty()) Text(SportsRaceFormat.cardEmpty(session, spoilers), fontSize = 12.sp, color = SportsMuted)
            rows.forEach { LeaderRow(it) }
            if (session.stale) Text("Updates delayed", fontSize = 11.sp, color = SportsAmber)
        }
    }
}

@Composable
private fun LeaderRow(driver: SportsRaceDriver) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${driver.position}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SportsMuted, modifier = Modifier.width(16.dp))
        Text(driver.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        SportsRaceFormat.cardValue(driver)?.let { Text(it, fontSize = 12.sp, color = SportsMuted, maxLines = 1) }
    }
}
