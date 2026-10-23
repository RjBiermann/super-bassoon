# Giffy Viewer — TV UI mockups (SVG)

Editable Inkscape mockups of every TV UI surface (`:app-tv`). Plain SVG 1.1,
inline presentation attributes only, no `foreignObject`, no external images —
posters/avatars are placeholder rects in the gif *avgColor* tones the app
renders. 1920×1080 per frame (~1.5 px per dp of the real layout).

## Palette (issue-#7 tokens — `core/ui/Theme.kt`, misuse-free single source)

| Role | Hex |
|---|---|
| background / surface | `#161617` |
| onSurface | `#F7E9EC` |
| onSurfaceVariant / secondary text | `#DCA1AB` |
| primary (accent + focus border) | `#FF668F` |
| onPrimary (button rest text) | `#5E0B25` |
| primaryContainer (focused fill) | `#7D0030` |
| onPrimaryContainer (focused text) | `#FFD9E1` |
| secondary | `#E3BDC4` |
| secondaryContainer (selected chip) | `#5E4349` / text `#FBD9E1` |
| outlineVariant (rest rings, divider) | `#51444A` |
| outline (annotations) | `#9C7A80` |

Font: **DM Sans**. Dark M3 only (theme is dark-only).

## Focus visualization (the 10-foot contract)

TV focus is **ring-only, zero zoom** (issue #37, `CardScale.None` everywhere).
Every focused element carries the issue-#7 tokens: **3 dp primary `#FF668F`
border + soft outer glow** (`borderVariant = primary`); interactive fills
(buttons/chips/surfaces) additionally flip to `primaryContainer #7D0030` /
`onPrimaryContainer #FFD9E1`. `focus-states.svg` is the canonical
focused-vs-unfocused comparison; the other files each show one focused state.

## Files → sources

| Mockup | Composes… | Source |
|---|---|---|
| `tv-home.svg` | `TvHomeScreen` (header, For You + scope chips, Trending with a focused card, Explore creator cards; rows below the fold) | `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvHomeScreen.kt` |
| `tv-source-feed.svg` | `TvSourceFeedScreen` (title + Refresh / Sort & filter icon buttons, niche header + Join, orientation chips, 4-card bands from `FEED_ROW_CARDS`, end-of-feed footer: "End of feed" + **focused Refresh button** — `RefreshFooterButton` #22/F4, shared with the error/filtered-empty states) | `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvSourceFeedScreen.kt` |
| `tv-more-menu.svg` | Home header pills row + **More ▾ `DropdownMenu` open** (Collections; My feeds + per-custom-feed shortcuts behind `Feature.CUSTOM_FEEDS`; divider; pinned niches + creators). Pills sit ABOVE the home title (`tv-home.svg` predates the pill row — newer mockup truth). No dim scrim (DropdownMenu), first menu row focused. | `TvMainActivity.kt` (`TvHomeDestination`) |
| `tv-quick-actions-tags.svg` | Quick-actions dialog **tags sub-view** (2 QuickAction rows per tag from `gif.tags.take(3)` — Favorite/Unfavorite via server `tagState` + Block — then ‹ Back) | `TvQuickActions.kt` |
| `tv-quick-actions-speed.svg` | Quick-actions dialog **player variant**: divider + Like / Mute / **`SpeedAdjustRow` focused** / Auto-swipe; annotated: ‹ › adjust ±0.25 (OS key repeat), clamp 0.5–2.0×, Center no-op (display-only row) | `TvQuickActions.kt` (`SpeedAdjustRow`) |
| `tv-add-to-feed-dialog.svg` | **TvAddToFeedDialog** picker: radio custom feeds (Room) + checkbox refs (`gifFeedRefs` → `customRefSummary` labels; creator ref preselected) + Add/Cancel | `TvQuickActions.kt` |
| `tv-add-to-collection-dialog.svg` | **TvAddToCollectionDialog**: saved collections as `QuickAction` rows (`folderName (contentCount)`), first focused, Cancel; empty state named in-frame notes | `TvQuickActions.kt` |
| `tv-add-to-niche-dialog.svg` | **TvAddToNicheDialog**: joined niches, `orderNichesByTagMatch` ordering (tag-match first, `take(8)`), first focused, Cancel | `TvQuickActions.kt` |
| `tv-settings.svg` | Shared `SettingsScreen` **TV variant** (`TvSettings` destination: `showGridColumns=false`, `showFeedAutoplay=false`, `showTvRowHeight=true`): General panel with the TV-home-row-size chip block; dashed ghosts mark the two hidden mobile-only rows | `feature/settings/SettingsScreen.kt` + `TvMainActivity.kt` |
| `tv-player.svg` | `TvPlayerScreen` — controls REVEALED state (poster under the player surface, always-on thin progress line, creator + description pill + niche pills cluster). Keymap in the header comment. | `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvPlayerScreen.kt` |
| `tv-niches.svg` | `TvNichesScreen` (title, category chips + Sort, niche rows: focused Surface with 16:9 preview tile, name + PushPin, stats, About) | `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvNiches.kt` |
| `tv-quick-actions-panel.svg` | `TvQuickActionsDialog` main pane, home-card context (MENU / held Center), first row focused; carries the annotated **open issue: rows measure ≈37.5 dp visual height vs the 48 dp M3 guideline** (dashed 48 dp ghost in-frame) | `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvQuickActions.kt` |
| `focus-states.svg` | Focused / unfocused card comparison + token legend + chip & button pair | `TvHomeScreen.kt` (GifCard) + `core/ui/Focus.kt` (`giffyFocus`) |

Shared components referenced: `core/ui` `CreatorLabel` (✓-tick), `AudioBadge`,
`giffyFocus`, `PlayerOverlay` tokens (player scrims/tracks), theme
`TvTheme.kt` (DM Sans + borrowed-palette tv-material scheme).

Honest-scaling notes:

- `tv-home` / `tv-source-feed` / `tv-niches` show mid-scroll states; cropped
  content at the frame edge is the fold, not a design gap.
- `tv-niches` draws preview tiles at reduced scale (annotated in-frame) so two
  rows fit the frame; real tiles render full-width 16:9.
- Dialog containers (`#221D1E`), card containers (`#221F20`) and the More
  ▾ `DropdownMenu` surface approximate M3 surface-container tones over the
  `#161617` background (6 dp / theme surface elevation). Header pills rest
  fill ≈ `#282728` (onSurface @8% over the background).

Open-issue annotation appears only where owed: `tv-quick-actions-panel.svg`
(row height). Quick-actions row variants are now mocked: the **main pane**
never carries `SpeedAdjustRow` (it is player-only — see the divider in the
source), so the panel mock stays home-context as-is; the tags sub-view and
the Speed row live in their own files above, and the three Add-to pickers
(`TvAddToFeedDialog` / `TvAddToCollectionDialog` / `TvAddToNicheDialog`) are
mocked as standalone dialogs. All four share the dialog geometry of
`tv-quick-actions-panel.svg` (630 px ≈ 420 dp wide Surface, 56 px rows ≈
37.5 dp — the same open-issue row height).
