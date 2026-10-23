package com.strmr.tv.home

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** The `homeShelves` fields Home reads, from the profile or the server. */
@Serializable
data class HomeFields(
    val shelves: List<ShelfConfig>? = null,
    val itemCap: Int? = null,
    val exploreCardPosition: String? = null,
    val excludeUpcomingFromContinue: Boolean? = null,
    val homeShelfFocusModel: String? = null,
    val disableTvLandscapeCardExpansion: Boolean? = null,
    val homeShelfScale: Double? = null,
    val homeHeroScale: Double? = null,
    val tvTopShelfMode: String? = null,
    val tvTopShelfSourceId: String? = null,
    val views: Map<String, HomeViewSettings>? = null,
) {
    /** A field this one doesn't set comes from `under` (a JS spread of `under`, then this). */
    fun over(under: HomeFields?): HomeFields = if (under == null) this else HomeFields(
        shelves = shelves ?: under.shelves,
        itemCap = itemCap ?: under.itemCap,
        exploreCardPosition = exploreCardPosition ?: under.exploreCardPosition,
        excludeUpcomingFromContinue = excludeUpcomingFromContinue ?: under.excludeUpcomingFromContinue,
        homeShelfFocusModel = homeShelfFocusModel ?: under.homeShelfFocusModel,
        disableTvLandscapeCardExpansion = disableTvLandscapeCardExpansion ?: under.disableTvLandscapeCardExpansion,
        homeShelfScale = homeShelfScale ?: under.homeShelfScale,
        homeHeroScale = homeHeroScale ?: under.homeHeroScale,
        tvTopShelfMode = tvTopShelfMode ?: under.tvTopShelfMode,
        tvTopShelfSourceId = tvTopShelfSourceId ?: under.tvTopShelfSourceId,
        views = views ?: under.views,
    )
}

/** A custom view's own layout fields over the inherited ones (`{ ...inherited, ...override }`). */
private fun HomeViewSettings.over(under: HomeFields) = HomeFields(
    shelves = shelves, itemCap = itemCap, exploreCardPosition = exploreCardPosition,
    excludeUpcomingFromContinue = excludeUpcomingFromContinue, homeShelfFocusModel = homeShelfFocusModel,
    disableTvLandscapeCardExpansion = disableTvLandscapeCardExpansion, homeShelfScale = homeShelfScale,
    homeHeroScale = homeHeroScale, tvTopShelfMode = tvTopShelfMode, tvTopShelfSourceId = tvTopShelfSourceId,
).over(under)

/**
 * The Home layout a view shows (`resolveHomeViewSettings`). All is Home itself. Another view
 * inherits Home unless it is `custom`, which brings its own layout fields and, when set, its own
 * shelves (an empty list: an empty page, without the top shelf). A top shelf source the view
 * doesn't allow falls back to the standard top shelf. Built-in rows a custom list leaves out
 * are added back disabled, and rows of the wrong media type are disabled.
 */
fun resolveHomeView(global: HomeFields?, profile: HomeFields?, view: String): HomeFields {
    val layered = (profile ?: HomeFields()).over(global)
    val base = layered.copy(shelves = mergeShelvesWithGlobal(global?.shelves.orEmpty(), profile?.shelves))
    if (view == HomeViews.ALL) return base
    val views = HomeViews.merge(global?.views, profile?.views)
    val override = views[view]
    if (override?.deleted == true) return base
    val filter = HomeViews.filter(view, views)
    val inherited = base.copy(shelves = ensureHomeShelfConfig(base.shelves.orEmpty()))
    val custom = override?.mode == "custom"
    var resolved = if (custom) override!!.over(inherited) else inherited
    if (custom && override!!.shelves?.isEmpty() == true) resolved = resolved.copy(tvTopShelfMode = override.tvTopShelfMode ?: "disabled")
    if (resolved.tvTopShelfMode == "shelf") {
        val source = resolved.shelves.orEmpty().firstOrNull { it.id == resolved.tvTopShelfSourceId }
        if (source == null || !homeViewShelfAllowed(source, filter)) resolved = resolved.copy(tvTopShelfMode = "default", tvTopShelfSourceId = null)
    }
    val shelves = if (resolved.shelves.orEmpty().isNotEmpty() || override?.shelves != null) resolved.shelves.orEmpty() else DefaultHomeShelves.all
    val ids = shelves.map { it.id }.toSet()
    // Disabled stand-ins keep the built-in rows from coming back.
    val complete = shelves + DefaultHomeShelves.all.filter { it.id !in ids }.map { it.copy(enabled = false) }
    return resolved.copy(shelves = complete.map { if (homeViewShelfAllowed(it, filter)) it else it.copy(enabled = false) })
}

private val shelfJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/**
 * The profile's shelves over the server's (`mergeHomeShelvesWithGlobal`): each profile shelf
 * takes the server shelf's fields it doesn't set, and server shelves the profile lacks follow.
 */
fun mergeShelvesWithGlobal(global: List<ShelfConfig>, profile: List<ShelfConfig>?): List<ShelfConfig> {
    if (profile == null) return global
    if (global.isEmpty()) return profile
    val byId = global.associateBy { it.id }
    val ids = profile.map { it.id }.toSet()
    val merged = profile.map { shelf ->
        val under = byId[shelf.id] ?: return@map shelf
        val fields = shelfJson.encodeToJsonElement(under).jsonObject + shelfJson.encodeToJsonElement(shelf).jsonObject
        shelfJson.decodeFromJsonElement<ShelfConfig>(JsonObject(fields))
    }
    return merged + global.filterNot { it.id in ids }
}
