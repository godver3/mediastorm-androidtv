# Visible episode source hints

TV and phone episode rows submit at most 12 actual visible coordinates to the
profile-protected episode availability endpoint. A clipped, resumed row observes
its own view; playback and preparation overlays pause it. Profile, server and
account changes discard old responses. One view ID survives scroll batches, with
ordered versions and bounded cleanup. Pending checks poll quickly for at most
15 renewals before returning to the 15-second lease cadence.

The shared observer tests use virtual time and synthetic episodes. They cover
stale replies, wrong coordinates, duplicate rows, unsupported servers, explicit
empty-array cleanup, shared-view version ordering and a listed local copy. The
latter leaves remote absence unknown because a library entry does not prove that
the required local file or audio track works.

Source hints affect artwork only. Existing spoiler masking, focus, play actions,
watch progress and local indicators remain available. Series-wide hints are not
inherited by an episode. The backend synthetic browser preview demonstrates API
states and report undo, not native Compose rendering or playback.

Run `:core:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest`
and build both debug APKs. Physical TV/phone focus, accessibility, resume and
playback acceptance remain required before release.
