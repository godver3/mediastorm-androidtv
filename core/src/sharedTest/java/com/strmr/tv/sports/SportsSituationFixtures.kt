package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import com.strmr.tv.ui.sports.game.SportsGamePage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Live football and baseball games for the situation tests (the tvOS `SportsSituationFixtures`):
 * this server's NFL final turned live (NE 3rd & 4 at the CHI 12, 0:41 left) and the Expo MLB final
 * turned live (Top 7th, 3-2, two outs, runners on 1st and 3rd). Detail stamped fresh unless
 * `stale`. Overrides are JSON members that replace the base's.
 */
object SportsSituationFixtures {
    const val FRESH = "2099-01-01T00:00:00Z"

    private const val NFL_LIVE = """"status":"live","statusDetail":"0:41 - 4th Quarter","period":"Q4","clock":"0:41",
        "footballSituation":{"kind":"nfl","possession":" NE ","downDistance":"3rd & 4","fieldPosition":"CHI 12",
                             "awayTimeouts":0,"homeTimeouts":1}"""

    fun nflLive(overrides: String = "", detail: String = "", stale: Boolean = false): SportsGamePage {
        val updated = """"updatedAt":"${if (stale) "2026-01-01T00:00:00Z" else FRESH}""""
        return SportsGamePage(SportsGameFixtures.game(merge(NFL_LIVE, overrides), SportsGameFixtures.nflDetail(merge(updated, detail))), null)
    }

    const val SITUATION = """{"kind":"mlb","inning":"Top 7th","balls":3,"strikes":2,"outs":2,"bases":"Runners on 1st & 3rd",
        "pitcher":"Jesus Luzardo","batter":"Ozzie Albies"}"""

    private val periods = (1..6).joinToString(",") { """{"label":"$it","away":"0","home":"0"}""" } + """,{"label":"7","away":"0","home":""}"""

    private val detail = """{"updatedAt":"$FRESH","stale":false,"capabilities":{"plays":true,"stats":true},
        "sport":${merge(SITUATION.trim().removeSurrounding("{", "}"), """"batterId":"33783","pitcherId":39667,"pitcherPitchCount":94""").let { "{$it}" }},
        "periods":[$periods],
        "comparisons":[{"label":"Hits","away":"2","home":"3"},{"label":"Errors","away":"0","home":"1"}],
        "playerStats":[
          {"id":"33783","teamId":"15","name":"Ozzie Albies","category":"Batting",
           "stats":[{"label":"AB","value":"3"},{"label":"H","value":"1"},{"label":"RBI","value":"0"}]},
          {"id":"39667","teamId":"22","name":"Jesus Luzardo","category":"Pitching",
           "stats":[{"label":"IP","value":"6.2"},{"label":"SO","value":"9"}]}]}"""

    /** The live MLB game as JSON members (no braces). */
    fun mlbMembers(overrides: String = ""): String = merge(
        """"id":"401816843","league":"mlb","sport":"baseball","startTime":"2026-10-23T17:05:00Z",
        "status":"live","statusDetail":"Top 7th","period":"Top 7th","clock":"0:00",
        "homeTeam":{"id":"22","name":"Philadelphia Phillies","abbreviation":"PHI","color":"e81828","score":"0"},
        "awayTeam":{"id":"15","name":"Atlanta Braves","abbreviation":"ATL","color":"0c2340","score":"0"},
        "liveSituation":$SITUATION,"detail":$detail""",
        overrides,
    )

    fun mlbGame(members: String): SportsGame = ApiJson.decodeFromString(SportsGame.serializer(), "{$members}")

    fun mlbLive(overrides: String = ""): SportsGamePage = SportsGamePage(mlbGame(mlbMembers(overrides)), null)

    /** The base situation with [overrides] replacing its members, as a JSON object. */
    fun situation(overrides: String): String = "{${merge(SITUATION.trim().removeSurrounding("{", "}"), overrides)}}"

    /** Two lists of JSON members merged, the second's replacing the first's. */
    fun merge(base: String, overrides: String): String {
        val json = ApiJson.parseToJsonElement("{$base}").jsonObject.toMutableMap()
        if (overrides.isNotBlank()) json.putAll(ApiJson.parseToJsonElement("{$overrides}").jsonObject)
        return JsonObject(json).toString().removeSurrounding("{", "}")
    }
}
