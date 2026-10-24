package com.strmr.tv.ui.sports.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.api.ApiClient
import com.strmr.tv.player.PlaybackCoordinator
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.sports.sportsGame
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.PageMargin
import com.strmr.tv.ui.WithFoldScroll
import com.strmr.tv.ui.pageBackground
import com.strmr.tv.ui.sports.SportsHub
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsSpoilers
import com.strmr.tv.ui.sports.SportsStreamPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * A game's page (Expo `app/sports/[eventId].tsx` with `SportsGameDetailTV`, tvOS `SportsGameView`):
 * the scoreboard, Watch Live / Find Streams and Hide Scores, then the tabs the feed can fill
 * (Overview, the sport's own, Box Score, Play-by-Play, Standings), the shown tab's panels and the
 * feed's freshness. The tab picked is kept while the page is open. With scores hidden there are
 * no tabs: only what was known before the start shows. Refreshed every 30 s while live, 60 s
 * otherwise. Focus anywhere on the first screen shows it from the top, scoreboard included;
 * Back leaves the page.
 */
@Composable
fun SportsGameScreen(id: String) {
    var game by remember { mutableStateOf<SportsGame?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf(SportsGameTab.OVERVIEW) }
    val spoilers = SportsSpoilers.hidden
    val focus = remember { SportsGameFocus() }
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
    // Watch when there is something to watch, else the Overview tab (Expo's default focus).
    val loaded = game != null
    LaunchedEffect(loaded) {
        val current = game ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { (if (canWatch(current)) focus.watch else if (SportsSpoilers.hidden) focus.spoilers else focus.tab).requestFocus() }
    }

    Box(Modifier.fillMaxSize().pageBackground()) {
        val current = game
        when {
            current != null -> {
                val league = SportsHub.state.leagues[current.league]
                val page = remember(current, league) { SportsGamePage(current, league) }
                Page(
                    page, spoilers, picked, focus, playerTarget, onPick = { picked = it }, onWatch = { picking = true },
                    onShowPlayer = { playerTarget = it; picked = SportsGameTab.STATS },
                )
            }
            error != null -> LoadError(error.orEmpty(), Modifier.align(Alignment.Center))
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 2.dp)
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

/**
 * The load error ("game not found": a fight card's bout the server doesn't list), focused so the
 * page has a focused element and Back pops to where it was opened from.
 */
@Composable
private fun LoadError(message: String, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    SportsReadout(modifier.widthIn(max = 520.dp).focusRequester(focus)) { Text(message, color = SportsMuted) }
}

/** What takes focus in a game page's controls (tvOS `SportsGameFocus`); [tab] is the shown tab's chip. */
private class SportsGameFocus {
    val watch = FocusRequester()
    val spoilers = FocusRequester()
    val tab = FocusRequester()
}

private fun canWatch(game: SportsGame) =
    game.lifecycle == SportsLifecycle.LIVE || game.lifecycle == SportsLifecycle.SCHEDULED || game.lifecycle == SportsLifecycle.DELAYED

@Composable
private fun Page(
    page: SportsGamePage,
    spoilers: Boolean,
    picked: SportsGameTab,
    focus: SportsGameFocus,
    playerTarget: SportsPlayerTarget?,
    onPick: (SportsGameTab) -> Unit,
    onWatch: () -> Unit,
    onShowPlayer: (SportsPlayerTarget) -> Unit,
) {
    val tabs = SportsGameTab.available(page, spoilers)
    val shown = SportsGameTab.shown(picked, tabs)
    val scroll = rememberScrollState()
    WithFoldScroll(scroll) { _ ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = PageMargin, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SportsGameHeader(page, spoilers)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Actions(page.game, spoilers, focus, onWatch)
                if (tabs.isNotEmpty()) Tabs(tabs, page.kind, shown, focus.tab, onPick)
            }
            if (spoilers) {
                SportsSpoilerOverview(page)
            } else {
                CompositionLocalProvider(LocalSportsShowPlayer provides onShowPlayer) { SportsGameContent(page, shown, playerTarget) }
                Text(SportsGameFormat.freshness(page, page.isStale()), fontSize = 11.sp, color = SportsFaint)
            }
        }
    }
}

/** Watch Live / Find Streams while there is something to watch, and Hide / Show Scores. */
@Composable
private fun Actions(game: SportsGame, spoilers: Boolean, focus: SportsGameFocus, onWatch: () -> Unit) {
    SportsChipRow(null, centered = true) {
        if (canWatch(game)) {
            Button(onClick = onWatch, modifier = Modifier.focusRequester(focus.watch)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (game.lifecycle == SportsLifecycle.LIVE) "Watch Live" else "Find Streams")
            }
        }
        OutlinedButton(onClick = { SportsSpoilers.toggle() }, modifier = Modifier.focusRequester(focus.spoilers)) {
            Icon(if (spoilers) AppIcons.Visible else AppIcons.Hidden, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (spoilers) "Show Scores" else "Hide Scores")
        }
    }
}

/** The page's tabs as a row of chips (the hub's filters): Select shows a tab; moving up from the panels lands on the shown one. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Tabs(tabs: List<SportsGameTab>, kind: SportsKind, shown: SportsGameTab, focus: FocusRequester, onPick: (SportsGameTab) -> Unit) {
    SportsChipRow(focus, centered = true) {
        tabs.forEach { tab ->
            FilterChip(
                selected = tab == shown, onClick = { onPick(tab) },
                modifier = if (tab == shown) Modifier.focusRequester(focus) else Modifier,
            ) { Text(tab.label(kind)) }
        }
    }
}
