# DESIGN-60 — player restores the wrong feed after back navigation

## Objective

Issue #60 (verbatim): "start by playing 1st feed; click a link to 2nd feed (creator feed);
start playing 2nd feed; go back the 2nd feed; go back to 1st feed player; currently player
plays the 2nd feed; expected: player to play 1st feed."

After opening a creator feed from the swipe player's overlay, BACK from the creator feed
landed the FIRST feed's player again — and that stale player played feed 2's content.

## Evidence

- `app-mobile/src/main/kotlin/com/rjbiermann/giffyviewer/mobile/MainActivity.kt:221`
  (mobile player `onOpenFeed` seam, AGENTS-APP.md "Player cluster opens are route pushes
  now") popped with `popUpTo(entry.id) { inclusive = true }`.
- Same idiom at `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvMainActivity.kt`
  `:249` (TvFeed sort replace) and `:279`/`:284` (TvPlayer onOpenCreator/onOpenNiche).

## Root cause

`entry` is a `NavBackStackEntry`; its `id` is a per-entry **UUID String** (verified:
`NavBackStackEntry.getId(): String` in navigation-common 2.10.2 bytecode), NOT a
destination id or route. The `popUpTo(String)` DSL overload matches **destination
routes**. A UUID never matches, so `NavControllerImpl.popBackStackInternal` logs
"Ignoring popBackStack to destination … as it was not found on the current back stack"
(navigation-runtime 2.10.2 bytecode, INFO log) and silently **does not pop**.

Net effect of the broken seam: the creator feed is pushed ON TOP of the live player:
stack `[feed1, PLAYER1, feed2]` instead of `[feed1, feed2]`. Walking BACK from feed 2
relaunches the covered `PLAYER1`, whose pages come from the **activity-scoped shared
`FeedViewModel`** (MainActivity.kt:172 — the §3.3 rule) that feed 2's
`MainFeedDestination` re-keyed via `open(creator)` (MainActivity.kt:303-309
`LaunchedEffect(srcResolved)`). The stale player therefore plays feed 2. Matches the
reported repro line for line — including "go back to 1st feed player" (BACK literally
lands the player entry).

Everything else checked and ruled out: `FeedScreen`'s entry source re-assertion works
(LaunchedEffect re-runs on recomposition), the inline-player parking/adoption guard
(A4, FeedScreen.kt `LaunchedEffect(source.keyBase)`) stops the parked player on source
swap, and `PlayerPage`'s `LaunchedEffect(gif.id)` re-fires on item swaps. The bug is
purely the navigation pop.

## Approach (smallest complete change)

One tested seam, four call sites:

- New `feature/feed/src/main/kotlin/com/rjbiermann/giffyviewer/feature/feed/PoppedEntryReplace.kt`:
  `NavController.navigateReplacing(route: Any, popped: KClass<P>)` =
  `navigate(route) { popUpTo(popped) { inclusive = true } }`. The KClass overload
  (`NavOptionsBuilder.popUpTo(KClass)`) matches the TOPMOST destination of that route
  class — popBackStackInternal walks the back stack reversed, so the topmost match is
  the entry the caller sits on at all four sites. The popped-entry replace semantics of
  AGENTS-APP.md are preserved exactly; only the wrong popUpTo argument was the bug.
- `MainActivity.kt` onOpenFeed → `navigateReplacing(MainFeed(source), MainPlayer::class)`.
- `TvMainActivity.kt` onSortChange → `navigateReplacing(TvFeed(sorted), TvFeed::class)`;
  TvPlayer onOpenCreator → `navigateReplacing(TvFeed(Creator), TvPlayer::class)`;
  onOpenNiche → `navigateReplacing(TvFeed(niche), TvPlayer::class)`.
- `feature/feed/build.gradle.kts`: `testImplementation(libs.navigation.compose)` only —
  the JVM/Robolectric regression rung drives the seam.

## TDD

1. RED: `PlayerClusterReplaceNavTest` (Robolectric, `:feature:feed` JVM rung) replicated
   the pre-fix host idiom verbatim and asserted the correct end state — failed with
   "BACK from the opened feed must land the covered feed, never the stale player"
   (the feed had stacked over a live player).
2. GREEN: same test now drives the production helper — passes; full
   `:feature:feed:testDebugUnitTest` suite 111 tests / 0 failures.

## Acceptance conditions

1. Mobile: player feed 1 → overlay creator link → feed 2 opens in the player's stack
   slot (player entry gone); BACK from feed 2 lands feed 1 (grid re-asserts its source);
   reopening the player plays feed 1.
2. TV: player onOpenCreator/onOpenNiche replace the player entry (BACK lands the covered
   surface, per the host comment); sort change replaces the current feed entry.
3. JVM regression test green: BACK after a cluster open never lands a player destination.
4. No behavior change for plain pushes without popUpTo (hop semantics untouched).

## Non-goals

- No PlayerScreen/FeedViewModel state changes (the shared-VM pager and adoption paths
  are correct).
- No emulator/device gate in this batch (separate serialized gate phase; device-focus
  suggestion below).
- No new features, no route-shape changes.
