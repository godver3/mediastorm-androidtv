# Live channel grouping review

Both clients keep their existing Material theme and channel components. A
full-width outlined header groups server-verified variants and exposes the
loaded source count, provider country/language hints and a separate Hide or
Show action. Headers do not infer identity from names or audio language.
All loaded variants start expanded. Each channel retains its source label,
existing playback entry, guide data, favorite and hidden actions.

Stable source-aware entry keys retain selection identity when pages arrive.
Collapsing leaves the first real source visible. The guide remains a flat
time grid. A manual load-more action remains available for collapsed or
fully deduplicated pages and failed page requests.

The browser reference renders exported production model snapshots. It can
check reference overflow, expansion and source identity, but cannot prove
native pixels, TV remote focus, large text, accessibility or decoded media.
Those checks require a physical device and are not marked passed.
