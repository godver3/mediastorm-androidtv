package com.strmr.tv.sports

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

// The live situation a game carries while it is played (Go `models.SportsFootballSituation` and
// `models.SportsMLBSituation`, Expo `SportsSituation`, tvOS `SportsSituationModels`): American
// football's possession, down and distance (`footballSituation` on NFL and college games) and
// baseball's at-bat (`liveSituation` on MLB games, `detail.sport` in the game's detail, which adds
// the players' ids and the pitch count). Read leniently: a missing, `null` or odd field is empty.

/**
 * NFL and college football's live situation (Expo `SportsSituation` kind `nfl`): who has the ball
 * ("NE", an id or a name), "3rd & 4", "CHI 12", and each side's timeouts left (null: not reported,
 * never read as none left).
 */
data class SportsFootballSituation(
    val possession: String = "",
    val downDistance: String = "",
    val fieldPosition: String = "",
    val awayTimeouts: Int? = null,
    val homeTimeouts: Int? = null,
) {
    /** The side with the ball: the feed names it by id, abbreviation or name, in any case. */
    fun offense(game: SportsGame): SportsTeam? = game.side(possession)

    /** "Ball at CHI 12" (Expo's scoreboard), null when the spot isn't reported. */
    val ballAt: String? get() = fieldPosition.ifEmpty { null }?.let { "Ball at $it" }

    /** A side's timeouts left, 0 to 3; null when not reported or out of range. */
    fun timeouts(team: SportsTeam, game: SportsGame): Int? =
        (if (team.id == game.homeTeam.id) homeTimeouts else awayTimeouts)?.takeIf { it in 0..3 }
}

internal fun readFootballSituation(f: SportsFields) = SportsFootballSituation(
    possession = f.string("possession")?.trim().orEmpty(),
    downDistance = f.string("downDistance")?.trim().orEmpty(),
    fieldPosition = f.string("fieldPosition")?.trim().orEmpty(),
    awayTimeouts = f.int("awayTimeouts"),
    homeTimeouts = f.int("homeTimeouts"),
)

/**
 * MLB's live at-bat (Expo `SportsSituation` kind `mlb`): the half-inning ("Top 7th"), the count,
 * outs, the runners ("Runners on 1st & 3rd"), and who pitches to whom. The page's detail adds the
 * players' box-score ids and the pitcher's pitch count.
 */
data class SportsBaseballSituation(
    val inning: String = "",
    val balls: Int? = null,
    val strikes: Int? = null,
    val outs: Int? = null,
    val bases: String? = null,
    val pitcher: String? = null,
    val pitcherId: String? = null,
    val pitcherPitchCount: Int? = null,
    val batter: String? = null,
    val batterId: String? = null,
) {
    /**
     * Which of first, second and third are taken (Expo `baseballBases`): "Bases empty" or
     * "Runners on 1st & 3rd" style labels only; null when the runners aren't reported plainly.
     */
    val occupiedBases: List<Boolean>?
        get() {
            val bases = bases ?: return null
            if (bases == "Bases empty") return listOf(false, false, false)
            if (!PlainBases.matches(bases)) return null
            return listOf("1st", "2nd", "3rd").map { it in bases }
        }

    private companion object {
        val PlainBases = Regex("""^(?:Runner on |Runners on |On )?(?:1st|2nd|3rd)(?: & (?:1st|2nd|3rd))*$""")
    }
}

internal fun readBaseballSituation(f: SportsFields) = SportsBaseballSituation(
    inning = f.string("inning").orEmpty(),
    balls = f.int("balls"),
    strikes = f.int("strikes"),
    outs = f.int("outs"),
    bases = f.filled("bases"),
    pitcher = f.text("pitcher")?.ifEmpty { null },
    pitcherId = f.text("pitcherId")?.ifEmpty { null },
    pitcherPitchCount = f.int("pitcherPitchCount"),
    batter = f.text("batter")?.ifEmpty { null },
    batterId = f.text("batterId")?.ifEmpty { null },
)

/** A game's `footballSituation`: anything but an object reads as none. Decode only. */
internal object SportsFootballSituationSerializer : SportsSituationSerializer<SportsFootballSituation>(
    "com.strmr.tv.sports.SportsFootballSituation", ::readFootballSituation,
)

/** A game's `liveSituation` (MLB): anything but an object reads as none. Decode only. */
internal object SportsBaseballSituationSerializer : SportsSituationSerializer<SportsBaseballSituation>(
    "com.strmr.tv.sports.SportsBaseballSituation", ::readBaseballSituation,
)

internal abstract class SportsSituationSerializer<T : Any>(name: String, private val read: (SportsFields) -> T) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(name)

    override fun deserialize(decoder: Decoder): T? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: throw SerializationException("JSON only")
        return (element as? JsonObject)?.let { read(SportsFields(it)) }
    }

    override fun serialize(encoder: Encoder, value: T?): Unit = throw SerializationException("decode only")
}

/** The side the feed names by id, abbreviation or name, in any case. */
fun SportsGame.side(key: String): SportsTeam? {
    val wanted = key.lowercase()
    if (wanted.isEmpty()) return null
    return listOf(awayTeam, homeTeam).firstOrNull { team ->
        listOf(team.id, team.abbreviation.orEmpty(), team.name).any { it.lowercase() == wanted }
    }
}

/** The live football situation: NFL and college football while live only, as the Expo normaliser keeps it. */
val SportsGame.liveFootballSituation: SportsFootballSituation?
    get() = if (lifecycle == SportsLifecycle.LIVE && (league == "nfl" || league == "college-football")) footballSituation else null

/** The live at-bat for MLB while live: the detail's, with the players' ids, before the scoreboard's. */
val SportsGame.liveBaseballSituation: SportsBaseballSituation?
    get() = if (lifecycle == SportsLifecycle.LIVE && league == "mlb") detail?.sport ?: liveSituation else null

/**
 * The period as the page shows it (Expo `periodLabel`): none before the start, a live MLB game's
 * half-inning from its at-bat, else the feed's period.
 */
val SportsGame.periodLabel: String?
    get() = when {
        lifecycle == SportsLifecycle.SCHEDULED -> null
        lifecycle == SportsLifecycle.LIVE && league == "mlb" -> liveSituation?.inning?.ifEmpty { null } ?: period
        else -> period
    }
