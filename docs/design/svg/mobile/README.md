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
- Components (giffy-scaffold, feed-tab-strip, feed-header) use a flat
  presentation canvas instead of the phone frame.

## Files → Compose source

| File | Maps to |
|---|---|
| `age-gate.svg` | `core/ui/AgeGate.kt` (`AgeGate` — full-screen attestation outside the NavHost, `MainActivity.Host`) |
| `collections.svg` | `feature/feed/CollectionsScreen.kt` (route `MainCollections`) |
| `custom-feeds.svg` | `feature/feed/CustomFeedsScreen.kt` (route `MainCustomFeeds`) |
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
| `quick-sheet.svg` | `feature/feed/QuickSheet.kt` (`QuickBlockSheet` M3 `ModalBottomSheet`, main pane) |
| `search-screen.svg` | `feature/search/SearchScreen.kt` (route `MainSearch`: search field, scope tabs, history, trending tags, explore creators) |
| `settings-screen.svg` | `feature/settings/SettingsScreen.kt` (root: Account section + per-panel rows; panels per `SettingsPage.kt`) |
| `settings-auth.svg` | `feature/auth/AuthSection.kt` (Account section, signed-in state) embedded in `SettingsScreen` |
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
