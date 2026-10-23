package com.strmr.tv.library

import com.strmr.tv.api.ApiJson
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.api.LibraryEntry
import com.strmr.tv.api.LibraryGroup
import com.strmr.tv.api.LibraryGroupPage
import com.strmr.tv.api.LibraryItem
import com.strmr.tv.api.LibraryMatch
import com.strmr.tv.api.LibraryRef
import com.strmr.tv.api.LibrarySource
import com.strmr.tv.home.HomeCard
import com.strmr.tv.home.HomeLink
import com.strmr.tv.home.ShelfConfig
import com.strmr.tv.player.PlaybackRequest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Library copies (local, Plex, Jellyfin): decoding the server's shapes, the best-first order Play
 * uses, episode mapping, the catalog ids a library card opens, library shelves and the progress a
 * library file reports.
 */
class LibraryVersionsTest {
    private fun item(
        id: String, width: Int, height: Int, codec: String, hdr: String? = null, gb: Double, source: String = "plex",
    ) = """{"id":"$id","fileName":"$id.mkv","sourceType":"$source","sizeBytes":${(gb * 1_000_000_000).toLong()},
           "probe":{"durationSeconds":6000,"videoCodec":"$codec","width":$width,"height":$height${hdr?.let { ",\"hdrFormat\":\"$it\"" } ?: ""}}}"""

    @Test fun versionsSortBestFirstWithQualityLabels() {
        val page = ApiJson.decodeFromString<LibraryGroupPage>(
            """{"groups":[{"id":"757910","groupType":"title","libraryType":"movie","title":"Example","tmdbId":1081003,
                "items":[${item("hd", 1920, 800, "h264", gb = 6.0)},${item("uhd", 3840, 1608, "hevc", "HDR10", 21.0)}]}],
               "total":1,"alphabetBuckets":["E"]}""",
        )
        assertEquals(1, page.total)
        assertEquals(listOf("E"), page.alphabetBuckets)
        val versions = LibraryVersions.of(page.groups[0], "Films")
        assertEquals(listOf("uhd", "hd"), versions.movie.map { it.id })
        // Wide 4K counts as 4K by its width, not "1608p".
        assertEquals("4K · HEVC · HDR10", versions.movie[0].quality)
        assertEquals("1080p · H.264", versions.movie[1].quality)
        assertEquals("Plex · Films", versions.movie[0].origin)
        assertEquals("21.0 GB · 28 Mbps", versions.movie[0].size)
        assertEquals("uhd", versions.best(null, null)?.id)
    }

    @Test fun episodesMapBySeasonAndEpisode() {
        val group = ApiJson.decodeFromString<LibraryGroup>(
            """{"id":"series:tmdb:tv:1","groupType":"show","libraryType":"show","title":"Show","tmdbId":1,
                "seasons":[{"seasonNumber":1,"episodes":[
                  {"episodeNumber":1,"items":[${item("s1e1", 1920, 1080, "h264", gb = 2.0, source = "local")}]},
                  {"episodeNumber":2,"items":[${item("s1e2", 1280, 720, "h264", gb = 1.0, source = "local")}]}]}]}""",
        )
        val versions = LibraryVersions.of(group, "TV")
        assertEquals("s1e2", versions.best(1, 2)?.id)
        assertNull(versions.best(1, 3))
        assertTrue(versions.movie.isEmpty())
        assertEquals("Local · TV", versions.best(1, 1)?.origin)
    }

    @Test fun matchesMergeLibrariesAndDropRepeatedFiles() {
        val matches = ApiJson.decodeFromString<List<LibraryMatch>>(
            """[{"libraryId":"plex","libraryName":"Films","group":{"id":"1","title":"Example","libraryType":"movie",
                  "items":[${item("a", 1920, 1080, "h264", gb = 8.0)}]}},
                {"libraryId":"jf","libraryName":"Movies","group":{"id":"2","title":"Example","libraryType":"movie",
                  "items":[${item("b", 3840, 2160, "hevc", gb = 40.0, source = "jellyfin")},${item("a", 1920, 1080, "h264", gb = 8.0)}]}}]""",
        )
        val versions = LibraryVersions.of(matches)
        assertEquals(listOf("b", "a"), versions.movie.map { it.id })
        assertEquals(LibrarySource.JELLYFIN, versions.movie[0].source)
    }

    private fun group(ids: String, show: Boolean = false) =
        ApiJson.decodeFromString<LibraryGroup>("""{"id":"g","title":"T","libraryType":"${if (show) "show" else "movie"}"$ids}""")

    @Test fun catalogTitleIdsAndTargets() {
        assertEquals("tmdb:movie:5", group(""","tmdbId":5""").catalogTitleId)
        assertEquals("tmdb:tv:5", group(""","tmdbId":5""", show = true).catalogTitleId)
        assertEquals("tvdb:series:7", group(""","tvdbId":7""", show = true).catalogTitleId)
        assertEquals("tt1", group(""","imdbId":"tt1"""").catalogTitleId)
        assertNull(group("").catalogTitleId)

        val matched = DetailsTarget.fromLibrary(group(""","tmdbId":5,"imdbId":"tt1""""))!!
        assertTrue(matched.fromLibrary)
        assertEquals("5", matched.tmdbId)
        assertNull(DetailsTarget.fromLibrary(group("")))
        assertFalse(DetailsTarget.unmatched(group("")).hasCatalogIds)
    }

    @Test fun libraryShelfIds() {
        fun shelf(json: String) = ApiJson.decodeFromString<ShelfConfig>(json)
        assertEquals("abc", shelf("""{"id":"s1","type":"library","libraryId":"abc"}""").libraryID)
        assertEquals("xyz", shelf("""{"id":"local-library-xyz","type":"local-library"}""").libraryID)
        assertNull(shelf("""{"id":"trending-movies","type":"builtin"}""").libraryID)
    }

    @Test fun libraryCardsOpenDetailsOrTheirOwnLink() {
        val library = LibraryRef("lib", "Films", "movie", "plex")
        val matched = HomeCard.from(LibraryEntry(library, group(""","tmdbId":9""")))
        assertTrue((matched.link as HomeLink.Details).target.fromLibrary)
        assertTrue(HomeCard.from(LibraryEntry(library, group(""))).link is HomeLink.Library)
    }

    private fun version(id: String) = LibraryVersion(LibraryItem(id), "Films")

    @Test fun libraryProgressNamesItsSourcePath() {
        val target = DetailsTarget("tmdb:movie:9", "M", true, 2020, tmdbId = "9", posterUrl = "/api/library/items/x/artwork/poster")
        val request = PlaybackRequest(target)
        assertNull(request.progressReport(10.0, 100.0, false, false, "debrid/x.mkv")["sourcePath"])
        val body = request.copy(libraryVersion = version("i")).progressReport(10.0, 100.0, false, false, "plexmedia:i")
        assertEquals("plexmedia:i", body["sourcePath"]?.jsonPrimitive?.content)
        assertEquals("tmdb:movie:9", body["itemId"]?.jsonPrimitive?.content)
        // A server-relative library poster means nothing in a notification.
        assertNull(body["posterUrl"])
    }

    @Test fun unmatchedFileProgressIsKeyedByItsStreamPath() {
        val request = PlaybackRequest(DetailsTarget.unmatched(group("")), libraryVersion = version("i"))
        val body = request.progressReport(10.0, 100.0, false, false, "plexmedia:i")
        assertEquals("plexmedia:i", body["itemId"]?.jsonPrimitive?.content)
        assertEquals("T", body["movieName"]?.jsonPrimitive?.content)
        assertNull(body["externalIds"])
        assertNull(body["seriesId"])
        assertEquals("plexmedia:i", request.streamMetadata("plexmedia:i")["itemId"])
        assertNull(request.streamMetadata("plexmedia:i")["titleId"])
    }

    @Test fun nextEpisodePlaysItsOwnLibraryCopy() {
        val show = ApiJson.decodeFromString<LibraryGroup>(
            """{"id":"g","title":"Show","libraryType":"show","tmdbId":1,"seasons":[{"seasonNumber":1,"episodes":[
                {"episodeNumber":1,"items":[{"id":"e1"}]},{"episodeNumber":2,"items":[{"id":"e2"}]}]}]}""",
        )
        fun episode(n: Int) = PlaybackRequest.EpisodeInfo(1, n)
        val request = PlaybackRequest(
            DetailsTarget.fromLibrary(show)!!, episode(1), following = listOf(episode(2), episode(3)),
            library = LibraryVersions.of(show, "TV"),
        )
        assertEquals("e1", request.preferredLibraryVersion?.id)
        // The copy picked for this episode doesn't carry over to the next.
        assertEquals("e2", request.copy(libraryVersion = version("e1")).nextEpisode?.preferredLibraryVersion?.id)
        assertNull(request.nextEpisode?.nextEpisode?.preferredLibraryVersion)
    }
}
