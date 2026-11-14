# Visible title source hints

Home, browse and search cards on TV and phone submit only their clipped visible
title coordinates to one shared observer. The lifecycle, focused window and
playback/preparation state gate demand. Duplicate shelves share one request;
leaving the last visible instance releases it. Cards representing an episode,
a library entry or a non-title action do not inherit whole-series hints.

The protocol requires a catalogue response delivered to the same profile and
client. Projections expire after 30 minutes without active renewal; a server
restart or expired/evicted projection yields unknown until the catalogue reloads.
No client-supplied query/name or direct provider request is sent. The server
still owns metadata, language preferences, scoped reports and provider budget.

Tests use virtual time for batch changes, duplicates, late replies, profile
changes, explicit cleanup arrays, finite polling, ordinary renewal and window
visibility gates. The synthetic backend browser fixture demonstrates actual
intersection/scroll requests against real HTTP handlers and the worker. Its
HTML layout is not native Compose rendering or media playback.

Run core, TV and phone unit suites and build both debug APKs. Real device focus,
accessibility, rendering and resume acceptance remain before release.
