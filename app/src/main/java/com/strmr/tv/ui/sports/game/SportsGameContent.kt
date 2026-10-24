package com.strmr.tv.ui.sports.game

import androidx.compose.runtime.Composable
import com.strmr.tv.ui.sports.game.baseball.SportsBaseballOverview
import com.strmr.tv.ui.sports.game.basketball.SportsBasketballOverview
import com.strmr.tv.ui.sports.game.combat.SportsCombatOverview
import com.strmr.tv.ui.sports.game.cricket.SportsCricketOverview
import com.strmr.tv.ui.sports.game.football.SportsDrivesPanel
import com.strmr.tv.ui.sports.game.football.SportsFootballOverview
import com.strmr.tv.ui.sports.game.golf.SportsGolfOverview
import com.strmr.tv.ui.sports.game.hockey.SportsHockeyOverview
import com.strmr.tv.ui.sports.game.rugby.SportsRugbyOverview
import com.strmr.tv.ui.sports.game.soccer.SportsLineupsPanel
import com.strmr.tv.ui.sports.game.soccer.SportsSoccerOverview
import com.strmr.tv.ui.sports.game.tennis.SportsTennisOverview

/**
 * The shown tab's panels: the one place each tab, and each sport's Overview, is chosen (tvOS
 * `SportsGameContent`). A sport's bespoke views plug in here: an Overview case in [Overview], or
 * a sport-specific tab view in the tab switch (the drives explorer for [SportsGameTab.DRIVES],
 * the pitch for [SportsGameTab.LINEUPS]).
 */
@Composable
fun SportsGameContent(page: SportsGamePage, tab: SportsGameTab, playerTarget: SportsPlayerTarget? = null) {
    when (tab) {
        SportsGameTab.OVERVIEW -> Overview(page)
        SportsGameTab.DRIVES -> SportsDrivesPanel(page)
        SportsGameTab.LINEUPS -> SportsLineupsPanel(page)
        SportsGameTab.STATS -> SportsBoxScore(page, playerTarget)
        SportsGameTab.PLAYS -> SportsPlayByPlay(page)
        SportsGameTab.STANDINGS -> SportsStandingsPanel(page)
    }
}

/**
 * A sport's Overview (Expo `SportsSportOverview`, and the football, baseball and basketball
 * overviews of `SportsGameDetailTV`): golf, cricket, combat, tennis, soccer and rugby have their
 * own too; the rest use the generic panels.
 */
@Composable
private fun Overview(page: SportsGamePage) {
    when {
        page.isFootballLeague -> SportsFootballOverview(page)
        page.game.league == "mlb" -> SportsBaseballOverview(page)
        page.kind == SportsKind.BASKETBALL -> SportsBasketballOverview(page)
        page.game.league == "nhl" -> SportsHockeyOverview(page)
        page.isIndividualGolf -> SportsGolfOverview(page)
        page.kind == SportsKind.TENNIS -> SportsTennisOverview(page)
        page.kind == SportsKind.CRICKET -> SportsCricketOverview(page)
        page.kind == SportsKind.MMA || page.kind == SportsKind.BOXING -> SportsCombatOverview(page)
        page.kind == SportsKind.SOCCER -> SportsSoccerOverview(page)
        page.kind.isRugby -> SportsRugbyOverview(page)
        else -> SportsGenericOverview(page)
    }
}
