package com.strmr.tv.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.strmr.tv.app.AppLog

/**
 * The Android TV home screen's rows, written through the TV provider as the Expo app's
 * `TopShelfManagerModule` does: a Continue Watching and a Watchlist preview channel (found
 * again by their internal provider IDs, their row IDs kept in prefs) and Watch Next programs
 * for Continue Watching, all tagged `strmr:` so only the app's own are replaced. A channel with
 * nothing to show is emptied rather than deleted (the launcher hides an empty channel): the
 * launcher makes only an app's first channel browsable by itself, so a deleted and republished
 * channel would stay hidden until the user turned it on again. Blocking provider I/O: call off
 * the main thread, one call at a time.
 */
class TvLauncher(private val context: Context) {
    private val helper = PreviewChannelHelper(context)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private enum class Channel(val prefsKey: String, val internalId: String, val displayName: String, val description: String) {
        CONTINUE_WATCHING("continue_watching_channel_id", "strmr-continue-watching", "Continue Watching", "Resume titles from MediaStorm"),
        WATCHLIST("watchlist_channel_id", "strmr-watchlist", "Watchlist", "Saved titles from your MediaStorm watchlist"),
    }

    fun write(payload: LauncherPayload) {
        syncChannel(Channel.CONTINUE_WATCHING, payload.continueWatching)
        syncChannel(Channel.WATCHLIST, payload.watchlist)
        syncWatchNext(payload.continueWatching)
    }

    fun clear() {
        Channel.entries.forEach(::emptyChannel)
        deleteManagedWatchNext()
    }

    /** What the provider holds now, for the log: channel IDs, browsable flags and program counts. */
    fun describe(): String {
        val channels = Channel.entries.joinToString { ch ->
            val id = findChannel(ch)
            if (id == null) "${ch.internalId}=none" else {
                val browsable = runCatching { helper.getPreviewChannel(id)?.isBrowsable }.getOrNull()
                "${ch.internalId}=#$id(${countPrograms(id)} programs${if (browsable == true) ", browsable" else ""})"
            }
        }
        return "$channels; watchNext=${managedWatchNextIds().size}"
    }

    private fun syncChannel(ch: Channel, programs: List<LauncherProgram>) {
        if (programs.isEmpty()) return emptyChannel(ch)
        val existing = findChannel(ch)
        val builder = PreviewChannel.Builder()
            .setDisplayName(ch.displayName)
            .setDescription(ch.description)
            .setAppLinkIntentUri(Uri.parse(DeepLink.HOME_URL))
            .setInternalProviderId(ch.internalId)
        logo()?.let { builder.setLogo(it) }
        val channel = builder.build()
        val id = if (existing != null) {
            helper.updatePreviewChannel(existing, channel)
            existing
        } else {
            helper.publishChannel(channel).also { AppLog.i(TAG, "published channel ${ch.internalId} #$it") }
        }
        // The builder's logo was stored with the channel.
        prefs.edit().putLong(ch.prefsKey, id).putInt(logoKey(ch), ChannelLogo.VERSION).apply()
        // As Expo: the app's first channel becomes browsable; the launcher decides for the others
        // (the user adds them from its channel settings). Asked again only while not browsable.
        if (helper.getPreviewChannel(id)?.isBrowsable != true) {
            runCatching { TvContractCompat.requestChannelBrowsable(context, id) }
                .onFailure { AppLog.w(TAG, "requestChannelBrowsable ${ch.internalId}: ${it.message}") }
        }
        deletePrograms(id)
        programs.forEachIndexed { index, p -> helper.publishPreviewProgram(previewProgram(id, p, 1000 - index)) }
    }

    private fun previewProgram(channelId: Long, p: LauncherProgram, weight: Int): PreviewProgram {
        val b = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setWeight(weight)
        b.setType(type(p))
            .setTitle(p.title)
            .setDescription(p.description)
            // A channel card opens details; Watch Next resumes (the launcher has one link per card).
            .setIntentUri(Uri.parse(p.displayUrl))
            .setInternalProviderId(managedId(p.id))
            .setPosterArtAspectRatio(aspect(p))
        p.imageUrl?.let { b.setPosterArtUri(Uri.parse(it)) }
        episode(p, { b.setSeasonNumber(it) }, { b.setEpisodeNumber(it) }, { b.setEpisodeTitle(it) })
        if (p.positionMs != null && p.durationMs != null) {
            b.setLastPlaybackPositionMillis(p.positionMs.toInt()).setDurationMillis(p.durationMs.toInt())
        }
        return b.build()
    }

    @SuppressLint("RestrictedApi")
    private fun syncWatchNext(programs: List<LauncherProgram>) {
        deleteManagedWatchNext()
        programs.forEach { p ->
            val b = WatchNextProgram.Builder()
            b.setWatchNextType(
                if (p.watchNext == LauncherProgram.WatchNext.NEXT) TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
                else TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE,
            )
                .setLastEngagementTimeUtcMillis(p.lastEngagementMs ?: System.currentTimeMillis())
            b.setType(type(p))
                .setTitle(p.title)
                .setDescription(p.description)
                .setIntentUri(Uri.parse(p.playUrl))
                .setInternalProviderId(managedId(p.id))
                .setPosterArtAspectRatio(aspect(p))
            p.imageUrl?.let { b.setPosterArtUri(Uri.parse(it)) }
            episode(p, { b.setSeasonNumber(it) }, { b.setEpisodeNumber(it) }, { b.setEpisodeTitle(it) })
            if (p.positionMs != null && p.durationMs != null) {
                b.setLastPlaybackPositionMillis(p.positionMs.toInt()).setDurationMillis(p.durationMs.toInt())
            }
            helper.publishWatchNextProgram(b.build())
        }
    }

    private inline fun episode(p: LauncherProgram, season: (Int) -> Unit, number: (Int) -> Unit, title: (String) -> Unit) {
        if (p.kind != LauncherProgram.Kind.EPISODE) return
        p.seasonNumber?.let(season)
        p.episodeNumber?.let(number)
        p.episodeTitle?.let(title)
    }

    private fun type(p: LauncherProgram) = when (p.kind) {
        LauncherProgram.Kind.MOVIE -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
        LauncherProgram.Kind.EPISODE -> TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
        LauncherProgram.Kind.SERIES -> TvContractCompat.PreviewPrograms.TYPE_TV_SERIES
    }

    private fun aspect(p: LauncherProgram) =
        if (p.landscape) TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9 else TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3

    private fun emptyChannel(ch: Channel) {
        val id = findChannel(ch) ?: return
        ensureLogo(ch, id)
        val count = countPrograms(id)
        if (count == 0) return
        deletePrograms(id)
        AppLog.i(TAG, "emptied channel ${ch.internalId} #$id ($count programs)")
    }

    /** The saved row ID when it still holds this channel, else a scan by internal provider ID. */
    private fun findChannel(ch: Channel): Long? {
        val saved = prefs.getLong(ch.prefsKey, -1L)
        if (saved > 0 && helper.getPreviewChannel(saved)?.internalProviderId == ch.internalId) return saved
        return helper.allChannels.firstOrNull { it.internalProviderId == ch.internalId }?.id
    }

    private fun programIds(channelId: Long): List<Long> {
        val cursor = context.contentResolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId), PreviewProgram.PROJECTION, null, null, null,
        ) ?: return emptyList()
        return cursor.use { c -> buildList { while (c.moveToNext()) add(PreviewProgram.fromCursor(c).id) } }
    }

    private fun countPrograms(channelId: Long) = programIds(channelId).size

    private fun deletePrograms(channelId: Long) = programIds(channelId).forEach(helper::deletePreviewProgram)

    private fun managedWatchNextIds(): List<Long> {
        val cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null,
        ) ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val program = WatchNextProgram.fromCursor(c)
                    if (program.internalProviderId?.startsWith(MANAGED_PREFIX) == true) add(program.id)
                }
            }
        }
    }

    private fun deleteManagedWatchNext() = managedWatchNextIds().forEach {
        context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null)
    }

    /** Built once; `updatePreviewChannel` stores it again on every sync, so existing channels get it too. */
    private val channelLogo: Bitmap? by lazy { runCatching { ChannelLogo.build(context) }.getOrNull() }

    private fun logo(): Bitmap? = channelLogo

    private fun logoKey(ch: Channel) = "${ch.internalId}_logo_version"

    /** An empty channel isn't rebuilt, so a channel published with an older logo gets the current one here. */
    private fun ensureLogo(ch: Channel, id: Long) {
        if (prefs.getInt(logoKey(ch), 0) == ChannelLogo.VERSION) return
        val logo = logo() ?: return
        if (ChannelLogoUtils.storeChannelLogo(context, id, logo)) {
            prefs.edit().putInt(logoKey(ch), ChannelLogo.VERSION).apply()
            AppLog.i(TAG, "logo v${ChannelLogo.VERSION} stored for ${ch.internalId} #$id")
        }
    }

    companion object {
        const val TAG = "Launcher"
        private const val PREFS = "launcher_channels"
        private const val MANAGED_PREFIX = "strmr:"

        fun managedId(id: String) = "$MANAGED_PREFIX$id"

        /**
         * Why the home screen's rows can't be written here, or null when they can: Android 8+ with
         * the leanback launcher (Expo's `supportsAndroidTvLauncher`), a TV provider, and not a
         * Fire TV, whose launcher shows no Android TV channels.
         */
        fun unsupportedReason(context: Context): String? {
            val pm = context.packageManager
            return when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> "Android ${Build.VERSION.SDK_INT} has no preview channels"
                pm.hasSystemFeature("amazon.hardware.fire_tv") -> "Fire TV launcher shows no Android TV channels"
                !pm.hasSystemFeature("android.software.leanback") -> "no leanback launcher"
                runCatching { pm.resolveContentProvider(TvContractCompat.AUTHORITY, 0) }.getOrNull() == null -> "no TV provider"
                else -> null
            }
        }
    }
}
