package com.strmr.tv.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strmr.tv.app.App
import kotlinx.serialization.json.Json

/**
 * The Home view on show (All, Movies, Shows or a custom page) and the profile's views. The Home
 * tab's view ([home]) is remembered per profile on this device (the Expo app resets it), and
 * falls back to All once the profile's views no longer have it. A view's own tab shows its view
 * ([show]) without changing [home]. Compose state; main thread only.
 */
object HomeViewStore {
    private val prefs by lazy { App.context.getSharedPreferences("strmr.homeView", Context.MODE_PRIVATE) }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private var profileId: String? = null

    /** The profile's views, merged with the server's (last loaded, then as Home loads them). */
    var views by mutableStateOf<Map<String, HomeViewSettings>>(emptyMap())
        private set
    /** The view on show: [home] on the Home tab, or the view of the view tab open. */
    var current by mutableStateOf(HomeViews.ALL)
        private set
    /** The Home tab's own view, remembered per profile. */
    var home by mutableStateOf(HomeViews.ALL)
        private set
    /** The views have been read from the server since the profile was chosen. */
    private var loaded = false

    val ids: List<String> get() = HomeViews.ids(views)
    val filter: HomeMediaFilter get() = HomeViews.filter(current, views)

    fun label(view: String) = HomeViews.label(view, views)

    /** The views Movies, Shows and custom pages, in picker order. */
    val pageIds: List<String> get() = ids.filter { it != HomeViews.ALL }

    /** Switches to a profile: its last view and views on this device, until Home loads its current ones. */
    fun activate(profileId: String) {
        if (profileId == this.profileId) return
        this.profileId = profileId
        loaded = false
        views = prefs.getString("views.$profileId", null)
            ?.let { runCatching { json.decodeFromString<Map<String, HomeViewSettings>>(it) }.getOrNull() } ?: emptyMap()
        home = HomeViews.parse(prefs.getString("view.$profileId", null))
        current = home
    }

    /** The profile's current views, from a Home load; a view that has gone falls back to All. */
    fun update(next: Map<String, HomeViewSettings>) {
        loaded = true
        if (next != views) {
            views = next
            profileId?.let { prefs.edit().putString("views.$it", json.encodeToString(next)).apply() }
        }
        if (home !in ids) keepHome(HomeViews.ALL)
        if (current !in ids) current = HomeViews.ALL
    }

    /** Shows [view] on the Home tab and remembers it as Home's view. */
    fun select(view: String) {
        keepHome(valid(view))
        current = home
    }

    /** Shows [view] for its own tab, leaving Home's remembered view as it is. */
    fun show(view: String) {
        current = valid(view)
    }

    private fun valid(view: String): String {
        val next = HomeViews.parse(view)
        // Before the views load, a page from the cache is trusted; Home falls back if it's gone.
        return if (next in ids || (!loaded && HomeViews.isCustom(next))) next else HomeViews.ALL
    }

    private fun keepHome(view: String) {
        home = view
        profileId?.let { prefs.edit().putString("view.$it", view).apply() }
    }
}
