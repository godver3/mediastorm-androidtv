package com.strmr.tv.live

import com.strmr.tv.api.ApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A recurring recording (`/api/live/recording-rules`): a title (or regex) the server matches
 * against the guide, on one channel or all of them, every airing or new episodes only.
 */
@Serializable
data class SeriesRule(
    val id: String,
    val matchType: String = "title",
    val pattern: String = "",
    val channelId: String? = null,
    val tvgId: String? = null,
    val channelName: String? = null,
    val allChannels: Boolean = false,
    val enabled: Boolean = true,
    val newOnly: Boolean = false,
) {
    val isRegex get() = matchType == "regex"

    /** Mirrors the server's rule expansion (`ruleMatchesChannel` + `matchesRecordingRule`). */
    fun covers(title: String, channel: LiveChannel): Boolean {
        val onChannel = allChannels ||
            (!channelId.isNullOrEmpty() && channelId.equals(channel.id, ignoreCase = true)) ||
            (!tvgId.isNullOrEmpty() && tvgId.equals(channel.tvgId, ignoreCase = true))
        if (!onChannel) return false
        if (!isRegex) return normalizedTitle(pattern) == normalizedTitle(title)
        // The server prefixes (?i); an explicit (?-i) in the pattern still wins.
        return runCatching { Regex("(?i)$pattern").containsMatchIn(title) }.getOrDefault(false)
    }

    /** "\"Pattern\" on Channel" / "\"Pattern\" on all channels". */
    val summary get() = "\"$pattern\" on ${if (allChannels) "all channels" else channelName ?: "this channel"}"

    companion object {
        /** Lower-cased letters and digits, every other run one space (the server's `normalizeRecordingTitle`). */
        fun normalizedTitle(value: String): String {
            val out = StringBuilder()
            for (c in value.lowercase()) {
                if (c.isLetterOrDigit()) out.append(c) else if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
            }
            return out.toString().trim()
        }

        /** Of [rules], the one recording [title] on [channel]: a title rule first, then a regex rule. */
        fun find(rules: List<SeriesRule>, title: String, channel: LiveChannel): SeriesRule? {
            val matches = rules.filter { it.covers(title, channel) }
            return matches.firstOrNull { !it.isRegex } ?: matches.firstOrNull()
        }
    }
}

@Serializable
private data class SeriesRulesResponse(val rules: List<SeriesRule>? = null)

suspend fun ApiClient.recordingRules(): List<SeriesRule> =
    send<SeriesRulesResponse>("GET", "/api/live/recording-rules", mapOf("profileId" to profileId)).rules.orEmpty()

/** Deletes a rule; the server cancels the airings it scheduled (one recording now keeps going). */
suspend fun ApiClient.deleteRecordingRule(id: String) {
    sendRaw("DELETE", "/api/live/recording-rules/${segment(id)}")
}

/** Switches a rule between every airing and new episodes only; the server reschedules its airings. */
suspend fun ApiClient.setRecordingRuleNewOnly(id: String, newOnly: Boolean) {
    sendRaw("PUT", "/api/live/recording-rules/${segment(id)}", body = buildJsonObject {
        put("profileId", profileId)
        put("newOnly", newOnly)
    })
}
