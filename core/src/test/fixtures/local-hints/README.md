# Local file source hints

`sourceType: local` with `reason: local_file_checked` describes a file and requested
audio track checked by the server. It is a separate readable LOCAL state and does
not dim artwork or disable card actions. Contradictory fields, foreign providers,
future/expired timestamps and freshness windows over two minutes remain unknown.
Ordinary remote candidates retain their existing presentation.

The existing title/episode observers carry this evidence without another client
provider request. The normal timer expires local evidence. Tests cover JSON,
markers, boundaries and virtual-time expiry. The shared backend browser fixture
uses generated media and real HTTP/FFprobe, but is not native rendering or playback.
Physical TV/phone focus, accessibility, resume and codec acceptance remain required.
