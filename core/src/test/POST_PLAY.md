# Playback completion validation

Run `:core:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest`,
then build each debug APK. The completion policy, scoped editor, observable
profile context, internal navigation and visible source eligibility have
regression coverage. The paired backend supplies genuine played history,
trusted catalogue delivery, current policy and negative-aware suggestions.

The end screen offers a voluntary rating for a movie or the whole series when
no known next episode is available. Unknown local episode ordering is not proof
that the series has ended. Skip writes nothing; an existing rating is editable.
Details, explicit Play and My List reuse their existing routes and stores.

On physical TV hardware, finish a movie and a final known episode. Confirm
Close receives initial focus, suggestions do not move it and every action is
reachable by remote. Back closes the panel. On phone and tablet, check narrow
portrait, landscape, large text, accessibility labels and picture-in-picture
return. Verify late suggestion arrivals, network failures, a lost rating save
acknowledgement, unlock expiry and a profile/server change during each request.
Native rendering, remote focus, accessibility, decoded audio and these device
checks remain pending; JVM tests and successful APK builds do not establish them.

The paired web browser tests render the production template and decode generated
video. Their screenshots are web reference evidence, not native screenshots.
Real application API checks use only an isolated synthetic loopback demo fixture.
No production deployment, device installation or private M3U publication occurs.
