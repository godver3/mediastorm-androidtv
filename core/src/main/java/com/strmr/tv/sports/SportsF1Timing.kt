package com.strmr.tv.sports

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** A driver's line in an F1 timing archive (Expo `f1Drivers`); "" or null where nothing was reported. */
data class SportsF1Driver(
    /** The racing number, which keys a driver throughout the archive. */
    val id: String,
    val name: String,
    val abbreviation: String,
    val team: String,
    val position: Int?,
    val laps: Int?,
    val knockedOut: Boolean,
    /** The best laps of Q1, Q2 and Q3. */
    val qualifyingTimes: List<String>,
    val bestLap: String,
    val lastLap: String,
    val interval: String,
    val pitStops: Int?,
    val retired: Boolean,
    val stopped: Boolean,
    val gap: String,
    val inPit: Boolean,
    /** S1–S3 of the latest lap; empty when the archive has no sectors. */
    val sectors: List<String>,
    val stints: List<SportsF1Stint>,
) {
    /** What to flag about the car: retired, stopped, in the pit or knocked out. */
    val state: String?
        get() = when {
            retired -> "Retired"
            stopped -> "Stopped"
            inPit -> "In pit"
            knockedOut -> "Knocked out"
            else -> null
        }
}

/** A tyre stint: compound, the set's age at the end, and the laps run on it (a used set's earlier laps taken off). */
data class SportsF1Stint(val id: String, val compound: String, val age: Int?, val laps: Int?, val used: Boolean)

/** A race-control message. */
data class SportsF1Message(
    val id: String,
    val message: String,
    val lap: Int?,
    val utc: String,
    val flag: String,
    val category: String,
    val driver: String,
)

/** The qualifying part an archive ends in, and how many entries were still running in it. */
data class SportsF1Qualifying(val part: Int, val remaining: Int?)

/**
 * Reads an F1 timing archive's raw channels (Expo `sports-f1.ts`). The feed keys lists by
 * index or as arrays; both read the same. Missing values stay missing, never zero.
 */
object SportsF1Timing {
    fun drivers(archive: SportsF1Archive): List<SportsF1Driver> {
        val channels = archive.channels ?: return emptyList()
        val timing = fields(fields(channels["TimingData"])["Lines"])
        val app = fields(fields(channels["TimingAppData"])["Lines"])
        return fields(channels["DriverList"]).filter { (_, raw) -> truthy(fields(raw)["RacingNumber"]) }.map { (id, raw) ->
            val driver = fields(raw)
            val line = fields(timing[id])
            val sectors = fields(line["Sectors"])
            SportsF1Driver(
                id = id,
                name = text(driver["FullName"]),
                abbreviation = text(driver["Tla"]),
                team = text(driver["TeamName"]),
                position = number(line["Position"]),
                laps = number(line["NumberOfLaps"]),
                knockedOut = isTrue(line["KnockedOut"]),
                qualifyingTimes = (0..2).map { value(fields(line["BestLapTimes"])["$it"]) },
                bestLap = value(line["BestLapTime"]),
                lastLap = value(line["LastLapTime"]),
                interval = value(line["IntervalToPositionAhead"]),
                pitStops = number(line["NumberOfPitStops"]),
                retired = isTrue(line["Retired"]),
                stopped = isTrue(line["Stopped"]),
                gap = text(line["GapToLeader"]),
                inPit = isTrue(line["InPit"]),
                sectors = if (sectors.isEmpty()) emptyList() else (0..2).map { value(sectors["$it"]) },
                stints = fields(fields(app[id])["Stints"]).map { (key, raw) -> stint(key, fields(raw)) },
            )
        }.sortedBy { it.position ?: 999 }
    }

    /** Race-control messages, newest first. */
    fun messages(archive: SportsF1Archive): List<SportsF1Message> =
        fields(fields(archive.channels?.get("RaceControlMessages"))["Messages"]).map { (id, raw) ->
            val m = fields(raw)
            SportsF1Message(id, text(m["Message"]), number(m["Lap"]), text(m["Utc"]), text(m["Flag"]), text(m["Category"]), text(m["RacingNumber"]))
        }.filter { it.message.isNotEmpty() }
            .sortedWith(compareByDescending<SportsF1Message> { it.utc }.thenByDescending { it.id.toDoubleOrNull() ?: 0.0 })

    /** "Air 24°C · Track 38°C · No rainfall reported", or "" when not reported. */
    fun weather(archive: SportsF1Archive): String {
        val w = fields(archive.channels?.get("WeatherData"))
        return listOfNotNull(
            w["AirTemp"]?.takeIf { it !is JsonNull }?.let { "Air ${text(it)}°C" },
            w["TrackTemp"]?.takeIf { it !is JsonNull }?.let { "Track ${text(it)}°C" },
            when (text(w["Rainfall"])) {
                "1" -> "Rain reported"
                "0" -> "No rainfall reported"
                else -> null
            },
        ).joinToString(" · ")
    }

    /** The qualifying part a qualifying archive ends in (Expo `f1Qualifying`); null for other sessions. */
    fun qualifying(archive: SportsF1Archive): SportsF1Qualifying? {
        if (!archive.sessionName.orEmpty().contains("qualifying", ignoreCase = true)) return null
        val timing = fields(archive.channels?.get("TimingData"))
        val part = number(timing["SessionPart"])?.takeIf { it in 1..3 } ?: return null
        return SportsF1Qualifying(part, number(fields(timing["NoEntries"])["${part - 1}"]))
    }

    private fun stint(key: String, stint: Map<String, JsonElement>): SportsF1Stint {
        val total = number(stint["TotalLaps"])
        val before = number(stint["StartLaps"])
        val laps = if (total != null && before != null && total >= before) total - before else null
        return SportsF1Stint(key, text(stint["Compound"]).ifEmpty { "UNKNOWN" }, total, laps, before != null && before > 0)
    }

    /** An object's fields, or an array's items keyed by index (the feed sends either). */
    private fun fields(element: JsonElement?): Map<String, JsonElement> = when (element) {
        is JsonObject -> element
        is JsonArray -> element.withIndex().associate { (i, item) -> "$i" to item }
        else -> emptyMap()
    }

    /** A `{"Value": …}` reading's text. */
    private fun value(element: JsonElement?): String = text(fields(element)["Value"])

    /** A string or number as text; anything else (booleans included) is "". */
    private fun text(element: JsonElement?): String {
        val primitive = element as? JsonPrimitive ?: return ""
        if (primitive is JsonNull || !primitive.isString && primitive.booleanOrNull != null) return ""
        return primitive.content
    }

    /** A non-negative number, from a number or numeric text; nothing for blanks and booleans. */
    private fun number(element: JsonElement?): Int? {
        val raw = text(element).trim().ifEmpty { return null }
        return raw.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.toInt()
    }

    private fun isTrue(element: JsonElement?): Boolean = (element as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull == true } == true

    /** JavaScript truthiness of a primitive: a non-empty string, a non-zero number or true. */
    private fun truthy(element: JsonElement?): Boolean {
        val primitive = element as? JsonPrimitive ?: return false
        if (primitive is JsonNull) return false
        if (primitive.isString) return primitive.content.isNotEmpty()
        primitive.booleanOrNull?.let { return it }
        return primitive.content.toDoubleOrNull()?.let { it != 0.0 } == true
    }
}
