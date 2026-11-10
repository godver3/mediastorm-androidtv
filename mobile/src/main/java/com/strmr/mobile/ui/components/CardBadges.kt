package com.strmr.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strmr.tv.home.CardBadge
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.NewEpisodeBadge
import com.strmr.tv.ui.settings.BadgeSettings

private val Scrim = Color.Black.copy(alpha = 0.65f)

/** A badge on artwork: a status symbol in a small disc on posters, a text capsule on wide cards. */
@Composable
fun CardBadgeView(badge: CardBadge, modifier: Modifier = Modifier, wide: Boolean = false) {
    val icon = badge.symbol?.icon
    val count = badge.count
    when {
        count != null -> Row(
            modifier.height(22.dp).background(Scrim, RoundedCornerShape(50))
                .semantics(mergeDescendants = true) { contentDescription = badge.text }.padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
            Text(count.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        icon == null || wide -> BadgeLabel(badge.text, icon, modifier)
        else -> Box(modifier.size(22.dp).background(Scrim, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, badge.text, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

/** A card's badges stacked in a corner, following Settings › Card Badges. */
@Composable
fun CardBadges(card: HomeCard, modifier: Modifier = Modifier, wide: Boolean = false) {
    val badges = card.visibleBadges(BadgeSettings.visibility)
    val new = card.newEpisode
    if (badges.isEmpty() && new == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        new?.let { NewBadge(it) }
        badges.forEach { CardBadgeView(it, wide = wide) }
    }
}

/** "New": the next episode came out in the last 72 hours (accent when its stream is ready). */
@Composable
fun NewBadge(badge: NewEpisodeBadge, modifier: Modifier = Modifier) {
    Text(
        "NEW", color = if (badge.ready) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.background(if (badge.ready) Color(0xFFF5B400) else Scrim, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Small capsule label on artwork, with an optional leading symbol. */
@Composable
fun BadgeLabel(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Scrim, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
        Text(text.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    }
}

val CardBadge.Symbol.icon: ImageVector
    get() = when (this) {
        CardBadge.Symbol.COMING_SOON -> Icons.Outlined.Event
        CardBadge.Symbol.UNRELEASED -> Icons.Outlined.HourglassEmpty
        CardBadge.Symbol.IN_THEATERS -> Icons.Outlined.Theaters
        CardBadge.Symbol.WATCHED -> Icons.Filled.Check
        CardBadge.Symbol.IN_PROGRESS -> Icons.Outlined.Timelapse
    }
