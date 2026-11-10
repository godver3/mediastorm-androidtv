package com.strmr.tv.player

import kotlinx.serialization.json.JsonObject

/**
 * Listens to a playback's progress reports beside the server ([PlayerSession.progressSink]).
 * The phone app plays downloads with one: it keeps the resume point on the device and holds
 * the reports the server didn't take (offline) to send once it answers again.
 */
interface ProgressSink {
    /** A report is being made at this position (seconds). Main thread. */
    fun saved(positionSeconds: Double, durationSeconds: Double, ended: Boolean)

    /** A report's body the server didn't take. Any thread. */
    fun undelivered(body: JsonObject)
}
