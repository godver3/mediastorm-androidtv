package com.strmr.tv.home

/**
 * Which Home row feeds the carousel, from the view's rows as they load: the
 * chosen row, else Top 10, else Trending (movies, then shows), else the first row with titles.
 * A preferred row that is still loading is kept (the carousel waits for it rather than jumping
 * to another row and back); one that failed or came back without titles is passed over.
 */
object CarouselPick {
    const val TOP_TEN = "top-ten"
    private val trending = listOf("trending-movies", "trending-tv")

    /** The row's id, or null when no row can feed the carousel (yet). */
    fun pick(slots: List<HomeSlot>, chosen: String?): String? {
        val preferred = listOfNotNull(chosen) + TOP_TEN + trending
        for (id in preferred) {
            when (val slot = slots.firstOrNull { it.id == id }) {
                null, is HomeSlot.Failed -> continue
                is HomeSlot.Loading -> return id
                is HomeSlot.Shelf -> if (slides(slot.shelf).isNotEmpty()) return id
            }
        }
        return slots.firstOrNull { it is HomeSlot.Shelf && slides(it.shelf).isNotEmpty() }?.id
    }

    /** The carousel's slides: up to [MAX] of the row's titles (cards that open details). */
    fun slides(shelf: HomeShelf): List<HomeCard> = shelf.shown.filter { it.target != null }.take(MAX)

    const val MAX = 10
}
