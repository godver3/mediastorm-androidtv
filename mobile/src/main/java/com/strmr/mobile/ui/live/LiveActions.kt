package com.strmr.mobile.ui.live

import com.strmr.tv.live.EpgProgram
import com.strmr.tv.live.LiveCatchups
import com.strmr.tv.live.LiveChannel
import com.strmr.tv.live.Recording
import com.strmr.tv.live.SeriesRule

/** One action in a channel's or programme's menu and sheet, with its TV wording. */
sealed class LiveAction(val label: String) {
    /** Tunes the channel live. */
    class WatchLive(label: String, val program: EpgProgram?) : LiveAction(label)
    /** Plays [program] from the provider's catch-up (an ended one, or Start Over on air). */
    class Catchup(label: String, val program: EpgProgram) : LiveAction(label)
    class CancelRecording(label: String, val recording: Recording) : LiveAction(label)
    class Record(label: String, val program: EpgProgram) : LiveAction(label)
    class RecordSeries(label: String, val program: EpgProgram, val newOnly: Boolean) : LiveAction(label)
    class SwitchSeries(label: String, val rule: SeriesRule, val newOnly: Boolean) : LiveAction(label)
    class CancelSeries(label: String, val rule: SeriesRule) : LiveAction(label)
    class RecordBlock : LiveAction("Record 2 Hour Block")
    class ChooseSource : LiveAction("Choose Source")
    class Favorite(label: String) : LiveAction(label)
    class Hide(label: String) : LiveAction(label)
}

/**
 * The TV long-press menu's choices, split into the groups the phone sheet shows. Pure, so the
 * wording and conditions are tested without a store.
 */
object LiveActions {
    /**
     * Watch choices: an ended programme replays from catch-up (and the channel tunes live); one
     * on now (or nothing chosen) tunes live, with Start Over when the provider archives it.
     */
    fun watch(channel: LiveChannel, program: EpgProgram?, current: EpgProgram?, now: Long): List<LiveAction> {
        if (program != null && program.stopMs <= now) {
            return listOfNotNull(
                if (LiveCatchups.canReplay(channel, program, now)) LiveAction.Catchup("Watch from Start", program) else null,
                LiveAction.WatchLive("Watch ${channel.name} Live", null),
            )
        }
        if (program != null && program.startMs > now) return listOf(LiveAction.WatchLive("Watch ${channel.name} Live", current))
        val airing = program ?: current
        return listOfNotNull(
            LiveAction.WatchLive("Watch Live", airing),
            airing?.takeIf { LiveCatchups.canReplay(channel, it, now) }?.let { LiveAction.Catchup("Start Over", it) },
        )
    }

    /**
     * Recording choices, as the TV `ChannelMenu`: a [program] that hasn't ended leads with its
     * recording (Stop / Cancel the one covering it, else Record or Record Once), then its
     * series (switch or cancel an existing rule, else Record Series / New Episodes Only). Without
     * one, the same for what is on now ([target]), worded for the channel. [existing] is the
     * recording covering the target, [rule] the series rule recording it.
     */
    fun record(program: EpgProgram?, target: EpgProgram?, existing: Recording?, rule: SeriesRule?, now: Long): List<LiveAction> {
        val p = target ?: return listOf(LiveAction.RecordBlock())
        val asProgram = program != null && program.stopMs > now
        val single = if (existing != null) {
            LiveAction.CancelRecording(if (existing.status == "pending") "Cancel Scheduled Recording" else "Stop Recording", existing)
        } else {
            LiveAction.Record(if (!asProgram) "Record Current Program" else if (p.isAiring(now)) "Record" else "Record Once", p)
        }
        val series = if (rule != null) {
            listOf(
                if (rule.newOnly) LiveAction.SwitchSeries("Record Every Airing Instead", rule, newOnly = false)
                else LiveAction.SwitchSeries("Record New Episodes Only Instead", rule, newOnly = true),
                LiveAction.CancelSeries(if (rule.isRegex) "Delete Rule \"${rule.pattern}\"" else "Cancel Series Recording", rule),
            )
        } else {
            listOf(
                LiveAction.RecordSeries(if (asProgram) "Record Series" else "Record Every Airing", p, newOnly = false),
                LiveAction.RecordSeries("Record New Episodes Only", p, newOnly = true),
            )
        }
        return listOf(single) + series + LiveAction.RecordBlock()
    }

    /** The channel's own choices: Choose Source (Stremio channels), favorite and hide. */
    fun channel(channel: LiveChannel, favorite: Boolean, hidden: Boolean): List<LiveAction> = listOfNotNull(
        if (channel.hasStremioSources) LiveAction.ChooseSource() else null,
        LiveAction.Favorite(if (favorite) "Remove from Favorites" else "Add to Favorites"),
        LiveAction.Hide(if (hidden) "Show Channel" else "Hide Channel"),
    )
}
