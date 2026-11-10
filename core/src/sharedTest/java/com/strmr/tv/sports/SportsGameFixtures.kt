package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import com.strmr.tv.ui.sports.game.SportsGamePage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Small `/api/sports/game/{id}` answers for the game page tests, cut from this server's NFL final
 * (401873010) and the Expo recorded fixtures (the same excerpts as the tvOS `SportsGameFixtures`).
 * [game]'s overrides are JSON members (`"league":"nhl","sport":"hockey"`) that replace the base's.
 */
object SportsGameFixtures {
    private const val BASE = """{"id":"401873010","league":"nfl","sport":"football","title":"New England Patriots at Chicago Bears",
        "startTime":"2026-10-23T00:15:00Z","endTime":"0001-01-01T00:00:00Z","status":"final","statusDetail":"Final",
        "period":"4th quarter","clock":"0:00","venue":"Soldier Field","broadcasts":["Prime Video"],
        "homeTeam":{"id":"3","name":"Chicago Bears","abbreviation":"CHI","color":"0b1c3a","alternateColor":"e64100",
                    "score":"30","winner":true,"record":"5-2"},
        "awayTeam":{"id":"17","name":"New England Patriots","abbreviation":"NE","color":"002a5c","alternateColor":"c60c30",
                    "score":"24","record":"4-3"}}"""

    fun game(overrides: String = "", detail: String? = null): SportsGame {
        val json = ApiJson.parseToJsonElement(BASE).jsonObject.toMutableMap()
        if (overrides.isNotEmpty()) json.putAll(ApiJson.parseToJsonElement("{$overrides}").jsonObject)
        if (detail != null) json["detail"] = ApiJson.parseToJsonElement(detail)
        return ApiJson.decodeFromJsonElement(SportsGame.serializer(), JsonObject(json))
    }

    fun page(overrides: String = "", detail: String? = null, league: SportsLeague? = null) = SportsGamePage(game(overrides, detail), league)

    fun league(json: String): SportsLeague = ApiJson.decodeFromString(SportsLeague.serializer(), json)

    fun detail(json: String): SportsGameDetail = ApiJson.decodeFromString(SportsGameDetailSerializer, json)

    /** [NFL_DETAIL] with some of its members replaced (`"stale":true`). */
    fun nflDetail(overrides: String): String {
        val json = ApiJson.parseToJsonElement(NFL_DETAIL).jsonObject.toMutableMap()
        json.putAll(ApiJson.parseToJsonElement("{$overrides}").jsonObject)
        return JsonObject(json).toString()
    }

    /** The NFL final's detail, trimmed: two drives, a few plays and stat lines, and a bad play. */
    const val NFL_DETAIL = """{
        "source":"espn","updatedAt":"2026-10-23T08:21:38.788755-06:00","stale":false,
        "capabilities":{"plays":true,"stats":true,"winProbability":false},
        "periods":[{"label":"Q1","away":"0","home":"10"},{"label":"Q2","away":14,"home":10}],
        "comparisons":[{"label":"Total yards","away":"380","home":"405"},{"label":"Third downs","away":"2-7","home":"4-12"}],
        "leaders":[{"athleteId":"4431611","label":"Passing Yards","name":"Caleb Williams","teamAbbreviation":"CHI",
                    "value":"22/33, 308 YDS, 2 TD"}],
        "plays":[
          {"id":"1","periodLabel":"Q4","clock":"0:00","title":"End of Game","description":"END GAME","scoring":false},
          {"id":"2","periodLabel":"Q4","clock":"0:37","title":"Field Goal Good","teamId":"17","scoring":true},
          {"id":"3","periodLabel":"Q1","title":"Kickoff"},
          {"periodLabel":"Q1","title":"no id: skipped"}],
        "playerStats":[
          {"id":"4431452","teamId":"17","name":"Drake Maye","category":"Passing","jersey":"10",
           "stats":[{"label":"C/ATT","value":"24/31"},{"label":"YDS","value":"321"},{"label":"SACK-YDS","value":"2-11"}]},
          {"id":"4431452","teamId":"17","name":"Drake Maye","category":"Rushing",
           "stats":[{"label":"CAR","value":"4"},{"label":"YDS","value":"26"}]},
          {"id":"1","teamId":"17","name":"Rhamondre Stevenson","category":"Rushing",
           "stats":[{"label":"CAR","value":"12"},{"label":"YDS","value":"54"},{"label":"TD","value":"1"}]},
          {"id":"4431611","teamId":"3","name":"Caleb Williams","category":"Passing","stats":[{"label":"YDS","value":"308"}]},
          {"id":"2","teamId":"3","name":"No Stats","category":"Defense","stats":null}],
        "footballDrives":[
          {"id":"4018730101","teamId":"17","start":31,"end":55,"startLabel":"NE 31","endLabel":"CHI 45","period":"Q1",
           "result":"DOWNS","description":"6 plays, 23 yards, 3:30","current":false,"playCount":6,"yards":23,"elapsed":"3:30",
           "plays":[{"id":"40187301040","type":"Kickoff","description":"C.Santos kicks 60 yards","clock":"15:00","period":"Q1",
                     "start":65,"end":31,"scoring":false,"down":0,"yards":26}]},
          {"id":"4018730102","teamId":"3","start":45,"end":100,"startLabel":"CHI 45","endLabel":"NE 0","period":"Q1",
           "result":"TD","description":"8 plays, 55 yards, 4:01","current":false}],
        "pregame":{"seasonLabel":"2026 · Regular season","updatedAt":"2026-10-23T08:21:38.79716-06:00",
                   "teams":[{"teamId":"17"},{"teamId":"3"}]}}"""
}
