package com.strmr.tv.api

import com.strmr.tv.app.AppLog
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * A JSON array decoded item by item: an item that fails to decode is skipped (and logged once,
 * under `strmr.Decode`, with its type and index) instead of failing the whole list, and a `null`
 * or missing array decodes as empty. Go encodes an empty slice as `null`, so a list endpoint
 * with nothing to return (a Continue Watching emptied by a hide) answers `null`.
 * Mirrors the tvOS client's `LossyArray`.
 */
object LossyList {
    const val LOG = "Decode"

    /** Where skipped items are reported; tests swap it for a collector (no android.util.Log off-device). */
    @Volatile var report: (String) -> Unit = { AppLog.w(LOG, it) }

    fun <T> decode(json: Json, element: KSerializer<T>, raw: String): List<T> =
        decode(json, element, json.parseToJsonElement(raw))

    fun <T> decode(json: Json, element: KSerializer<T>, value: JsonElement?): List<T> {
        val array = when (value) {
            null, JsonNull -> return emptyList()
            is JsonArray -> value
            // Not a list at all: the whole response is unusable, as a strict decode would say.
            else -> throw IllegalArgumentException("expected a JSON array for ${element.typeName}, got ${value::class.simpleName}")
        }
        val out = ArrayList<T>(array.size)
        array.forEachIndexed { index, item ->
            try {
                out += json.decodeFromJsonElement(element, item)
            } catch (e: IllegalArgumentException) {
                // SerializationException is an IllegalArgumentException.
                report("skipped ${element.typeName} #$index: ${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}")
            }
        }
        return out
    }

    private val KSerializer<*>.typeName: String
        get() = descriptor.serialName.substringAfterLast('.').removeSuffix("?")
}

/**
 * A lossy list field: `@Serializable(with = LossyListSerializer::class) val items: List<T> = emptyList()`.
 * A JSON `null` there is coerced to the field's default by the app's [ApiJson] (`coerceInputValues`).
 */
class LossyListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val strict = ListSerializer(element)
    override val descriptor = strict.descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val input = decoder as? JsonDecoder ?: return strict.deserialize(decoder)
        return LossyList.decode(input.json, element, input.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: List<T>) = strict.serialize(encoder, value)
}
