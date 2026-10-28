package com.strmr.tv.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App
import com.strmr.tv.app.AppLog

/** Log tag area for the Home carousel (`adb logcat -s strmr.HomeCarousel`). */
const val CAROUSEL_LOG = "HomeCarousel"

/** Where the focused card sits in a Home row once it has scrolled to it (`homeShelfFocusModel`). */
enum class FocusedCardPosition(val label: String) { LEFT("Left"), CENTER("Center"), RIGHT("Right") }

/** How much of Home the hero (logo, line, overview) takes above the rows. */
enum class HeroSize(val label: String) {
    FULL("Full"), COMPACT("Compact"), MINIMAL("Minimal"), OFF("Off");

    companion object {
        /** The size nearest the profile's `homeHeroScale` (0.5–1). */
        fun fromProfile(scale: Double) = if (scale >= 0.9) FULL else if (scale >= 0.7) COMPACT else MINIMAL
    }
}

enum class HeroTextSize(val label: String, val scale: Float) { SMALL("Small", 0.85f), MEDIUM("Medium", 1f), LARGE("Large", 1.15f) }

/**
 * How Focused Title Info's full hero, one shelf to a screen, moves between shelves (and from the
 * carousel's controls into the first one): the rows scroll, or the page fades out, the next shelf
 * takes the place and it fades back in.
 */
enum class ShelfTransition(val label: String) { SLIDE("Slide"), SWAP("Swap") }

enum class CardSize(val label: String, val scale: Double) {
    SMALL("Small", 0.6), MEDIUM("Medium", 0.8), LARGE("Large", 1.0),
    /** About 1.25 × Large: a 169 dp poster (tvOS 225 × 338 pt). */
    EXTRA_LARGE("Extra Large", 1.25);

    companion object {
        fun nearest(scale: Double) = entries.minBy { kotlin.math.abs(it.scale - scale) }
    }
}

/**
 * Where a card's title and its year / episode line go: under the card, over
 * its bottom edge on a gradient (Expo's `showLandscapeTextOverlay` look), or nowhere.
 */
enum class CardText(val label: String) {
    BELOW("Below"), OVERLAY("Overlay"),
    /** Over 16:9 cards only (landscape rows and posters opened on focus); posters stay bare. */
    OVERLAY_WIDE("Overlay on Expanded"),
    NONE("None");

    /** Whether a card drawing its 16:9 art ([wide]) or its poster puts the text over it. */
    fun overlays(wide: Boolean) = this == OVERLAY || (this == OVERLAY_WIDE && wide)
}

/** How a Home row draws its cards. */
enum class ShelfStyle(val label: String, val cardSizeLabel: String) {
    POSTER("Poster", "Poster Cards"),
    /** The focused poster opens to a 16:9 card with the title's backdrop (`tvLandscapeOnFocus`). */
    EXPANDING_POSTER("Poster, Expands on Focus", "Expanding Poster Cards"),
    LANDSCAPE("Landscape", "Landscape Cards");

    companion object {
        /** Shelves the Expo TV home draws landscape; the rest are posters. */
        fun isLandscapeByDefault(config: ShelfConfig) = config.id in landscapeIds || config.type == "collection-hub"

        private val landscapeIds = setOf(
            "continue-watching", "my-upcoming", "watch-something", "dashboard", "streaming-services",
            "recently-watched", "watch-together", "tonight", "live-favorites",
        )
    }
}

/** The Home layout values the profile's `homeShelves` carry; the device settings override each. */
data class ProfileHomeLayout(
    val focusPosition: FocusedCardPosition = FocusedCardPosition.LEFT,
    val expandPosters: Boolean = true,
    val cardScale: Double = 1.0,
    val heroScale: Double = 1.0,
) {
    val heroSize: HeroSize get() = HeroSize.fromProfile(heroScale)

    companion object {
        fun from(focusModel: String?, disableCardExpansion: Boolean?, shelfScale: Double?, heroScale: Double?) = ProfileHomeLayout(
            focusPosition = FocusedCardPosition.entries.firstOrNull { it.name.equals(focusModel, ignoreCase = true) } ?: FocusedCardPosition.LEFT,
            expandPosters = disableCardExpansion != true,
            cardScale = clamp(shelfScale),
            heroScale = clamp(heroScale),
        )

        /** As the Expo app reads the scales: unset or non-positive is 1, else 0.5–1. */
        private fun clamp(scale: Double?) = if (scale == null || !scale.isFinite() || scale <= 0) 1.0 else scale.coerceIn(0.5, 1.0)
    }
}

/** The old "Home Style" pref (Classic / Carousel) as the two settings (Top Carousel, Focused Title Info). */
object HomeStyleMigration {
    const val OLD_KEY = "homeStyle"

    fun migrate(old: String?): Pair<Boolean, Boolean> = if (old == "CAROUSEL") true to false else false to true
}

/** Which Home Layout sub-settings show for the two switches. */
data class HomeLayoutOptions(val topCarousel: Boolean, val focusedInfo: Boolean) {
    val showsCarouselRow get() = topCarousel
    val showsHeroSize get() = focusedInfo
    /** Hero Text Size scales the Focused Title Info and the carousel's text. */
    val showsTextSize get() = topCarousel || focusedInfo
    val textSizeLabel get() = when {
        topCarousel && focusedInfo -> "Hero & Carousel Text Size"
        topCarousel -> "Carousel Text Size"
        else -> "Hero Text Size"
    }
}

/** Card sizes chosen for one kind of row, as saved in preferences ("LANDSCAPE=SMALL"). */
object StyleCardSizes {
    fun encode(sizes: Map<ShelfStyle, CardSize>): Set<String> = sizes.map { "${it.key.name}=${it.value.name}" }.toSet()

    fun decode(saved: Set<String>): Map<ShelfStyle, CardSize> = saved.mapNotNull { entry ->
        val style = ShelfStyle.entries.firstOrNull { it.name == entry.substringBefore('=') }
        val size = CardSize.entries.firstOrNull { it.name == entry.substringAfter('=') }
        if (style != null && size != null) style to size else null
    }.toMap()

    /** A style's card scale: its own size, else [cardSize]'s (the Card Size setting). */
    fun scale(sizes: Map<ShelfStyle, CardSize>, style: ShelfStyle, cardSize: Double) = sizes[style]?.scale ?: cardSize
}

/** A row the profile shows, for the per-row settings. */
data class ShelfLayoutInfo(val id: String, val title: String, val landscapeByDefault: Boolean)

/**
 * The Home layout choices made on this device, on top of the profile's values. A null choice
 * follows the profile. Saved in preferences; Compose state, so Home redraws as they change.
 */
object HomeLayoutStore {
    private val prefs by lazy { App.context.getSharedPreferences("strmr.homeLayout", Context.MODE_PRIVATE) }

    var profile by mutableStateOf(ProfileHomeLayout())
        private set
    var shelves by mutableStateOf<List<ShelfLayoutInfo>>(emptyList())
        private set

    var focusPosition by mutableStateOf(enumOrNull<FocusedCardPosition>("focusPosition"))
        private set
    var expandPosters by mutableStateOf(if (prefs.contains("expandPosters")) prefs.getBoolean("expandPosters", true) else null)
        private set
    var heroSize by mutableStateOf(enumOrNull<HeroSize>("heroSize"))
        private set
    /**
     * "Animate Expansion": an expanding poster opens and folds in motion, the row gliding with
     * it (device only, on by default); off, the card and the row snap into place.
     */
    var animateExpansion by mutableStateOf(prefs.getBoolean(ANIMATE_EXPANSION, true))
        private set
    init { migrateHomeStyle() }

    /**
     * "Top Carousel": an independent carousel fed by one row above the rows.
     * Device only (the server has no such field); off by default.
     */
    var topCarousel by mutableStateOf(prefs.getBoolean(TOP_CAROUSEL, false))
        private set
    /** "Focused Title Info": the Classic hero (backdrop, logo, line, synopsis) following the focused card. On by default. */
    var focusedInfo by mutableStateOf(prefs.getBoolean(FOCUSED_INFO, true))
        private set
    /** "Shelf Transition" under the full hero (device only): Slide by default. */
    var shelfTransition by mutableStateOf(enumOrNull<ShelfTransition>("shelfTransition") ?: ShelfTransition.SLIDE)
        private set
    /** The row the carousel shows; null picks Top 10, else Trending, else the first row with titles. */
    var carouselRow by mutableStateOf(prefs.getString("carouselRow", null))
        private set
    var heroTextSize by mutableStateOf(enumOrNull<HeroTextSize>("heroTextSize") ?: HeroTextSize.MEDIUM)
        private set
    var cardSize by mutableStateOf(enumOrNull<CardSize>("cardSize"))
        private set
    /**
     * The card text (device only; the server has no such field, so it defaults to Below).
     * Overlay and None fit more rows. Reads the old on/off "cardTitles" choice as Below / None.
     */
    var cardText by mutableStateOf(
        enumOrNull<CardText>("cardText") ?: if (prefs.getBoolean("cardTitles", true)) CardText.BELOW else CardText.NONE,
    )
        private set
    /** "View Switcher on Home": the Home View pill under the tab bar (device only, off by default). */
    var viewSwitcher by mutableStateOf(prefs.getBoolean("viewSwitcher", false))
        private set
    var shelfStyles by mutableStateOf(readStyles())
        private set
    /** Card sizes chosen for one kind of row (device only); a kind not listed takes Card Size. */
    var styleCardSizes by mutableStateOf(readStyleCardSizes())
        private set
    /** Rows hidden from Home on this device; they still load, so showing one again is immediate. */
    var hiddenShelves by mutableStateOf(prefs.getStringSet("hiddenShelves", emptySet())!!.toSet())
        private set

    val effectiveFocusPosition get() = focusPosition ?: profile.focusPosition
    val effectiveExpandPosters get() = expandPosters ?: profile.expandPosters
    /** Whether any row on Home draws expanding posters: by default, or set on a row. */
    val usesExpandingPosters get() = effectiveExpandPosters || ShelfStyle.EXPANDING_POSTER in shelfStyles.values
    val effectiveHeroSize get() = heroSize ?: profile.heroSize
    /** The same Card Size in every Top Carousel / Focused Title Info combination. */
    val effectiveCardScale get() = cardSize?.scale ?: profile.cardScale

    /** The card scale of rows drawn in a style: its own size, else Card Size's. */
    fun cardScale(style: ShelfStyle) = StyleCardSizes.scale(styleCardSizes, style, effectiveCardScale)

    fun chooseFocusPosition(v: FocusedCardPosition?) { focusPosition = v; putEnum("focusPosition", v) }
    fun chooseExpandPosters(v: Boolean?) {
        expandPosters = v
        prefs.edit().apply { if (v == null) remove("expandPosters") else putBoolean("expandPosters", v) }.apply()
    }
    fun chooseAnimateExpansion(v: Boolean) { animateExpansion = v; prefs.edit().putBoolean(ANIMATE_EXPANSION, v).apply() }
    fun chooseHeroSize(v: HeroSize?) { heroSize = v; putEnum("heroSize", v) }
    fun chooseTopCarousel(v: Boolean) {
        topCarousel = v; prefs.edit().putBoolean(TOP_CAROUSEL, v).apply()
        AppLog.i(CAROUSEL_LOG, "top carousel ${onOff(v)}")
    }
    fun chooseFocusedInfo(v: Boolean) {
        focusedInfo = v; prefs.edit().putBoolean(FOCUSED_INFO, v).apply()
        AppLog.i(CAROUSEL_LOG, "focused title info ${onOff(v)}")
    }
    fun chooseCarouselRow(v: String?) {
        carouselRow = v
        prefs.edit().apply { if (v == null) remove("carouselRow") else putString("carouselRow", v) }.apply()
        AppLog.i(CAROUSEL_LOG, "carousel row ${v ?: "default"}")
    }
    fun chooseHeroTextSize(v: HeroTextSize) { heroTextSize = v; putEnum("heroTextSize", v) }
    fun chooseShelfTransition(v: ShelfTransition) { shelfTransition = v; putEnum("shelfTransition", v) }
    fun chooseCardSize(v: CardSize?) { cardSize = v; putEnum("cardSize", v) }
    /** Sets one kind of row's card size; null follows Card Size. */
    fun chooseStyleCardSize(style: ShelfStyle, v: CardSize?) {
        styleCardSizes = if (v == null) styleCardSizes - style else styleCardSizes + (style to v)
        prefs.edit().putStringSet(STYLE_CARD_SIZES, StyleCardSizes.encode(styleCardSizes)).apply()
    }
    fun chooseCardText(v: CardText) { cardText = v; putEnum("cardText", v) }
    fun chooseViewSwitcher(v: Boolean) { viewSwitcher = v; prefs.edit().putBoolean("viewSwitcher", v).apply() }

    fun defaultStyle(shelf: String): ShelfStyle = when {
        shelves.any { it.id == shelf && it.landscapeByDefault } -> ShelfStyle.LANDSCAPE
        effectiveExpandPosters -> ShelfStyle.EXPANDING_POSTER
        else -> ShelfStyle.POSTER
    }

    fun style(shelf: String) = shelfStyles[shelf] ?: defaultStyle(shelf)

    /** Sets a row's style; null returns it to the default. */
    fun setStyle(style: ShelfStyle?, shelf: String) {
        shelfStyles = if (style == null) shelfStyles - shelf else shelfStyles + (shelf to style)
        prefs.edit().putStringSet("shelfStyles", shelfStyles.map { "${it.key}=${it.value.name}" }.toSet()).apply()
    }

    fun isHidden(shelf: String) = shelf in hiddenShelves

    fun setHidden(hidden: Boolean, shelf: String) {
        hiddenShelves = if (hidden) hiddenShelves + shelf else hiddenShelves - shelf
        prefs.edit().putStringSet("hiddenShelves", hiddenShelves).apply()
    }

    /** Called by Home after loading the profile's settings and planning its rows. */
    fun update(profile: ProfileHomeLayout, shelves: List<ShelfLayoutInfo>) {
        if (profile != this.profile) this.profile = profile
        if (shelves != this.shelves) this.shelves = shelves
    }

    /** The first pass's one "Home Style" choice becomes the two settings, then its key goes. */
    private fun migrateHomeStyle() {
        if (!prefs.contains(HomeStyleMigration.OLD_KEY)) return
        val (carousel, info) = HomeStyleMigration.migrate(prefs.getString(HomeStyleMigration.OLD_KEY, null))
        prefs.edit().putBoolean(TOP_CAROUSEL, carousel).putBoolean(FOCUSED_INFO, info).remove(HomeStyleMigration.OLD_KEY).apply()
        AppLog.i(CAROUSEL_LOG, "migrated home style: top carousel ${onOff(carousel)}, focused info ${onOff(info)}")
    }

    private fun onOff(v: Boolean) = if (v) "on" else "off"

    private const val TOP_CAROUSEL = "topCarousel"
    private const val FOCUSED_INFO = "focusedInfo"
    private const val STYLE_CARD_SIZES = "styleCardSizes"
    private const val ANIMATE_EXPANSION = "animateExpansion"

    private inline fun <reified E : Enum<E>> enumOrNull(key: String): E? =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    private fun putEnum(key: String, v: Enum<*>?) = prefs.edit().apply { if (v == null) remove(key) else putString(key, v.name) }.apply()

    private fun readStyles(): Map<String, ShelfStyle> = prefs.getStringSet("shelfStyles", emptySet())!!.mapNotNull { entry ->
        val id = entry.substringBeforeLast('=')
        ShelfStyle.entries.firstOrNull { it.name == entry.substringAfterLast('=') }?.let { id to it }
    }.toMap()

    private fun readStyleCardSizes() = StyleCardSizes.decode(prefs.getStringSet(STYLE_CARD_SIZES, emptySet())!!)
}
