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
| `tv-source-feed.svg` | `TvSourceFeedScreen` (title + Refresh / Sort & filter icon buttons, niche header + Join, orientation chips, 4-card bands from `FEED_ROW_CARDS`) | `app-tv/src/main/kotlin/com/rjbiermann/giffyviewer/tv/TvSourceFeedScreen.kt` |
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
- Dialog containers (`#221D1E`) and card containers (`#221F20`) approximate
  M3 surface-container tones over the `#161617` background (6 dp / theme
  surface elevation).

Open-issue annotation appears only where owed: `tv-quick-actions-panel.svg`
(row height). Quick-actions panel row variants (player Like/Mute/Speed/
Auto-swipe, Add-to pickers) are documented in `AGENTS-APP.md` /
`TvQuickActions.kt`, not mocked here — they are row permutations of the same
dialog.
