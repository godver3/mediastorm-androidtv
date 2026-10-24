package com.strmr.tv.sports

import com.strmr.tv.api.LossyList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs

/**
 * One JSON object of a game's detail, read leniently (tvOS `sportsValue` / `sportsList` /
 * `sportsText`): a field that is missing, `null` or of another type reads as absent, and a list
 * skips (and logs, under `strmr.Decode`) the items it can't read. Only an item's required
 * fields ([require], [requireText]) throw, which skips that item, never the page.
 */
@JvmInline
internal value class SportsFields(val json: JsonObject) {
    private fun primitive(key: String) = json[key] as? JsonPrimitive

    /** Text; null when missing, `null` or not text. */
    fun string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content

    /** Text with something in it. */
    fun filled(key: String): String? = string(key)?.ifEmpty { null }

    fun bool(key: String): Boolean? = primitive(key)?.takeUnless { it.isString }?.booleanOrNull

    fun int(key: String): Int? = primitive(key)?.takeUnless { it.isString }?.intOrNull

    fun double(key: String): Double? = primitive(key)?.takeUnless { it.isString }?.doubleOrNull

    /** A figure the feed may send as text or as a number ("12" or 12). */
    fun text(key: String): String? {
        val value = primitive(key) ?: return null
        if (value.isString) return value.content
        value.longOrNull?.let { return it.toString() }
        val number = value.doubleOrNull ?: return null
        return if (number % 1.0 == 0.0 && abs(number) < 1e15) number.toLong().toString() else number.toString()
    }

    /** Text the item can't do without. */
    fun require(key: String): String = string(key) ?: throw SerializationException("missing $key")

    /** A figure the item can't do without, as text. */
    fun requireText(key: String): String = text(key) ?: throw SerializationException("missing $key")

    /** An object; null when missing, not an object, or missing what it can't do without. */
    fun <T> item(key: String, read: (SportsFields) -> T): T? {
        val value = json[key] as? JsonObject ?: return null
        return try {
            read(SportsFields(value))
        } catch (e: IllegalArgumentException) {
            LossyList.report("skipped $key: ${e.message}")
            null
        }
    }

    /** A list; empty when missing, `null` or not a list, without the items it can't read. */
    fun <T> list(key: String, read: (SportsFields) -> T): List<T> {
        val array = json[key] as? JsonArray ?: return emptyList()
        return array.mapIndexedNotNull { index, item ->
            try {
                read(SportsFields(item as? JsonObject ?: throw SerializationException("not an object")))
            } catch (e: IllegalArgumentException) {
                // SerializationException is an IllegalArgumentException.
                LossyList.report("skipped $key #$index: ${e.message}")
                null
            }
        }
    }
}

/**
 * A game's `detail`, read with [readGameDetail]: anything but an object reads as the empty
 * detail, so the game itself always decodes. Decode only; the app never sends a game.
 */
internal object SportsGameDetailSerializer : KSerializer<SportsGameDetail> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.strmr.tv.sports.SportsGameDetail")

    override fun deserialize(decoder: Decoder): SportsGameDetail {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: throw SerializationException("JSON only")
        return (element as? JsonObject)?.let { readGameDetail(SportsFields(it)) } ?: SportsGameDetail()
    }

    override fun serialize(encoder: Encoder, value: SportsGameDetail): Unit = throw SerializationException("decode only")
}
