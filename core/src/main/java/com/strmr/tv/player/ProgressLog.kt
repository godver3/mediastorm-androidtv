package com.strmr.tv.player

import com.strmr.tv.api.ProgressReply
import com.strmr.tv.api.formatTimestamp
import com.strmr.tv.app.AppLog
import kotlin.math.roundToInt

/**
 * How one playback's progress reports fare, so a resume point that didn't stick can be traced
 * in an uploaded log. Logged: the first report the server stores, every pause / close / end
 * report with the server's resume decision, a failed report (each one that isn't periodic; the
 * first of a run of periodic ones, then every [REPEAT]th) and the first success after a failure.
 */
class ProgressLog(
    private val title: String,
    private val log: (warn: Boolean, message: String) -> Unit = { warn, message ->
        if (warn) AppLog.w(AREA, message) else AppLog.i(AREA, message)
    },
) {
    enum class Kind(val label: String) { PERIODIC("periodic"), PAUSE("pause"), CLOSE("close"), END("end") }

    private var stored = false
    private var failures = 0

    fun saved(kind: Kind, position: Double, duration: Double, reply: ProgressReply) {
        val line = "${kind.label} report at ${at(position, duration)} saved: ${decision(reply)}"
        when {
            failures > 0 -> log(false, "$title: $line (after $failures failed)")
            kind != Kind.PERIODIC -> log(false, "$title: $line")
            !stored -> log(false, "$title: first $line")
        }
        stored = true
        failures = 0
    }

    fun failed(kind: Kind, position: Double, duration: Double, error: Throwable) {
        failures++
        if (kind != Kind.PERIODIC || failures == 1 || failures % REPEAT == 0) {
            log(true, "$title: ${kind.label} report at ${at(position, duration)} failed ($failures in a row): ${error.message ?: error::class.simpleName}")
        }
    }

    private fun at(position: Double, duration: Double) =
        "${formatTimestamp(position)}/${formatTimestamp(duration)} (${percent(if (duration > 0) position / duration * 100 else 0.0)})"

    private fun decision(reply: ProgressReply): String {
        val resume = reply.resume ?: return "stored at ${percent(reply.percentWatched)}"
        return if (resume.eligible) "resume offered at ${formatTimestamp(resume.position)}"
        else "stored at ${percent(resume.percent)}, resume not offered"
    }

    private fun percent(value: Double) = "${(value * 10).roundToInt() / 10.0}%"

    companion object {
        const val AREA = "Progress"
        /** A run of failed periodic reports logs its first, then every this many (10 s apart). */
        const val REPEAT = 6
    }
}
