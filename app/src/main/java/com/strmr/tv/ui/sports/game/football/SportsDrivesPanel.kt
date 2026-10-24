package com.strmr.tv.ui.sports.game.football

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import com.strmr.tv.ui.MinimalScroll
import com.strmr.tv.ui.sports.SportsLive
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsChipRow
import com.strmr.tv.ui.sports.game.SportsColumns
import com.strmr.tv.ui.sports.game.SportsGamePage

/**
 * The Drives tab of NFL and college football games, a drive explorer (Expo `FootballDrivesTable`
 * with `DrivePosition`, plus each drive's plays; tvOS `SportsDrivesPanel`): both sides or one, the
 * drives newest first with their place on the field, and beside them the chosen drive's chart and
 * plays. The chosen drive follows the remote down the list and stays marked while its plays are
 * read; Select moves on to its plays and Left comes back to it. Each column scrolls on its own,
 * and the page keeps the whole explorer in view while the remote is in it.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SportsDrivesPanel(page: SportsGamePage) {
    var teamId by remember { mutableStateOf<String?>(null) }
    var chosenId by remember { mutableStateOf<String?>(null) }
    var highlighted by remember { mutableStateOf<String?>(null) }
    // Where focus goes once the chosen drive's plays have been drawn.
    var landing by remember { mutableStateOf<FocusRequester?>(null) }
    val focus = remember { DriveFocus() }
    val possession = remember(page) { SportsFootballPossession.of(page) }
    val drives = page.detail.footballDrives
    val current = drives.firstOrNull { SportsDriveResult.of(it, possession).current }
    val explorer = SportsDriveExplorer(drives, teamId, chosenId, current?.id)
    val selected = explorer.selected
    val listed = explorer.listed
    val height = (LocalConfiguration.current.screenHeightDp - 110).coerceAtLeast(300).dp
    val whole = remember { WholeView() }

    LaunchedEffect(landing) {
        val target = landing ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { target.requestFocus() }
        landing = null
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
        SportsColumns(Modifier.onSizeChanged { whole.size = it.toSize() }.bringIntoViewResponder(whole)) {
            Column(Modifier.weight(1f).height(height).focusGroup()) {
                SportsPanel("Drives", Modifier.fillMaxHeight(), note = "Newest first · ${listed.size} drives") {
                    SportsChipRow(focus.chip) {
                        listOf(null, page.game.awayTeam, page.game.homeTeam).forEach { team ->
                            val chosen = team?.id == teamId
                            FilterChip(
                                selected = chosen, onClick = { teamId = team?.id },
                                leadingIcon = team?.let { { SportsTeamLogo(it, 16.dp) } },
                                modifier = if (chosen) Modifier.focusRequester(focus.chip) else Modifier,
                            ) { Text(team?.shortName ?: "Both Teams") }
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth()
                            .focusProperties { onEnter = { if (requestedFocusDirection != FocusDirection.Enter) runCatching { focus.chosen.requestFocus() } } }
                            .focusGroup().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (listed.isEmpty()) SportsReadout { Text("No drives supplied for this team.", fontSize = 14.sp, color = SportsMuted) }
                        listed.forEach { drive ->
                            key(drive.id) {
                                val result = SportsDriveResult.of(drive, possession)
                                val isSelected = drive.id == selected?.id
                                SportsReadout(
                                    (if (isSelected) Modifier.focusRequester(focus.chosen) else Modifier)
                                        .onFocusChanged { if (it.isFocused) chosenId = drive.id },
                                    selected = isSelected,
                                    tint = if (result.current) SportsLive.copy(alpha = 0.18f) else null,
                                    onClick = {
                                        chosenId = drive.id
                                        landing = if (drive.plays.isNotEmpty()) focus.firstPlay else focus.field
                                    },
                                ) { SportsDriveListRow(page, drive, explorer.number(drive), result) }
                            }
                        }
                    }
                }
            }
            Column(
                Modifier.weight(1.2f).height(height)
                    .focusProperties {
                        onEnter = { if (requestedFocusDirection != FocusDirection.Enter) runCatching { focus.field.requestFocus() } }
                    }
                    .focusGroup(),
            ) {
                SportsPanel(null, Modifier.fillMaxHeight()) {
                    if (selected != null) {
                        SportsDriveChart(
                            page, selected, explorer.number(selected), SportsDriveResult.of(selected, possession),
                            focus.field, focus.firstPlay, focus.chosen, highlighted, onHighlight = { highlighted = it },
                        )
                    } else {
                        SportsReadout(Modifier.focusRequester(focus.field)) {
                            Text("No drives supplied for this team.", fontSize = 14.sp, color = SportsMuted)
                        }
                    }
                }
            }
        }
    }
}

/** What takes focus in the drive explorer. */
private class DriveFocus {
    val chip = FocusRequester()
    val chosen = FocusRequester()
    val field = FocusRequester()
    val firstPlay = FocusRequester()
}

/**
 * Hands the page the whole explorer whenever something in it asks to be shown, so the page lines
 * the explorer up once (its foot at the screen's) instead of following each row; the columns
 * scroll their own rows.
 */
@OptIn(ExperimentalFoundationApi::class)
private class WholeView : BringIntoViewResponder {
    var size = Size.Zero

    override fun calculateRectForParent(localRect: Rect): Rect = Rect(Offset.Zero, size)

    override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
}
