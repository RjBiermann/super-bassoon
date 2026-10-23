# Giffy Viewer — mobile UI mockups (SVG, Inkscape-editable)

Hand-authored plain SVG, one file per mobile screen/dialog/sheet plus the shared
components. Inline presentation attributes only (no `foreignObject`, no `<style>`,
no `class` attributes), so they open and edit cleanly in Inkscape.

- Canvas: 452×932 (screen mockups) — the phone frame sits at (20,20) and is
  **412×892 dp** inside it, matching the 412 dp Compose reference width.
- Theme: dark-only, plausible M3 dark tokens:
  surface `#1C1B1F`, surfaceContainerHigh `#36343B`, onSurface `#E6E1E5`,
  onSurfaceVariant `#CAC4D0`, primary `#D0BCFF`, onPrimary `#381E72`,
  secondary `#CCC2DC`, secondaryContainer `#4A4458`, outline `#938F99`.
  Every SVG carries a small swatch legend below the frame.
- Components (giffy-scaffold, feed-tab-strip, quick-block-sheet-add-to,
  focus-states-mobile) use a flat presentation canvas instead of the phone
  frame.

## Files → Compose source

| File | Maps to |
|---|---|
| `age-gate.svg` | `core/ui/AgeGate.kt` (`AgeGate` — full-screen attestation outside the NavHost, `MainActivity.Host`) |
| `collections.svg` | `feature/feed/CollectionsScreen.kt` (route `MainCollections`) |
| `custom-feeds.svg` | `feature/feed/CustomFeedsScreen.kt` (route `MainCustomFeeds`) |
| `feed-autoplay-preview.svg` | `feature/feed/FeedScreen.kt` inline autoplay (L519–595: `autoplayOn = gridColumns == 1 && feedAutoplay && !dataSaver`, shared VM player, 150 ms settle) + the data-saver static-poster variant (L132–139), side by side |
| `feed-filter-dialog.svg` | `feature/feed/FeedFilterDialog.kt` (M3 `AlertDialog`: Sort / Duration / Resolution / Shuffle / Orientation + Clear filters) |
| `feed-header.svg` | `feature/feed/FeedHeader.kt` (`CreatorFeedHeader`, `NicheFeedHeader`) + the For You scope-selector chips from `FeedScreen.kt` |
| `feed-screen.svg` | `feature/feed/FeedScreen.kt` (home, route `MainFeedDefault`/`MainFeed`: GiffyScaffold + tab strip + seam row + scope chips + sort icon + masonry grid + `ScrollToTopFab`) |
| `feed-tab-strip.svg` | `feature/feed/FeedTabStrip.kt` (shared component: primary chips, Feeds-group sub-row, screen-seam row) |
| `following.svg` | `feature/feed/FollowingScreen.kt` (route `MainFollowing`) |
| `giffy-scaffold.svg` | `core/ui/GiffyScaffold.kt` (shared component: offline banner + TopAppBar + collapsing header extra + content slot) |
| `niches.svg` | `feature/feed/NichesScreen.kt` (route `MainNiches`: category chips + Sort menu + preview rows) |
| `niche-about.svg` | `feature/feed/NicheAboutScreen.kt` (route `MainNicheAbout`: description, counts, Join, rules, top creators, related) |
| `pin-lock.svg` | `core/ui/PinLock.kt` (`PinLockScreen` — gates the whole app before the NavHost) |
| `player-overlay.svg` | `feature/feed/PlayerScreen.kt` (route `MainPlayer` overlay: description/tags pills, action rail, progress, `core/ui/PlayerOverlay.kt` scrim tokens) |
| `quick-block-sheet-add-to.svg` | `feature/feed/QuickSheet.kt` (`QuickBlockSheet` “Add to…” pane: `AddToCustomFeedDialog` L292, `AddToCollectionDialog` L360, `AddToNicheDialog` L395 — side by side, flat canvas) |
| `quick-sheet.svg` | `feature/feed/QuickSheet.kt` (`QuickBlockSheet` M3 `ModalBottomSheet`, main pane) |
| `search-screen.svg` | `feature/search/SearchScreen.kt` (route `MainSearch`: search field, scope tabs, history, trending tags, explore creators) |
| `settings-screen.svg` | `feature/settings/SettingsScreen.kt` (root: Account section + per-panel rows; panels per `SettingsPage.kt`) |
| `settings-auth.svg` | `feature/auth/AuthSection.kt` (Account section, signed-in state) embedded in `SettingsScreen` |
| `settings-pin-dialog.svg` | `feature/settings/SettingsScreen.kt` `SetPinDialog` (L990: digits-only 4-char field over the General panel, Save enables at 4) |
| `settings-panel-general.svg` | `feature/settings/SettingsScreen.kt` `GeneralPanel` (L377: App lock/PIN, Grid columns — mobile-only row, hidden on TV —, Autoplay in feed, AMOLED) |
| `settings-panel-video.svg` | `feature/settings/SettingsScreen.kt` `VideoPanel` (L566: Video fit Fit/Crop, quality Auto/Highest/SD per `core/model VideoQuality`, Data saver) |
| `settings-panel-feeds.svg` | `feature/settings/SettingsScreen.kt` `FeedsPanel` (L684: Orientation Any/Vertical/Horizontal + Verified creators only) |
| `settings-panel-blocked.svg` | `feature/settings/SettingsScreen.kt` `BlockedPanel` (L771: favorited creators/tags, blocked creators/tags/keywords, Hidden this week, not-blockable note) |
| `settings-panel-backup.svg` | `feature/settings/SettingsScreen.kt` `BackupPanel` (L885: Export/Import buttons + SAF `CreateDocument`/`OpenDocument` affordance) |
| `settings-panel-about.svg` | `feature/settings/SettingsScreen.kt` `AboutPanel` (L919: name, disclaimer, version) |
| `focus-states-mobile.svg` | `core/ui/Focus.kt` (`GiffyPillButton` L68 rest vs focused; `giffyFocus` ring on rows/chips vs `GifTile`’s ringless touch-first treatment; flat canvas) |
| `webview-login.svg` | `feature/auth/WebViewLoginScreen.kt` (PKCE WebView login) |

## Notes matching reality

- **No bottom-nav component exists on mobile** — the codebase has no
  `NavigationBar`; navigation is top-app-bar actions plus the feed tab strip's
  screen-seam row, so no `bottom-nav.svg` is produced.
- The age gate and PIN lock render **outside** the NavHost (they gate the app,
  not pages) — see `MainActivity.Host`.
- The feed screen order is: app bar (search + settings actions) → collapseable
  tab strip → seam row → For You scope chips (logged-in) → sort/filter icon →
  2-col staggered masonry with creator labels and audio badges; the thin bar at
  the top of the grid is the M3 `LinearProgressIndicator` refresh state.
- The player overlay tokens follow `core/ui/PlayerOverlay.kt`: 60% black scrim,
  #F7E9EC overlay text on an 80% black pill backdrop, white track hairlines.
- `focus-states-mobile.svg` extends the legend with two plausible M3 tokens not
  in the standard swatch row: `primaryContainer` #4F378B and
  `onPrimaryContainer` #EADDFF (the focused fill/text pair `GiffyPillButton` and
  `giffyFocus` read from the scheme; see `core/ui/Focus.kt` and the TV focus
  tokens in `docs/design/svg/tv/focus-states.svg`).
- Settings panels mock the in-screen sub-pages (`SettingsPage` enum): the
  top bar keeps the close (×) icon `SettingsScreen` passes as `backIcon` on
  every page, and each panel is reachable from `settings-screen.svg`’s root
  rows. Grid columns is a mobile-only row (`showGridColumns = false` on TV).
- Blocked panel rows: section headers are `primary`-colored `titleSmall` with
  heading semantics; each value row is body text + an × `IconButton`.
- Backup panel export/import go through the SAF system document picker
  (`CreateDocument("application/json")` / `OpenDocument`) — no storage
  permission, no app-owned file browsing UI.
