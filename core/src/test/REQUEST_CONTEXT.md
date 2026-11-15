# JSON request identity validation

Run `:core:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest`.
The added tests exercise the same context, authorization, refresh coalescing and
transport retry classes used by `ApiClient`. Three tests use real loopback HTTP
peers with synthetic credentials and controlled response barriers.

A prepared request retains its authentication generation and profile epoch.
Changing the server, pairing credential or account invalidates it. Changing
profiles invalidates profile requests while an account rotation may still finish
for that same account. Normal token rotation preserves peers, coalesces active
refreshes and never adopts a replacement account's token. A cancelled waiter
does not detach a shared refresh that is still running.

Old responses, rejected rotations and transport failures cannot sign out a new
account or retry under its credentials. A remote reconnect may retry once when
the identity and route remain unchanged. A changed proxy origin requires a new
request; the old prepared request is cancelled. Late recovery cannot replace a
new route. Proxy addresses and pairing credentials publish together. Distinct
pairings never share an opening; replacement waits for the old native operation
to stop, and unpairing cancels an active opening. Login, logout and PIN persistence commit only inside their captured
context, and server/session publication and local cleanup are serialized.
A PIN prompt also retains its own identity. Old submit/dismiss callbacks cannot
affect a new prompt, and cancellation before a reply prevents unlock persistence.
Concurrent waiters share a prompt without orphaning it when one is cancelled.

Rating, completion, watchlist writes and progress reporting pass the originating
profile epoch explicitly. Decoded generic replies are checked again after the
background parser returns. A delayed sign-out UI callback does not replace the
phase of a newer login.

No backend behavior or API contract changes. The paired backend's previously
recorded suite is separate evidence and is not represented as a new run here.
These JVM checks do not initialize Android Keystore, render native UI, establish
a real iroh pairing or verify a physical network handover. Direct media transfer
and pairing persistence outside the JSON request path are not covered by this
package. Check those device paths before a family rollout.
