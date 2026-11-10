package com.strmr.tv.ui.sports.game.basketball

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.strmr.tv.sports.SportsBasketballShot
import com.strmr.tv.sports.SportsGame
import com.strmr.tv.sports.SportsTeam

/**
 * The basketball court in drawing units (Expo `basketball-court.ts`, tvOS `SportsCourtGeometry`;
 * 10 units a foot): the 940 × 500 playing area from (40, 40), rims at x 92.5 and 927.5 on the
 * y 290 midline. The lane, three-point line and restricted area follow the league; the paint
 * takes the home colour, and Boston and Brooklyn keep their parquet and herringbone floors. Shots
 * are team-oriented: the away side attacks the left basket, the home side the right (never the
 * broadcast's direction).
 */
data class SportsCourtGeometry(
    /** Half the lane's width. */
    val laneHalf: Double,
    val restricted: Boolean,
    val floor: Floor,
    val threeRadius: Double,
    val cornerDistance: Double,
    /** The home side's paint colour (`#rrggbb`); a neutral grey without one. */
    val paint: String,
) {
    enum class Floor { WOOD, PARQUET, HERRINGBONE }

    companion object {
        const val LEFT = 40.0
        const val TOP = 40.0
        const val WIDTH = 940.0
        const val HEIGHT = 500.0
        const val MIDLINE = 290.0
        const val LEFT_RIM = 92.5
        const val RIGHT_RIM = 927.5

        fun of(league: String, home: SportsTeam): SportsCourtGeometry {
            val college = "college-basketball" in league
            val hex = home.color?.replace("#", "").orEmpty()
            return SportsCourtGeometry(
                laneHalf = if (college) 60.0 else 80.0,
                restricted = league != "womens-college-basketball",
                floor = when {
                    league == "nba" && home.id == "2" -> Floor.PARQUET
                    league == "nba" && home.id == "17" -> Floor.HERRINGBONE
                    else -> Floor.WOOD
                },
                threeRadius = if (league == "nba") 237.5 else (22 + 1.75 / 12) * 10,
                cornerDistance = if (college) (21 + 7.375 / 12) * 10 else 220.0,
                paint = if (hex.length == 6 && hex.toLongOrNull(16) != null) "#" + hex.lowercase() else "#545e65",
            )
        }

        /**
         * Where a shot was taken (Expo `basketballShotPosition`), in drawing units; null for a side
         * not in this game or a spot off the court.
         */
        fun position(shot: SportsBasketballShot, game: SportsGame): Offset? {
            if (shot.teamId != game.awayTeam.id && shot.teamId != game.homeTeam.id) return null
            if (!shot.x.isFinite() || !shot.y.isFinite() || shot.x !in 0.0..50.0 || shot.y !in -5.25..88.75) return null
            return if (shot.teamId == game.awayTeam.id) Offset((LEFT_RIM + shot.y * 10).toFloat(), (40 + shot.x * 10).toFloat())
            else Offset((RIGHT_RIM - shot.y * 10).toFloat(), (540 - shot.x * 10).toFloat())
        }

        /**
         * A corner for the latest shot's card away from its marker (Expo
         * `basketballShotCardPlacement`), keeping [clearance] around the marker; null when the card
         * doesn't fit beside it.
         */
        fun cardPlacement(size: Size, card: Size, marker: Offset, clearance: Float, inset: Float): Offset? {
            if (size.width <= 0 || size.height <= 0 || card.width + 2 * inset > size.width / 2 || card.height + 2 * inset > size.height) return null
            val left = if (marker.x < size.width / 2) size.width - card.width - inset else inset
            val tops = if (marker.y < size.height / 2) listOf(size.height - card.height - inset, inset) else listOf(inset, size.height - card.height - inset)
            for (top in tops) {
                val dx = marker.x - marker.x.coerceIn(left, left + card.width)
                val dy = marker.y - marker.y.coerceIn(top, top + card.height)
                if (dx * dx + dy * dy > clearance * clearance) return Offset(left, top)
            }
            return null
        }
    }
}
