package com.strmr.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.strmr.tv.home.CardBadge
import com.strmr.tv.home.HomeCard
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.settings.BadgeSettings

/**
 * A badge on artwork (tvOS ArtworkBadge). On a poster a status shows as its symbol in a small
 * glass disc; a 16:9 card ([wide]: landscape, or a poster opened on focus) has room for the text capsule.
 */
@Composable
fun Badge(badge: CardBadge, modifier: Modifier = Modifier, wide: Boolean = false) {
    val icon = badge.symbol?.icon
    val count = badge.count
    if (count != null) {
        CountBadge(count, icon, badge.text, modifier)
        return
    }
    if (icon == null || wide) {
        BadgeLabel(badge.text, badge.symbol, modifier = modifier)
        return
    }
    Box(
        modifier.size(26.dp)
            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = badge.text, tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

/** A card's badges stacked in its top-left corner, as the Expo app stacks a release status with the watch state. */
@Composable
fun Badges(card: HomeCard, modifier: Modifier = Modifier, wide: Boolean = false) {
    val badges = card.visibleBadges(BadgeSettings.visibility)
    if (badges.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) { badges.forEach { Badge(it, wide = wide) } }
}

/**
 * A series' unwatched episodes: the watch-state symbol (when shown) then the number, in a
 * capsule the height of the poster disc (Expo's icon-and-number badge).
 */
@Composable
private fun CountBadge(count: Int, icon: ImageVector?, description: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(26.dp)
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp)) }
        Text(count.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Small capsule label on artwork or beside metadata, with an optional leading symbol. */
@Composable
fun BadgeLabel(text: String, symbol: CardBadge.Symbol? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbol?.icon?.let { Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp)) }
        Text(text.uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.25.sp)
    }
}

private val CardBadge.Symbol.icon: ImageVector
    get() = when (this) {
        CardBadge.Symbol.COMING_SOON -> AppIcons.Event
        CardBadge.Symbol.UNRELEASED -> AppIcons.Hourglass
        CardBadge.Symbol.IN_THEATERS -> AppIcons.Theaters
        CardBadge.Symbol.WATCHED -> Icons.Filled.Check
        CardBadge.Symbol.IN_PROGRESS -> AppIcons.WatchedPartial
    }
