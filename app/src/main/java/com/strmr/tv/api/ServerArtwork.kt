package com.strmr.tv.api

import okhttp3.Interceptor

/**
 * Artwork the server serves itself (`/api/…`: Plex and Jellyfin posters and backdrops through the
 * library API), which needs the app's credentials. Its URL names the active profile (the server
 * checks its access to the library); [interceptor], on the image loader's client, adds the media
 * headers (token, profile unlock, remote-access credential) to requests for the server.
 */
object ServerArtwork {
    /** The server's URL for a server-relative path such as "/api/library/items/…/artwork/poster". */
    fun url(path: String): String {
        val query = path.substringAfter('?', "").split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        return ApiClient.url(path.substringBefore('?'), query + ("profileId" to ApiClient.profileId)).toString()
    }

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val base = ApiClient.baseUrl
        val forServer = request.url.host == base.host && request.url.port == base.port &&
            request.url.encodedPath.startsWith(base.encodedPath.trimEnd('/') + "/api/")
        if (!forServer) return@Interceptor chain.proceed(request)
        chain.proceed(request.newBuilder().apply { ApiClient.mediaHeaders.forEach { (name, value) -> header(name, value) } }.build())
    }
}
