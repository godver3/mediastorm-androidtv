package com.strmr.tv.ui

/**
 * Lazy-list keys for server items whose IDs aren't guaranteed unique (a list holding the same
 * title twice, unnumbered library extras): a repeated key crashes a LazyRow / LazyColumn /
 * grid, so the second and later repeats get an occurrence suffix (`id#2`, `id#3`) while
 * first occurrences keep their plain, stable key.
 */
fun <T> uniqueKeys(items: List<T>, id: (T) -> String): List<String> {
    val seen = HashMap<String, Int>()
    return items.map { item ->
        val base = id(item)
        val n = (seen[base] ?: 0) + 1
        seen[base] = n
        if (n == 1) base else "$base#$n"
    }
}
