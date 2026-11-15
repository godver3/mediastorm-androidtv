<!-- Hallmark · pre-emit critique: P4 H4 E3 S4 R5 V4 -->
# Personal area validation

The first personal-area package groups access to the existing Continue
Watching, watchlist, explicit Love preferences and custom lists. It does
not introduce another list dataset or claim a complete Home redesign.
Children keep their existing restricted Home; the aggregate is initially
unavailable for child profiles. Unknown counts are not replaced with zero.

Run `:core:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest`.
The checked run passes 750 tests, including 12 new personal-area and preview
regressions. TV and phone APKs build. Their hashes and protected-value scan
are recorded in `fixtures/personal-area/results.json`.

`PersonalAreaTest` covers profile admission, stable choices while loading,
unknown/empty counts, existing watchlist identity, the Love source, bounded
preview inputs, user-list order/deduplication and stable rename keys. A
preference revision refreshes artwork without pretending to be an item
count. Recommendation generation is not requested to decorate its tile.

The existing Compose Material palette, typography, navigation and focus
restoration are reused. `.hallmark/review.json` distinguishes a static
review from pending rendered checks. No native device/emulator rendering,
remote focus, viewport or accessibility acceptance is claimed. Backend API
and full browser validation are recorded in the paired backend package.
