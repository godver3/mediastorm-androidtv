package com.strmr.tv.api

import kotlinx.serialization.Serializable

@Serializable
data class Credits(val cast: List<CastMember> = emptyList())

@Serializable
data class CastMember(
    /** The TMDB person ID. */
    val id: Int,
    val name: String,
    val character: String? = null,
    val profileUrl: String? = null,
)

/** A movie collection ("The Matrix Collection"), as named on a movie's details. */
@Serializable
data class CollectionRef(val id: Int, val name: String)

/** `GET /api/metadata/collection`: the collection's movies. */
@Serializable
data class CollectionDetails(val id: Int, val name: String, val movies: List<Title>? = null)

/** A person a cast card opens. */
data class PersonRef(val id: Int, val name: String, val profileUrl: String?)

/** `GET /api/metadata/person`: the person and the titles they appear in. */
@Serializable
data class PersonDetails(val person: Person, val filmography: List<Title>? = null) {
    @Serializable
    data class Person(
        val id: Int,
        val name: String,
        val biography: String? = null,
        val profileUrl: String? = null,
        val knownFor: String? = null,
    )
}

/** A collection's movies in release order (undated last), as the Expo details page sorts them. */
suspend fun ApiClient.collectionMovies(id: Int): List<Title> =
    send<CollectionDetails>("GET", "/api/metadata/collection", mapOf("id" to id.toString())).movies.orEmpty()
        .sortedWith(compareBy<Title> { it.year?.takeIf { y -> y > 0 } ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })

suspend fun ApiClient.person(id: Int): PersonDetails = send("GET", "/api/metadata/person", mapOf("id" to id.toString()))
