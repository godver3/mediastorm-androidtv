package com.strmr.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.strmr.tv.home.NewEpisodeBadge
import com.strmr.tv.ui.AppIcons

/** `RECENT_EPISODE_BADGE_READY_COLOR` / `…_NOT_READY_COLOR`. */
private val ReadyColor = Color(0xFFFACC15)
private val NotReadyColor = Color(0xFF6B7280)

/** Sparkles and "New" in a rounded tag, in the Expo badge's colours (the tvOS `NewEpisodeBadgeView`). */
@Composable
fun NewEpisodeBadgeView(badge: NewEpisodeBadge, modifier: Modifier = Modifier) {
    val content = if (badge.ready) Color.Black else Color.White
    Row(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = "New episode" }
            .background(if (badge.ready) ReadyColor else NotReadyColor, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Sparkles, contentDescription = null, tint = content, modifier = Modifier.size(11.dp))
        Text("New", fontSize = 11.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, color = content)
    }
}
