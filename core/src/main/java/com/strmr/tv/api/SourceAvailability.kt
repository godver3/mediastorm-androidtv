package com.strmr.tv.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Search evidence for the selected profile and playback intent, not a playback guarantee. */
@Serializable(with = SourceAvailabilitySerializer::class)
data class SourceAvailability(
    val state: String = "unknown",
    val checkedAt: String? = null,
    val expiresAt: String? = null,
    val providers: List<String> = emptyList(),
    val complete: Boolean = false,
    val reason: String? = null,
)

/** A malformed optional hint must not discard its otherwise usable catalogue title. */
object SourceAvailabilitySerializer : KSerializer<SourceAvailability> {
    override val descriptor = buildClassSerialDescriptor("SourceAvailability")
    override fun deserialize(decoder: Decoder): SourceAvailability {
        val obj = (decoder as JsonDecoder).decodeJsonElement() as? JsonObject
            ?: return SourceAvailability(reason = "invalid_source_hint")
        fun text(key: String): String? = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        val complete = (obj["complete"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true
        return SourceAvailability(
            state = text("state") ?: "unknown", checkedAt = text("checkedAt"), expiresAt = text("expiresAt"),
            providers = (obj["providers"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull } ?: emptyList(),
            complete = complete, reason = text("reason"),
        )
    }
    override fun serialize(encoder: Encoder, value: SourceAvailability) {
        (encoder as JsonEncoder).encodeJsonElement(buildJsonObject {
            put("state", value.state)
            value.checkedAt?.let { put("checkedAt", it) }
            value.expiresAt?.let { put("expiresAt", it) }
            put("providers", JsonArray(value.providers.map(::JsonPrimitive)))
            put("complete", value.complete)
            value.reason?.let { put("reason", it) }
        })
    }
}
