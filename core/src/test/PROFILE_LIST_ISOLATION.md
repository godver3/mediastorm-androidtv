# Profile list isolation validation

Run `:core:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest`.
The checked run passes 738 tests, including 21 new asynchronous regressions.
Both debug APKs build; their hashes and the protected-value scan are in
`fixtures/profile-list-isolation/results.json`.

`ProfileListIsolationTest` drives the actual five store entry points with
suspended synthetic replies. It covers late results, same-profile reset,
A/B/A visits, old completion versus new request sharing, cancelled waiters,
retry, error privacy, partial rows and context changes before reset.
`ProfilePreviewCacheTest` covers the actual preview entry point, stale
results, same-ID context changes, expiry, LRU bounds and cancellation.

The data context is ephemeral and contains no persisted identity or token.
Account/server/remote-credential changes and profile activation invalidate it.
Reset detaches existing work instead of making a new profile wait for it.
Late local mutation acknowledgements do not repopulate new profile state;
this does not undo an operation already accepted by the server.

These are state and build checks. No emulator or physical device rendering,
remote focus or accessibility acceptance is claimed. No production backend,
private playlist or real provider input was used by the new tests.
