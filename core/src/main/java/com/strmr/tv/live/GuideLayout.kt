package com.strmr.tv.live

/** One slot in a guide row: a programme, or a stretch without guide data. */
data class GuideCell(val id: Long, val program: EpgProgram?, val start: Long, val end: Long)

/** How a guide row's programmes become cells, shared by the TV guide and the phone guide. */
object GuideLayout {
    /**
     * The window's programmes with gaps filled; gaps under five minutes fold into the next
     * programme so no sliver of a cell is left to focus or tap. `id` is the programme's own start
     * (stable while the window pages), or a gap's start.
     */
    fun cells(programs: List<EpgProgram>, from: Long, to: Long): List<GuideCell> {
        val cells = mutableListOf<GuideCell>()
        var cursor = from
        for (p in programs) {
            if (p.endMs <= cursor || p.startMs >= to) continue
            var start = maxOf(p.startMs, cursor)
            if (start - cursor >= 300_000) cells += GuideCell(cursor, null, cursor, start) else start = cursor
            val end = minOf(p.endMs, to)
            cells += GuideCell(p.startMs, p, start, end)
            cursor = end
        }
        if (to - cursor >= 60_000 || cells.isEmpty()) {
            cells += GuideCell(cursor, null, cursor, to)
        } else {
            val last = cells.removeAt(cells.lastIndex)
            cells += last.copy(end = to)
        }
        return cells
    }
}
