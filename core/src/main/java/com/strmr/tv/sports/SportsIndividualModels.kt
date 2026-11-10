package com.strmr.tv.sports

import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.LossyList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// The individual and other sports' data (Go `models.SportsGolfEntry`, `SportsCricketInnings`,
// `SportsCricketScorecard`, the fight card of `SportsCombat`, Expo `sports-model.ts`, tvOS
// `SportsIndividualModels`): golf's leaderboard, cricket's innings and player scorecards, a fight
// card's bouts, and a game's listed participants (golf's field on the scoreboard). Read leniently:
// a missing, `null` or malformed field is empty and a bad list item is skipped, never the page.

/** A golfer on the leaderboard: the tournament score to par and each round's strokes and holes. */
data class SportsGolfEntry(val id: String, val name: String, val score: String? = null, val rounds: List<SportsGolfRound> = emptyList()) {
    /** The latest round played ("R4"), for the hub card. */
    val latestRound: Int? get() = rounds.maxOfOrNull { it.round }
}

internal fun readGolfEntry(f: SportsFields) = SportsGolfEntry(
    id = f.require("id"),
    name = f.require("name"),
    score = f.text("score")?.ifEmpty { null },
    rounds = f.list("rounds", ::readGolfRound),
)

/** One round: strokes, score to par and the holes played. */
data class SportsGolfRound(val round: Int, val strokes: String? = null, val toPar: String? = null, val holes: List<SportsGolfHole> = emptyList())

private fun readGolfRound(f: SportsFields) = SportsGolfRound(
    round = f.int("round") ?: throw SerializationException("missing round"),
    strokes = f.text("strokes")?.ifEmpty { null },
    toPar = f.text("toPar")?.ifEmpty { null },
    holes = f.list("holes") {
        SportsGolfHole(it.int("hole") ?: throw SerializationException("missing hole"), it.text("strokes").orEmpty(), it.text("toPar")?.ifEmpty { null })
    },
)

data class SportsGolfHole(val hole: Int, val strokes: String, val toPar: String? = null)

/**
 * A cricket innings: the side batting, its order in the match, runs, wickets, overs, the target
 * being chased, and whether the feed says it is the one in progress ([batting]).
 */
data class SportsCricketInnings(
    val teamId: String,
    val number: Int,
    val score: String? = null,
    val target: Int? = null,
    val batting: Boolean? = null,
    val runs: Int? = null,
    val wickets: Int? = null,
    /** Text or a number in the feed ("15", "18.4"). */
    val overs: String? = null,
    val description: String? = null,
)

internal fun readCricketInnings(f: SportsFields) = SportsCricketInnings(
    teamId = f.require("teamId"),
    number = f.int("number") ?: throw SerializationException("missing number"),
    score = f.filled("score"),
    target = f.int("target"),
    batting = f.bool("batting"),
    runs = f.int("runs"),
    wickets = f.int("wickets"),
    overs = f.text("overs")?.ifEmpty { null },
    description = f.filled("description"),
)

/** One side's batting or bowling card for an innings. */
data class SportsCricketScorecard(
    val kind: Kind,
    val innings: Int,
    val teamId: String? = null,
    val teamName: String = "",
    val extras: String? = null,
    val players: List<SportsCricketPlayer> = emptyList(),
) {
    enum class Kind { BATTING, BOWLING }
}

internal fun readCricketScorecard(f: SportsFields) = SportsCricketScorecard(
    kind = when (f.string("kind")) {
        "batting" -> SportsCricketScorecard.Kind.BATTING
        "bowling" -> SportsCricketScorecard.Kind.BOWLING
        else -> throw SerializationException("unknown kind ${f.string("kind")}")
    },
    innings = f.int("innings") ?: throw SerializationException("missing innings"),
    teamId = f.string("teamId"),
    teamName = f.string("teamName").orEmpty(),
    extras = f.filled("extras"),
    players = f.list("players") {
        SportsCricketPlayer(it.require("id"), it.require("name"), it.string("headshotUrl"), it.filled("dismissal"), it.list("stats", ::readStat))
    },
)

data class SportsCricketPlayer(
    val id: String,
    val name: String,
    val headshotUrl: String? = null,
    val dismissal: String? = null,
    val stats: List<SportsStat> = emptyList(),
) {
    fun value(label: String): String? = stats.firstOrNull { it.label == label }?.value
}

/** A fight card (or a bout of one): its name, the bout's division and scheduled rounds, and every bout on the card in the feed's order. */
data class SportsCombat(
    val cardName: String? = null,
    val division: String? = null,
    val scheduledRounds: Int? = null,
    val bouts: List<SportsCombatBout> = emptyList(),
)

private fun readCombat(f: SportsFields) = SportsCombat(
    cardName = f.filled("cardName"),
    division = f.filled("division"),
    scheduledRounds = f.int("scheduledRounds"),
    bouts = f.list("bouts", ::readCombatBout),
)

/** A bout on a card: both fighters, the division, its state and when it ended. */
data class SportsCombatBout(
    val id: String,
    val away: SportsTeam,
    val home: SportsTeam,
    val division: String? = null,
    val status: String = "scheduled",
    val period: String? = null,
    val clock: String? = null,
) {
    val lifecycle: SportsLifecycle get() = SportsLifecycle.of(status, null)
}

private fun readCombatBout(f: SportsFields) = SportsCombatBout(
    id = f.require("id"),
    away = f.team("away"),
    home = f.team("home"),
    division = f.filled("division"),
    status = f.string("status") ?: "scheduled",
    period = f.filled("period"),
    clock = f.filled("clock"),
)

/** A side the bout can't do without, read as the scoreboard's team. */
private fun SportsFields.team(key: String): SportsTeam {
    val value = json[key] as? JsonObject ?: throw SerializationException("missing $key")
    return ApiJson.decodeFromJsonElement(SportsTeam.serializer(), value)
}

/** A player, fighter or team the scoreboard lists for an event, in its order (golf's field with each golfer's score to par). */
data class SportsParticipant(
    val id: String,
    val name: String,
    val abbreviation: String? = null,
    val score: String? = null,
    val position: Int? = null,
    val winner: Boolean? = null,
)

private fun readParticipant(f: SportsFields) = SportsParticipant(
    id = f.text("id").orEmpty(),
    name = f.string("name").orEmpty(),
    abbreviation = f.string("abbreviation"),
    score = f.text("score")?.ifEmpty { null },
    position = f.int("position"),
    winner = f.bool("winner"),
)

/** A game's `combat`: anything but an object reads as none. Decode only. */
internal object SportsCombatSerializer : SportsSituationSerializer<SportsCombat>("com.strmr.tv.sports.SportsCombat", ::readCombat)

/** A game's `participants`: a list where a malformed or nameless one is skipped; anything but a list reads as none. Decode only. */
internal object SportsParticipantsSerializer : KSerializer<List<SportsParticipant>?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.strmr.tv.sports.SportsParticipants")

    override fun deserialize(decoder: Decoder): List<SportsParticipant>? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: throw SerializationException("JSON only")
        return (element as? JsonArray)?.let { readItems(it, "participants", ::readParticipant) }?.filter { it.name.isNotEmpty() }
    }

    override fun serialize(encoder: Encoder, value: List<SportsParticipant>?): Unit = throw SerializationException("decode only")
}

/** The objects of [array] read with [read], skipping (and reporting) the ones it can't read. */
private fun <T> readItems(array: JsonArray, name: String, read: (SportsFields) -> T): List<T> =
    array.mapIndexedNotNull { index, item: JsonElement ->
        try {
            read(SportsFields(item as? JsonObject ?: throw SerializationException("not an object")))
        } catch (e: IllegalArgumentException) {
            LossyList.report("skipped $name #$index: ${e.message}")
            null
        }
    }
