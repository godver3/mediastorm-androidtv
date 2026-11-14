# Native release reporting

Run `./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest`.
Build TV and phone test packages with `./gradlew :app:assembleDebug :mobile:assembleDebug`.
No server account or `secrets.properties` is needed. The unit fixtures use synthetic
prequeue IDs, releases and a replaceable `ReleaseReportGateway`.

`ReleaseReportsTest` checks explicit adoption after preparation, resume position at
the time of adoption, wrong-content reset, five language intents, report-only and
undo, transient manual retry, duplicate submission, cancellation during POST,
profile changes, failed and timed-out alternatives, affected track validation,
older servers, preservation of subtitle preferences, wire-model decoding and the
shared remote/touch menu choices.

This client requires the backend release-report endpoints. Playback without a
server prequeue binding (manual direct sources, downloads, trailers, live TV and
direct library playback) does not expose an unverified report action. Old servers
show an unavailable message instead of falling back to a global blacklist write.

Preparation selects and probes a source on the server. It does not buffer a second
video or start automatic audio analysis. Adoption opens the prepared source through
the existing player coordinator and preserves the server's track descriptors and
prequeue binding. The default resume choice is ten seconds back; wrong content
always starts at zero. Different cuts are not assumed to be aligned.

Physical TV remote focus, touch layout, decoder playback, seek accuracy and backend
integration still require device acceptance. Automated unit tests and APK builds
alone do not establish those properties. Prior saved progress for incorrectly
identified content is not repaired by this change.
