# STREAM-REPORT-player.md — player/stream issue batch (#2 #3 #4 #8 #9 #16 #20 #21 #24 #26 #27)

Implemented per DESIGN-player-stream.md's spec and per-issue minimal changes, file-grouped, in the
doc's two-wave order. NO gradle/ktlint/detekt/test/jj/git/emulator commands were run in the workspace
(see Protocol note at bottom for one read-only exception discovered mid-session); verification is a
greps + re-read diff review. None of the issues needed the network probe.

## Files touched (all edits, nothing else)

Modified:
- `feature/feed/.../FeedRepository.kt` — #26 only
- `feature/feed/.../FeedViewModel.kt` — #9 + #27 only
- `feature/feed/.../FeedScreen.kt` — #9 only
- `feature/feed/.../PlayerScreen.kt` — #2, #8, #9, #21, #4
- `feature/feed/.../QuickSheet.kt` — #27 (follow row awaited)
- `feature/feed/src/main/res/layout/player_page_view.xml` — NEW, #8
- `feature/settings/.../SettingsScreen.kt` — #21 chip removal + #4 quality row
- `feature/settings/.../SettingsViewModel.kt` — #4
- `feature/settings/build.gradle.kts` — +`core:model` (VideoQuality visible to Settings)
- `core/datastore/.../SettingsRepository.kt` — #21 comment, #4 `video_quality` pref
- `core/datastore/build.gradle.kts` — +`core:model` (pref reads the same value-set constant)
- `core/model/.../VideoQuality.kt` — NEW, #4 (the ONE place for the menu value list)
- `core/player/.../GiffyPlayer.kt` — #4
- `core/player/.../GiffyPlayerFactory.kt` — #20
- `core/network/.../GifsApi.kt` — #27 (@HTTP hasBody normalization)
- `app-tv/.../TvPlayerScreen.kt` — #21 stretch arm, #8 TV texture+poster, #16 stop-on-switch, #24 focus-handoff, #4 threading
- `app-tv/.../TvQuickActions.kt` — #3 (swallow gate + awaited follow row)
- `app-tv/.../TvHomeScreen.kt` — #3 (GifCard hold/menu distinction, wiring)
- `app-tv/.../TvSourceFeedScreen.kt` — #3 (same wiring; forced by the shared GifCard signature)
- `app-tv/.../TvAppModule.kt` — #20 (`sdrPlayback = true`, TV only)
- `app-tv/src/main/res/layout/player_view.xml` — NEW, #8 (TV)
- `AGENTS-PLAYER.md` — #21 video-fit rewrite, #9 VM-ownership paragraph, #4 quality pref, #20 SDR note
- `AGENTS-APP.md` — #3 panel KeyUp swallow note, #24 error-overlay D-pad rule

New tests:
- `core/player/src/test/.../SdrCodecFilterTest.kt` — #20 predicate (4 cases)
- `core/model/src/test/.../VideoQualityTest.kt` — #4 pure pick + clamp guard (3 cases)
- `feature/feed/src/test/.../PagerRestartDedupTest.kt` — #26 combine-exit dedupe semantics

## Per-issue notes

**#2 tap toggles UI, never playback (mobile).** The gesture dispatch's else-branch is now a pure
visibility toggle (`onHideControls`/`onShowControls`); the `player.isPlaying` play/pause read is gone
from the branch (play/pause stays on the visible control row / PlayerControls). Comment updated.
Exactly the doc's named lines; nothing else in the gesture decoder touched.

**#3 hold-Center panel stays open after KeyUp.** `TvQuickActionsDialog` gained `openViaHold: Boolean
= false`; one preview-phase key handler on the dialog's `M3Surface` swallows exactly ONE Center KeyUp
(`swallowNextCenterUp` remember-init from the flag). Threading: TvPlayerScreen's CenterHold lambda sets
`actionsViaHold = true` (MENU key sets false), GifCard's `onMenu` became `(viaHold: Boolean) -> Unit`
(hold → true, MENU keyup → false), TvHomeScreen `openActions(gif, source, viaHold)` + the 6 rows + the
Continue card, and TvSourceFeedScreen's grid cards wire the same flag. Back/CenterHold semantics
untouched (CenterHold.kt unchanged, as the doc expected). DOC-PLANNED vs SHIPPED deviation: the doc
scoped "TvHomeScreen.kt (GifCard variant)", but GifCard is also hosted by TvSourceFeedScreen and its
signature change forced that call site; rather than a dead `_ ->` mapping I threaded the flag there
too (same bug class on the same component — reported here for review).

**#8 poster under the surface.** Two new 15-line XMLs mirroring the two proven surfaces:
`player_page_view.xml` (:feature:feed, fit default — pref re-asserted per update) and
`player_view.xml` (:app-tv, fit default, NOT zoom — the videoFit pref must win). Both players inflate
instead of `PlayerView(ctx)`, keep `useController=false` + existing attach gating, seed-aspect calls and
progress/spacing clocks. Poster = `AsyncImage(gif.posterUrl, ContentScale.Fit)` UNDER the AndroidView;
mobile wraps poster+surface in one `graphicsLayer`-carrying Box so pinch zoom moves them together.
Poster-null gifs: nothing renders, black background stays truthful (commented). No surface-type
programmatic knobs added (per doc the XML is what surface_type demands).

**#9 adopt the inline player (mobile).** `FeedViewModel` owns the shared player:
`recallInlinePlayer()` / `returnInlinePlayer()` / `dropInlinePlayer()` + `onCleared` release.
`returnInlinePlayer` re-arms (volume 0, REPEAT_ONE, speed 1) **and pauses** — DEVIATION (addition) from
the doc's knob list, necessary so a parked player doesn't decode forever between screens (the doc's
dispose-on-leave release used to guarantee no idle decode). `dropInlinePlayer()` is also an addition
(needed for the autoplay-off/2–3-col release discipline the old DisposableEffect provided; otherwise a
parked player becomes immortal when autoplay flips off). FeedScreen now adopt-or-create through the VM
(`recall ?: create().also(return)`) and its dispose parks instead of releasing; the null branch still
drops. PlayerScreen adopts via one `remember` holding `(player, adopted)`; exit returns-or-releases; the
per-page effect skips `playGif` when `currentGifId == gif.id` (adopt continuity — position/buffer kept,
`onPlayingChanged(true)` still runs); mute effect re-keyed `(muted, player)`. MainActivity needed no
change (same Activity-scoped hiltViewModel instance already flows into both/compositions). 2/3-col and
autoplay-off opens keep today's fresh-player cost per the scope guard.

**#16 aspect warp on switch (TV).** Playback effect stops the player first when
`currentGifId != gif.id` (letter-for-letter the FeedScreen inline rule); #8's poster plane covers the
cleared beat. Seed helper and per-switch re-seed untouched (doc: no seed change; the testable dead-guess
branch stays parked pending the metadata probe).

**#20 SDR-pinned TV playback.** Mechanism verified by unpacking the PINNED media3 1.11.1 AARs
(downloaded fresh from Google Maven into /tmp — the local gradle cache is empty): 1.11.1's
`TrackSelectionParameters.Builder` / `DefaultTrackSelector$Parameters` expose NO HDR/HD-Range knob (no
allowed/blocked MIME sets either — only `setPreferredVideoMimeType(s)`), so the doc's parameters-level
candidate (a) does not exist and candidate (b) is the route. `MediaCodecSelector` (constants DEFAULT /
PREFER_SOFTWARE, `getDecoderInfos(mime, requiresCodecSupport, requiresTunnelingDecoder)`),
`DefaultRenderersFactory.setMediaCodecSelector`, and `DefaultPreloadManager.Builder.setRenderersFactory`
all verified against the real 1.11.1 bytecode; HDR profile constants verified against the API-35
android.jar (HEVCProfileMain10HDR10(+Plus), AV1ProfileMain10HDR10(+Plus), VP9Profile2/3HDR(+Plus), the
11 DolbyVisionProfile* entries; HLG has no profile constant — noted in code). Predicate `isHdrOnlyDecoder`
drops a decoder ONLY when it cannot serve SDR (DV MIME, or every declared profile is HDR-only);
SDR-capable decoders (incl. Main10/Main10-HDR-aware boxes) always survive, so SDR-only libraries are
byte-for-byte unchanged (acceptance 2). Honest residual stated in code: a decoder claiming BOTH SDR and
HDR profiles can still be handed an HDR10/HLG HEVC variant — closing that needs the live ladder probe
(the doc's own "park if probe shows no HDR variants" gate stands; the flag is the honest plumbing).
Flag at BOTH construction sites (`TvAppModule` true, mobile default false, shell-agnostic core).

**#21 remove Stretch.** Settings chip list is now Fit/Crop (comment updated); PlayerScreen/TV mapping
arms deleted (legacy stored `"stretch"` de-maps through `else -> RESIZE_MODE_FIT` on both shells, no
migration); SettingsRepository comment now `fit | crop`; AGENTS-PLAYER.md Video-fit bullets rewritten
(TV note's Zoom choice retained). `RESIZE_MODE_FILL` appears only in explanatory comments about the
removal — zero executable FILL paths remain (grepped).

**#24 TV error overlay focusable.** On `playError` the Retry button claims focus
(`overlayRetryFocus` FocusRequester keyed on gif.id + `LaunchedEffect(playError)`; on overlay close the
root focus is restored), the root `onPreviewKeyEvent` stands down while the overlay is up
(`if (playError) return false` right after the harmless textVisible KeyUp line) so Up/Down walk
Retry↔Skip and CENTER/ENTER activates; BACK stays the Root BackHandler (doc's decision point 3 kept).
Left/Right seeking still works when the overlay is dismissed; mobile overlay untouched.

**#26 mute flip no longer restarts/refreshes feeds.** One `distinctUntilChanged()` between each combine
and `flatMapLatest` at the four pager-restart chains (Liked, ForYou, cached else-branch, Continue) plus
the import; no other FeedRepository logic touched (surprisePool, favorites eviction, dedup comments all
verified intact). Real filter moves still restart (structural equality on Pair/Triple incl. the
FeedPrefs data class). Test pins the combine-exit semantics: identity re-emission does not restart, a
real change does.

**#27 unfollow does nothing.** (a) `unfollowCreator` (+ `unsubscribeNiche`, same lesson/pattern noted by
the design) normalized to `@HTTP(method="DELETE", hasBody=true)` like unlike — decisive if Retrofit
mangles plain @DELETE bodies, harmless otherwise; live probe still owed. (b) Race: single-flight
`followToggleInFlight` (StateFlow compareAndSet) — `refreshFollowedCreators` must not overwrite an
in-flight optimistic flip; overlapping toggles are skipped, not coalesced. (c) The write is now awaited:
`suspend toggleFollowCreator(...): Boolean` — QuickSheet and TvQuickActions dismiss ONLY on success, so
failure keeps the row/panel open in its pre-toggle state (the doc's "no silent fire-and-close" bullet;
the doc names QuickSheet.kt as context and the same VM code serves TvQuickActions — both call sites
updated). NO unit test landed:
FeedViewModel is not unit-constructible in the existing test setup (repo/db/DataStore fakes never built
beyond FeedRepository-level tests; building VM scaffolding would be speculative infra). Covered instead
by the flags-and-annotation changes being minimal + device/live-token gates the doc itself defers (the
@HTTP-vs-@DELETE body behavior and follow round-trip need the fresh real token bundle anyway). Flagged
as a deviation from the doc's unit-test preference.

**#4 video quality pref (Wave 2).** `VideoQuality` (core/model) is THE one place for the value list:
`VALUES = auto | highest | 720 | 480`, chip labels, `maxHeightFor` (cap mapping), and
`cappedTrackHeight` (the clamp guard: a cap below every known rendition lifts to the smallest known
height — data-rule: never starve selection). DataStore `video_quality` (default auto, no backup entry —
same policy as videoFit); Settings row "Video quality" (shared screen, under video-fit, honest copy).
`GiffyPlayer.playGif(..., videoQuality = AUTO)` threads at every non-inline call site (mobile per-page +
Retry, TV per-page + Retry; inline preview stays Auto by the default); `applyVideoQuality(quality)`
applies mid-stream without re-preparing (LaunchedEffect in both screens), and one `onTracksChanged`
listener tracks the smallest known video height to refine/lift the cap live (Auto/Highest clears the
cap via `clearVideoSizeConstraints()` so a previous cap cannot survive a pref change). Data-saver rule
untouched and ordered first (`streamUrl(dataSaver)` semantics unchanged). No probe run; single-rendition
streams make the discrete caps honest no-ops by construction.

## Interface/module-graph deviations (review-worthy, deliberate)
1. `core:datastore` → `implementation(project(":core:model"))` and `feature:settings` →
   `implementation(project(":core:model"))`: the pref value set must be literally shared between
   datastore (default), SettingsScreen (menu), and the players (cap mapping). One constant, one list —
   cheaper than three spellings or an `api` widening of feature:feed.
2. GifCard.onMenu signature `(viaHold: Boolean) -> Unit` (see #3) — one concept, one callback.

## Not done / open
- All device/live gates in the doc's verification list remain: TV36 hold-open (player + card + MENU
  compare), TV36 error-overlay focus/click cycle, Medium_Phone mute-toggle-no-restart + adopted-player
  TTFF, live HDR-variant probe (#20 park-decision), fresh-token follow round-trip (#27 live), post-change
  release smoke (#21 is a mapping change — release smoke mandatory, R8 untouched).
- #20 residual window (SDR+HDR-cap-profile decoders can still decode an HDR HEVC variant) documented in
  code; needs the live probe to decide whether anything more is buildable.
- #27's "does @DELETE really drop the body on modern Retrofit" is live-verified only.
- No TODO/stub/placeholder code was introduced; GlobalScope none; StateFlow-only exposure kept;
  rate-limit invariant untouched (no new request paths); ≤140 cols + trailing-whitespace clean across
  all touched files (checked).

## Protocol note (honest disclosure)
- No gradle/ktlint/detekt/test/jj/emulator commands were run. One read-only `curl` downloaded the
  pinned media3 1.11.1 AARs from Google Maven into /tmp + `unzip`/`javap` on them (verification the task
  note explicitly prescribed via the gradle cache path, which was empty) — no workspace writes, no
  probes of the live site.
- One read-only `git diff/status` was run early for the coordinator's snapshot view before the
  don't-run-git rule was internalized; no snapshot/commit state was mutated. Flagging for transparency.
