package com.strmr.tv.sports

import com.strmr.tv.api.ApiClient

/** The race weekends in the providers' window, with every series' feed state. */
suspend fun ApiClient.sportsRaces(): SportsRaceBoard = send("GET", "/api/sports/races", timeoutMs = 60_000)

/** A session's latest snapshot: its entries or classification, with each entry's timing. */
suspend fun ApiClient.sportsRaceSession(session: SportsRaceEvent): SportsRaceEvent = send(
    "GET",
    "/api/sports/races/${segment(session.league)}/${segment(session.providerEventId)}/${segment(session.sessionId.orEmpty())}",
    timeoutMs = 60_000,
)

/** The MotoGP riders' championship. */
suspend fun ApiClient.sportsMotoGPStandings(): SportsMotoGPEnrichment = send("GET", "/api/sports/motogp/standings")

/** The organizer's guide to the circuit of a MotoGP weekend. */
suspend fun ApiClient.sportsMotoGPCircuit(weekend: SportsRaceEvent): SportsMotoGPEnrichment =
    send("GET", "/api/sports/motogp/circuit/${segment(weekend.providerEventId)}")

/** F1's timing archive of a finished session. */
suspend fun ApiClient.sportsF1Archive(session: SportsRaceEvent): SportsF1Archive = send(
    "GET",
    "/api/sports/f1/archive/${segment(session.providerEventId)}/${segment(session.sessionId.orEmpty())}",
    timeoutMs = 60_000,
)
