package com.strmr.mobile.ui.sports.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.mobile.ui.sports.LocalSportsCompact
import com.strmr.mobile.ui.sports.PageMargin
import com.strmr.mobile.ui.sports.SportsFaint
import com.strmr.mobile.ui.sports.SportsMuted
import com.strmr.mobile.ui.sports.SportsReadout
import com.strmr.mobile.ui.sports.SportsStreamPicker
import com.strmr.tv.api.ApiClient
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.sportsGame
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.tv.ui.sports.SportsSpoilers
import com.strmr.tv.ui.sports.game.SportsGameFormat
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsGameTab
import com.strmr.tv.ui.sports.game.SportsPlayerTarget
import com.strmr.tv.ui.sports.game.LocalSportsShowPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * A game's page (the TV's `SportsGameScreen`, the iPhone's `MobileSportsGameView`): the
 * scoreboard, Watch Live / Find Streams, Follow and Hide Scores, then the tabs the feed can
 * fill (Overview, the sport's own, Box Score, Play-by-Play, Standings) as a scrollable tab
 * row, the shown tab's panels and the feed's freshness. Multi-column panels stack on a phone.
 * With scores hidden there are no tabs. Refreshed every 30 s while live, 60 s otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsGameScreen(id: String) {
    var game by remember { mutableStateOf<SportsGame?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf(SportsGameTab.OVERVIEW) }
    val spoilers = SportsSpoilers.hidden
    // The Box Score's player opened from the Overview; dropped on leaving the Box Score or
    // toggling Hide Scores, so re-appearing tables don't apply it again.
    var playerTarget by remember { mutableStateOf<SportsPlayerTarget?>(null) }
    LaunchedEffect(picked) { if (picked != SportsGameTab.STATS) playerTarget = null }
    LaunchedEffect(spoilers) { playerTarget = null }

    LaunchedEffect(id) {
        while (true) {
            try {
                game = ApiClient.sportsGame(id)
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (game == null) error = e.message ?: "Couldn't load the game"
            }
            delay(if (game?.lifecycle == SportsLifecycle.LIVE) 30_000 else 60_000)
        }
    }

    PageScaffold(
        game?.displayTitle ?: "Game", showBack = true,
        actions = {
            IconButton(onClick = { SportsSpoilers.toggle() }) {
                Icon(if (spoilers) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                     contentDescription = if (spoilers) "Show Scores" else "Hide Scores")
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val current = game
            CompositionLocalProvider(LocalSportsCompact provides (maxWidth < 600.dp)) {
                when {
                    current != null -> {
                        val league = SportsHub.state.leagues[current.league]
                        val page = remember(current, league) { SportsGamePage(current, league) }
                        Page(page, spoilers, picked, playerTarget, onPick = { picked = it }, onWatch = { picking = true },
                             onShowPlayer = { playerTarget = it; picked = SportsGameTab.STATS })
                    }
                    error != null -> SportsReadout(Modifier.align(Alignment.Center).widthIn(max = 520.dp).padding(PageMargin)) {
                        Text(error.orEmpty(), color = SportsMuted)
                    }
                    else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }

    val current = game
    if (picking && current != null) {
        SportsStreamPicker(current, onPick = { match ->
            picking = false
            PlaybackCoordinator.playLive(match.channel, current.displayTitle)
        }, onDismiss = { picking = false })
    }
}

private fun canWatch(game: SportsGame) =
    game.lifecycle == SportsLifecycle.LIVE || game.lifecycle == SportsLifecycle.SCHEDULED || game.lifecycle == SportsLifecycle.DELAYED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(
    page: SportsGamePage,
    spoilers: Boolean,
    picked: SportsGameTab,
    playerTarget: SportsPlayerTarget?,
    onPick: (SportsGameTab) -> Unit,
    onWatch: () -> Unit,
    onShowPlayer: (SportsPlayerTarget) -> Unit,
) {
    val tabs = SportsGameTab.available(page, spoilers)
    val shown = SportsGameTab.shown(picked, tabs)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.padding(horizontal = PageMargin, vertical = 8.dp)) { SportsGameHeader(page, spoilers) }
        Actions(page, spoilers, onWatch)
        if (tabs.isNotEmpty()) {
            PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(shown).coerceAtLeast(0), edgePadding = PageMargin) {
                tabs.forEach { tab -> Tab(selected = tab == shown, onClick = { onPick(tab) }, text = { Text(tab.label(page.kind)) }) }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = PageMargin), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (spoilers) {
                SportsSpoilerOverview(page)
            } else {
                CompositionLocalProvider(LocalSportsShowPlayer provides onShowPlayer) { SportsGameContent(page, shown, playerTarget) }
                Text(SportsGameFormat.freshness(page, page.isStale()), fontSize = 11.sp, color = SportsFaint)
            }
        }
    }
}

/** Watch Live / Find Streams while there is something to watch, and Follow each team. */
@Composable
private fun Actions(page: SportsGamePage, spoilers: Boolean, onWatch: () -> Unit) {
    val game = page.game
    SportsChipRow(null, Modifier.padding(horizontal = PageMargin), centered = true) {
        if (canWatch(game)) {
            Button(onClick = onWatch) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(if (game.lifecycle == SportsLifecycle.LIVE) "Watch Live" else "Find Streams")
            }
        }
        SportsFollowButtons(game, page.kind)
        if (spoilers) OutlinedButton(onClick = { SportsSpoilers.toggle() }) { Text("Show Scores") }
    }
}
