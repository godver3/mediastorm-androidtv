# Source hint presentation checks

Run all native unit tests and build the TV/phone debug packages:

```sh
./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest \
  :app:assembleDebug :mobile:assembleDebug
```

`SourceHintTest` covers fresh and expired evidence, incomplete/contradictory
negative states, unsupported states, malformed optional JSON, legacy servers,
profile-specific title/watchlist mappings, chart provenance and local observation
cancellation. Virtual time proves expiry removes dimming while the card is still
observed. The presentation never disables a card or starts a provider request.

A negative requires a complete check with a recognized absence reason, valid
RFC3339 dates, an expiry after the current instant and a window no longer than
the server's maximum 24 hours. Clock uncertainty and incompatible data stay
unknown. Source candidates are labelled as search hints, not playable media.

Set `MEDIASTORM_SOURCE_HINT_ARTIFACTS` to an output directory to export synthetic
`decisions.json` through `SourceHintFixtureTest`. Copy `index.html` into that
directory and serve it on an explicitly selected local/private test address.
This reference reads the actual policy decisions. Its HTML layout is not the
native Compose layout. It uses no media files, provider credentials or playlists.

Real native card rendering, small poster badge overlap, accessibility reading,
remote focus, profile changes during navigation and device clock/resume behavior
still need TV/phone acceptance. Individual episode states, visible-item source
scheduling, local-library hints and a manual source-recheck endpoint are separate
features. Optional vendor codecs are not required for these unit tests.
