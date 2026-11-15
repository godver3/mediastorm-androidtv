package com.strmr.tv.ui.lists

import com.strmr.tv.library.ProfileDataContext
import com.strmr.tv.library.optionalProfileRead

/** Shared by main-thread list tiles and background Home hub loads. */
internal class ProfilePreviewCache(
    private val context: () -> Long = { ProfileDataContext.current },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val capacity: Int = 128,
    private val maxAgeMs: Long = 300_000,
) {
    private data class Key(val context: Long, val list: String, val version: Int?)
    private data class Entry(val art: TileArt, val created: Long)
    private val lock = Any()
    private val items = LinkedHashMap<Key, Entry>(16, 0.75f, true)
    private var generation = 0L

    init { require(capacity > 0 && maxAgeMs > 0) }

    fun reset() = synchronized(lock) { generation++; items.clear() }

    suspend fun art(list: String, version: Int?, load: suspend () -> TileArt?): TileArt {
        val captured = context()
        val key = Key(captured, list, version)
        val stamp = synchronized(lock) {
            if (context() != captured) return TileArt()
            items.entries.removeAll { it.key.context != captured || now() - it.value.created >= maxAgeMs }
            items[key]?.let { return it.art }
            generation
        }
        val loaded = optionalProfileRead(load) ?: return TileArt()
        return synchronized(lock) {
            if (context() != captured || generation != stamp) return TileArt()
            items[key] = Entry(loaded, now())
            while (items.size > capacity) items.remove(items.keys.first())
            loaded
        }
    }
}
