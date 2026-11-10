package com.strmr.tv.ui.sports.cards

import com.strmr.tv.sports.SportsTeam

/**
 * A logo-less side's mark: a team's short abbreviation ("RCB"), else a player's initials
 * ("Chris Rodesch" → "CR", "J.J. Spaun" → "JS").
 */
val SportsTeam.monogram: String
    get() {
        val short = abbreviation
        if (!short.isNullOrEmpty() && short.length <= 4 && ' ' !in short && '.' !in short) return short
        val words = name.split(' ', '/').filter { it.firstOrNull()?.isLetter() == true }
        val first = words.firstOrNull()?.first() ?: return shortName.take(3)
        val last = if (words.size > 1) words.last().first() else null
        return listOfNotNull(first, last).joinToString("").uppercase()
    }
