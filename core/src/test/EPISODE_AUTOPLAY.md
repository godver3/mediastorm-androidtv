# Profile next-episode autoplay

Run `:core:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest`, then build each app with `:app:assembleDebug` and `:mobile:assembleDebug`. No private prefill properties are required.

`EpisodeAdvancePolicyTest` drives the policy used by `UpNext` with a deterministic monotonic clock: five seconds after genuine media end, seven at a known credits marker only when both choices allow it, pause/buffering, seeking back, explicit cancellation, repeated/late end events, errors, unknown duration and one transition. `EpisodeAutoplaySettingsTest` decodes real wire shapes and checks that only the profile controls autoplay; the device can still control credits skipping. A successful legacy profile read defaults autoplay to on, while an unavailable profile remains off for automatic transitions.

`EpisodeAutoplayEditorTest` checks confirmed selection, unavailable reads, retry after a lost acknowledgement, duplicate submissions and late profile or closed-screen responses. The setting is under Playback on TV and under This profile in the phone's Playback page. It patches only `nextEpisode` through the existing profile skipping endpoint. An old server that rejects the new field produces a visible save error.

`EpisodeAutoplayRequestTest` checks the next supplied aired episode across a season boundary and repeated preparation of the same target. A source failure does not consume the following episode. The existing coordinator resolves that target or shows its normal error and retry flow.

On TV, Play/Pause holds the countdown at natural media end and Back dismisses the next-episode pill. The phone card has Play, Pause/Resume countdown and Dismiss. Disabling autoplay keeps manual Next Episode available. Live/catch-up/growing-recording recovery runs before the episode-end decision.

The paired backend includes Chromium checks of the production HTML player with decoded generated video and an explicitly synthetic unavailable next source. Those screenshots are web rendering evidence. They do not establish physical Android/Fire TV rendering, remote focus, audio output or device accessibility acceptance, which still need device testing. No physical device installation or production deployment is part of this validation.
