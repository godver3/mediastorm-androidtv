package com.strmr.tv.ui.sports.game.football

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.strmr.tv.sports.SportsFootballDrive
import com.strmr.tv.sports.SportsLifecycle
import com.strmr.tv.ui.sports.SportsLive
import com.strmr.tv.ui.sports.SportsMuted
import com.strmr.tv.ui.sports.SportsPanel
import com.strmr.tv.ui.sports.SportsReadout
import com.strmr.tv.ui.sports.SportsTeamLogo
import com.strmr.tv.ui.sports.game.SportsGamePage
import com.strmr.tv.ui.sports.game.SportsTeamColors

/**
 * A drive's place on the field as a thin bar (Expo `DrivePosition`): the field from the side's own
 * goal to the opponent's with quarter ticks, and the drive's arrow (white and thicker for the drive
 * in progress, a dot where a score ended). Every side attacks right. Without a credible path
 * ([hasReliablePath]) only a known start shows. Drawn in dp, not the field's units, so a narrow
 * bar keeps legible strokes.
 */
@Composable
fun SportsDrivePositionBar(page: SportsGamePage, drive: SportsFootballDrive, result: SportsDriveResult, modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val team = page.team(drive.teamId)
    val color = team?.let { SportsTeamColors.parse(page.colorHex(it)) } ?: SportsMuted
    Canvas(modifier.fillMaxWidth().height(height)) {
        val point = 0.5.dp.toPx()
        val scale = size.width / SportsFieldSpace.WIDTH
        fun x(yard: Double) = (SportsFieldSpace.x(yard) * scale).toFloat()
        val y = size.height / 2
        val rule = Color.White.copy(alpha = 0.28f)
        drawLine(rule, Offset(x(0.0), y), Offset(x(100.0), y), strokeWidth = 1.5f * point)
        for (yard in listOf(0.0, 25.0, 50.0, 75.0, 100.0)) {
            drawLine(rule, Offset(x(yard), y - size.height * 0.34f), Offset(x(yard), y + size.height * 0.34f), strokeWidth = 1.5f * point)
        }
        if (team == null) return@Canvas
        fun own(yard: Double) = x(SportsFootballYards.own(yard, team, page.game))
        val start = drive.knownStart
        val end = drive.knownEnd
        if (drive.hasReliablePath && start != null && end != null) {
            arrow(own(start), own(end), y, color, result.current, result.scoring, width = 4 * point, size = 1.4f * point)
        } else if (start != null) {
            dot(own(start), y, 5 * point, color, 1.5f * point)
        }
    }
}

/** A drive's result: gold with a diamond when it scored; the drive in progress in the hub's live red, with its dot. */
@Composable
fun SportsDriveResultText(result: SportsDriveResult, fontSize: TextUnit = 13.sp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (result.current) Box(Modifier.size(5.dp).clip(CircleShape).background(SportsLive))
        Text(
            (if (result.scoring) "◆ " else "") + result.text, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (result.current) SportsLive else if (result.scoring) SportsTeamColors.scoring else Color.White,
        )
    }
}

/**
 * A drive in the Drives tab's list (Expo `FootballDrivesTable`'s row with `DrivePosition`): its
 * number in the game, side, quarter and result, its place on the field, then start → end with
 * plays, yards and time. The side's colour runs down the edge.
 */
@Composable
fun SportsDriveListRow(page: SportsGamePage, drive: SportsFootballDrive, number: Int, result: SportsDriveResult) {
    val team = page.team(drive.teamId)
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(team?.let { SportsTeamColors.parse(page.colorHex(it)) } ?: SportsMuted))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("$number", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 18.dp))
                team?.let { SportsTeamLogo(it, 17.dp) }
                Text(team?.shortName ?: "Team unavailable", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(drive.period.ifEmpty { "—" }, fontSize = 14.sp, color = SportsMuted)
                Spacer(Modifier.weight(1f))
                SportsDriveResultText(result)
            }
            SportsDrivePositionBar(page, drive, result, height = 13.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(drive.span, fontSize = 11.sp)
                Spacer(Modifier.weight(1f).widthIn(min = 6.dp))
                Text(
                    drive.statsLine ?: drive.description.ifEmpty { "Drive statistics unavailable" }, fontSize = 11.sp, color = SportsMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The Overview's latest drives (Expo `FootballRecentDrives`): the four newest, each with its number
 * in the game, side, place on the field, start and end, and result. One stop each.
 */
@Composable
fun SportsRecentDrives(page: SportsGamePage, possession: SportsFootballPossession?) {
    val all = page.detail.footballDrives
    SportsPanel("Recent Drives", note = "Newest first · Each team attacks right, own goal to opponent goal") {
        all.takeLast(4).asReversed().forEach { drive ->
            val team = page.team(drive.teamId)
            val result = SportsDriveResult.of(drive, possession)
            SportsReadout {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${SportsDriveExplorer.number(drive, all)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.width(22.dp))
                    Row(Modifier.width(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        team?.let { SportsTeamLogo(it, 16.dp) }
                        Text(team?.shortName ?: "—", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    SportsDrivePositionBar(page, drive, result, Modifier.weight(1f))
                    Text(drive.span, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(95.dp))
                    Box(Modifier.width(80.dp)) { SportsDriveResultText(result, 12.sp) }
                }
            }
        }
    }
}

/** Whether the Overview shows [SportsRecentDrives]: drives sent and the game under way. */
val SportsGamePage.showsRecentDrives: Boolean
    get() = detail.footballDrives.isNotEmpty() && lifecycle != SportsLifecycle.SCHEDULED
