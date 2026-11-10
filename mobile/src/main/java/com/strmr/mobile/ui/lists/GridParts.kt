package com.strmr.mobile.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.components.PosterCard
import com.strmr.mobile.ui.components.TitleMenu
import com.strmr.tv.home.HomeCard

/** Poster columns that fit the width: about three on a phone, more on a tablet. */
val PosterColumns = GridCells.Adaptive(minSize = 108.dp)

/** The grids' edge padding, with room under the last row. */
fun gridPadding(top: Dp = 8.dp) = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = 24.dp)

val GridSpacing = Arrangement.spacedBy(12.dp)

/** A row across the whole grid (a header, a status, a spinner). */
fun LazyGridScope.fullWidth(key: Any, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = "full") { content() }
}

/** A grid of title cards, each opening with [open] and showing the title menu on long press. */
fun LazyGridScope.posterCards(cards: List<HomeCard>, open: (HomeCard) -> Unit) {
    items(cards.size, key = { cards[it].id }, contentType = { "poster" }) { i ->
        val card = cards[i]
        PosterCard(
            card, Dp.Unspecified, onClick = { open(card) }, onLongClick = { TitleMenu.show(card) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** An empty or failed grid's message, centred, with an optional action. */
@Composable
fun GridStatus(
    title: String,
    message: String? = null,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        message?.let {
            Text(
                it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        if (action != null && onAction != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun GridSpinner(top: Dp = 56.dp) {
    Box(Modifier.fillMaxWidth().padding(top = top, bottom = 16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}
