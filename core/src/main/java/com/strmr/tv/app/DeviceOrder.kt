package com.strmr.tv.app

/**
 * This device's own order over a list whose default order comes from the server: Home rows
 * (Settings › Home Layout › Row Order) and menu entries (Settings › Menu › Tab Order). Saved on
 * the device only; an empty saved order follows the server.
 */
object DeviceOrder {
    /**
     * [server]'s ids in the [saved] order. Saved ids still in [server] keep their saved order;
     * an id the saved order doesn't know goes right after its nearest earlier neighbour in
     * [server] already placed, or first when it has none. Saved ids [server] no longer has drop.
     */
    fun merge(saved: List<String>, server: List<String>): List<String> {
        val ids = server.distinct()
        if (saved.isEmpty()) return ids
        val present = ids.toSet()
        val out = saved.filter { it in present }.distinct().toMutableList()
        ids.forEachIndexed { i, id ->
            if (id in out) return@forEachIndexed
            val anchor = (i - 1 downTo 0).map { ids[it] }.firstOrNull { it in out }
            out.add(if (anchor == null) 0 else out.indexOf(anchor) + 1, id)
        }
        return out
    }

    /** [items] (in server order) put in the [saved] order, by [key]. */
    fun <T> apply(items: List<T>, saved: List<String>, key: (T) -> String): List<T> {
        if (saved.isEmpty()) return items
        val byKey = items.groupBy(key)
        return merge(saved, items.map(key)).flatMap { byKey[it].orEmpty() }
    }

    /**
     * [items] in the [saved] order, except those [first] keeps in front and [last] keeps at the
     * end (each in its own order): Home leads the menu and Settings ends the TV's.
     */
    fun <T> applyPinned(items: List<T>, saved: List<String>, key: (T) -> String, first: (T) -> Boolean, last: (T) -> Boolean = { false }): List<T> {
        val (lead, rest) = items.partition(first)
        val (tail, middle) = rest.partition(last)
        return lead + apply(middle, saved, key) + tail
    }

    /**
     * The order to save after the user arranged [shown] (what the editor listed): those first,
     * then ids of the [previous] order the editor didn't list (hidden tabs, another Home view's
     * rows) after them, so they keep a place when they come back.
     */
    fun save(previous: List<String>, shown: List<String>): List<String> = shown + previous.filter { it !in shown }

    /** [list] with the item at [from] moved to [to]. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Stored as one string: ids never hold a newline. */
    fun encode(order: List<String>): String = order.joinToString("\n")

    fun decode(stored: String?): List<String> = stored?.split("\n")?.filter { it.isNotEmpty() }.orEmpty()
}
