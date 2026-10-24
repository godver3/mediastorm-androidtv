package com.strmr.tv.ui.sports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val PanelShape = RoundedCornerShape(12.dp)

/**
 * A titled card of a sports page (Expo `sportsDetailPanel`, tvOS `SportsPanel`): a faint
 * surface with the title and an optional note, then its rows. Rows the remote should reach are
 * [SportsReadout]s, whose text lines up with the title.
 */
@Composable
fun SportsPanel(title: String?, modifier: Modifier = Modifier, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(PanelShape).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 6.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (title != null || note != null) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                title?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                note?.let { Text(it, fontSize = 12.sp, color = SportsMuted) }
            }
        }
        content()
    }
}

/**
 * A row the remote can land on to read it (Expo `SportsReadout`, tvOS `sportsReadout`): when
 * focused it shows a faint fill and a focused TV card's white hairline, and the page scrolls to
 * it. [onClick] makes it a control; [selected] tints it, and [tint] gives it a colour of its own
 * (a scoring play in its side's colour, the drive in progress).
 */
@Composable
fun SportsReadout(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = tint ?: if (selected) Color.White.copy(alpha = 0.09f) else Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.14f).let { if (tint != null) it.compositeOver(tint) else it },
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}

/**
 * A sports page's status note (Expo `SportsNotice`): what is missing or late and why, a spinner
 * while it loads, and Try Again when a retry can help.
 */
@Composable
fun SportsNotice(title: String, message: String, modifier: Modifier = Modifier, loading: Boolean = false, onRetry: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.14f), PanelShape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(message, fontSize = 12.sp, color = SportsMuted)
        }
        onRetry?.let { OutlinedButton(onClick = it) { Text("Try Again") } }
    }
}

/** A labelled figure: the label small and muted over the value. */
@Composable
fun SportsFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, fontSize = 11.sp, color = SportsMuted, maxLines = 1)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A table's column heading: small, muted capitals. */
@Composable
fun SportsColumnHeading(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = SportsMuted, maxLines = 1, modifier = modifier)
}

/** An inline reading, "LAST 1:23.100": the label muted, the value set off in semibold. */
@Composable
fun SportsReading(label: String, value: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = SportsMuted)) { append("$label ") }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value) }
        },
        fontSize = 12.sp, maxLines = 1, modifier = modifier,
    )
}
