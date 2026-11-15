# Native live station groups

TV and phone group channel entries using the backend's authoritative
`stationId`, never a client name or URL heuristic. All loaded variants start
expanded. A header can retain just the first real entry, then reveal every
loaded variant again. The per-profile device preference can turn grouping
off. Old servers without station IDs retain a flat list.

Header counts describe loaded, visible entries, not the server's total.
Provider country and language fields are display hints, not verified audio
tracks. Source labels, entry playback IDs, EPG keys, favorites and hidden
settings remain on individual entries. No new station-wide favorite store
is introduced. The guide remains a flat time grid with source-aware keys.

## Review and tests

Start with `LiveStationGroups`, `LivePageGate` and `LiveGuideData` in the
shared core. Both clients render the same row model in their existing
channel list. Owned child scopes and `ProfileDataContext` discard old pages,
guide replies and queued tuning on a profile or account change. HTTP starts
carry the originating context through authorization and media URL creation.
A late accepted server tune is not claimed to be undone by client rejection.

`LivePageGate` advances the offset by the raw returned page size before
presentation deduplication. Query snapshots include favorite membership.
Filter changes, forced reloads and A/B/A changes cannot accept stale pages;
an old completion cannot clear a replacement request's loading state.

Run the three suites and both applications separately:

```sh
./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :mobile:assembleDebug
```

The 29 new tests cover authoritative grouping, country/language labels,
legacy and invalid IDs, source-local duplicate channel IDs, collapsed state,
loaded counts, stable keys, raw page offsets, favorite snapshots, profile
switches and actual guide-controller responses held behind coroutine gates.
The full suite contains 882 passing cases: 749 core, 44 TV and 89 phone.
No Android application singleton is initialized by the model tests.

Set `MEDIASTORM_NATIVE_MODEL_ARTIFACTS` when running core tests to export
16 synthetic snapshots from the production grouping model. The checked-in
`fixtures/live-station-groups/reference.html` renders those snapshots, with
an explicit notice that it is an HTML model reference. Its browser checks
cover six widths, reference overflow, expansion, keyboard re-opening, source
identity and flat order. The images do not show native Compose rendering.

The backend is unchanged. Server-side authorized grouping and fallback come
from backend PR 84; source-slot rules come from PR 83. Expansion does not
prepare or probe a source. Selecting a source uses the existing server tune
path and its fallback rules, rather than adding a second client retry loop.

## Device acceptance

On TV, confirm initial channel focus, header navigation, collapse/re-open
and stable focus as pages arrive. On phone, check narrow widths, large text,
TalkBack, long-press actions and refresh. Exercise switching profiles while
pages, guide data and a tune are pending. Confirm source-specific selection,
EPG, favorite and hidden behavior on authorized multi-list data. Tune failure
and actual decoded playback need a real device and provider; no private
playlist was used in this fixture. These checks remain pending.
