---
name: giffy-regression-patterns
description: "Checklist of bug classes that actually recurred in this repo, with their fix shapes — for code review, bug diagnosis, and pre-merge self-review. Use when reviewing a diff, fixing a feed/player/paging bug, or wondering why UI state misbehaves."
---

# Giffy Viewer — Recurring Regression Patterns

Each pattern below was a real bug fixed here. When reviewing changes, check the diff
against this list.

## State & flow collection

- **Pref-watcher phantom refresh**: a DataStore re-subscription wave re-emits the INITIAL
  state; `drop(1)` alone swallows only the first emission so identical replays pass
  `distinctUntilChanged` and force-restart pagers under the user. Fix shape:
  `.distinctUntilChanged().drop(1)` — dedupe BEFORE drop.
- **Session-wide dedup survives pager restarts**: Favorites round-robin re-lists ids →
  everything deduped to empty ("No favorites yet" with data). Dedup must be
  per-generation, not static/session-wide.
- **Silent no-op callbacks**: a bare `{ }` lambda passed as a state callback (auto-swipe
  advance) is a silent feature kill. Grep for empty lambdas on state paths.

## Composition & remember

- **Missing remember keys**: `playError` had to be `remember(gif.id)`-keyed or the error
  overlay stuck after navigating past a failed item. Any state object captured across
  item switches needs the item key.
- **Flow reads in composition** (`FlowOperatorInvokedInComposition`) are real
  recomposition-observation bugs — hoist to `remember{}` / VM.

## Layout measurement

- **Shift-without-grow**: a layout modifier that places content at `-offsetPx` must ALSO
  measure it `maxHeight + offsetPx` — otherwise the bottom strip is off-screen and hits
  NO scrollable (the app-wide scroll-jam bug).
- **Surface is not a layout container**: tv-material `Surface` content needs its own
  Column for stacking/overlap.
- **Double insets**: Scaffold `innerPadding` already includes nav-bar insets; adding
  `windowInsetsPadding(WindowInsets.navigationBars)` after it doubles the gap.
- **IME on edge-to-edge targetSdk 35**: the window no longer auto-resizes —
  `adjustResize` + `imePadding()` where text fields exist.

## Async races

- **Stale async clobbering newer state**: a late "un-settle" from a walked-away card
  overwrote a newer settle. Guard with the current target id (settledId pattern) and
  unit-test the race.
- **Adopted-state continuity bugs**: when a screen adopts a shared/parked player, decide
  explicitly whether to resume or preserve parked state — "preserve for continuity" made
  the player open paused.

## Navigation

- **Polymorphic @Serializable nav args**: nav 2.10 auto-derives NavTypes only for
  non-polymorphic args — sealed hierarchies (FeedSource/Niche) need the explicit
  `RouteNavTypes.kt` pattern (canon in this repo).
- **Route-arg wire format**: raw JSON with `https://…` injects `/` path segments →
  "cannot be found in the navigation graph" crash. Wrap URL-bearing args URL-safe-Base64,
  slash-free, with a regression test.
- Keep route args non-null (`RouteEncoder.encodeNull` routes through serializeAsValue
  even when nullable is allowed); use an arg-less start destination.

## Defensive degenerate cases

- Divide-by-zero on empty sets: Favorites pager guarded an empty favorite set →
  LoadResult.Error on every fresh install. Degenerate-empty beats Error.
- Copy branches that over-claim: "You're offline" hard-coded for all non-IOException
  errors lied about breaker-open 429 exhaustion — branch on the actual exception type.

## Meta-lesson

Static gates (compile/ktlint/detekt/unit tests) have repeatedly passed code with
crash-class device-only bugs. A diff that touches UI behavior owes a device gate
(giffy-device-gate), not just green checks.
