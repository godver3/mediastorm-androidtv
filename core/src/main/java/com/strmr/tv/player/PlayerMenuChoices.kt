package com.strmr.tv.player

import androidx.media3.common.C
import androidx.media3.common.Tracks

// What the player's menus offer, shared by the TV popovers and the phone's bottom sheets.

/** One row of a player menu; a new `section` starts a headed group. */
data class MenuChoice(
    val title: String,
    val detail: String? = null,
    val selected: Boolean,
    val section: String? = null,
    val onClick: () -> Unit,
)

/** Playback speeds, as the tvOS player's Settings menu offers them. */
val PLAYER_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * The Audio or Subtitles menu's rows: the stream's tracks (subtitles with Off and any downloaded
 * or translated track, then Search Online…, Translate… and Style). The caller passes the
 * current values of the controller's flows so the rows follow them.
 */
fun trackMenuChoices(
    controller: PlayerController,
    audio: Boolean,
    tracks: Tracks,
    external: ExternalTrack?,
    externalOn: Boolean,
    translationAllowed: Boolean?,
    onDone: () -> Unit,
    onStyle: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
): List<MenuChoice> {
    val list = tracks.ofType(if (audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT)
        .filter { it.supported && (controller.session.isLive || !TrackPreferences.isClosedCaption(it.format)) }
    val ext = external.takeIf { !audio }
    return buildList {
        if (!audio && (list.isNotEmpty() || ext != null)) {
            add(MenuChoice("Off", selected = list.none { it.selected } && !(ext != null && externalOn)) { controller.subtitlesOff(); onDone() })
        }
        ext?.let { t ->
            add(MenuChoice(t.title, t.detail, selected = externalOn) { controller.selectExternal(); onDone() })
        }
        list.forEach { track ->
            val f = track.format
            add(MenuChoice(f.menuTitle("Track ${track.typeIndex + 1}"), if (audio) f.audioDetail() else f.subtitleDetail(),
                           track.selected) { controller.select(track); onDone() })
        }
        if (!audio && onSearch != null && controller.online.available) {
            add(MenuChoice("Search Online…", selected = false, onClick = onSearch))
        }
        // Only while the server allows translation (its switch is a kill switch).
        if (!audio && onTranslate != null && controller.online.available && translationAllowed == true) {
            add(MenuChoice("Translate…", selected = false, onClick = onTranslate))
        }
        if (!audio && onStyle != null && (list.isNotEmpty() || ext != null)) {
            add(MenuChoice("Style", SubtitleStyle.sizeName(SubtitleStyleStore.style.size), selected = false, onClick = onStyle))
        }
    }
}

/**
 * The Subtitles menu's Style picks over the device's [SubtitleStyle] (size, box, position, as
 * the tvOS quick menu offers); each pick applies at once and the full set lives in Settings.
 */
fun subtitleStyleChoices(style: SubtitleStyle = SubtitleStyleStore.style): List<MenuChoice> =
    SubtitleStyle.sizes.map { v ->
        MenuChoice(SubtitleStyle.sizeName(v), selected = v == style.size, section = "Size") { SubtitleStyleStore.update { it.copy(size = v) } }
    } + SubtitleStyle.backgroundOpacities.map { v ->
        MenuChoice(SubtitleStyle.percent(v), selected = v == style.backgroundOpacity, section = "Background") {
            SubtitleStyleStore.update { it.copy(backgroundOpacity = v) }
        }
    } + SubtitleStyle.margins.map { v ->
        MenuChoice(SubtitleStyle.marginName(v), selected = v == style.bottomMargin, section = "Position") {
            SubtitleStyleStore.update { it.copy(bottomMargin = v) }
        }
    }

/** The Playback Speed menu's rows. */
fun speedChoices(controller: PlayerController, current: Float, onDone: () -> Unit): List<MenuChoice> =
    PLAYER_SPEEDS.map { s ->
        MenuChoice(speedLabel(s), selected = s == current) { controller.setSpeed(s); onDone() }
    }

/** "Normal" or "1.5×". */
fun speedLabel(speed: Float): String = if (speed == 1f) "Normal" else "${speed}×"

/** Translate…'s first step: the stream's text tracks, labelled as in the Subtitles menu. */
fun translateSourceChoices(controller: PlayerController, tracks: Tracks, onPick: (PlayerTrack) -> Unit): List<MenuChoice> =
    controller.online.translatableTracks(tracks).map { track ->
        val f = track.format
        MenuChoice(f.menuTitle("Track ${track.typeIndex + 1}"), f.subtitleDetail(), selected = false) { onPick(track) }
    }
