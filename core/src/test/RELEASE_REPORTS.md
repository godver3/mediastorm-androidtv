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
video or analyze audio samples. Technical playback-error suggestions are handled
separately by `AudioRecoveryPolicy`. Adoption opens the prepared source through
the existing player coordinator and preserves the server's track descriptors and
prequeue binding. The default resume choice is ten seconds back; wrong content
always starts at zero. Different cuts are not assumed to be aligned.

Physical TV remote focus, touch layout, decoder playback, seek accuracy and backend
integration still require device acceptance. Automated unit tests and APK builds
alone do not establish those properties. Prior saved progress for incorrectly
identified content is not repaired by this change.

## Technical audio recovery suggestions

`AudioRecoveryTest` checks fatal audio output errors and audio-renderer decoder
errors after the existing local fallback paths. Video, network and container errors
are ignored. Missing-track hints require server-expected audio, selected video,
actual playback progress and ten seconds without any audio tracks. Pause, zero
player volume, disabled audio, startup, seek jumps and clock resets do not establish
a missing-track defect. Presence of a silent track is not analyzed.

The existing Auto Switch Sources setting gates automatic preparation. There is one
automatic suggestion per playback and no further automatic audio preparation after
adopting that suggestion. The UI offers review and dismissal; it does not switch by
itself or save a report. Both manual and automatic menus share the prepared-source
flow, so opening the menu cannot duplicate source preparation. A deliberate audio
selection, another manual source, Stop or a profile/context change discards the
suggestion and releases its unused preparation.

The policy and gateway tests are synthetic. Device error delivery, TV focus, phone
layout, real decoder recovery and backend/provider integration need physical-device
acceptance. No empirical content-audio defect classification is claimed.

Explicit audio intent from the details/manual track panel also waits for its save
before automatic preparation. Unknown tags and subtitle-only changes leave audio
intent unchanged. The single automatic-attempt budget follows a source handoff
within the same episode; the next episode starts its own budget.
