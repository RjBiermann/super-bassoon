package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rjbiermann.giffyviewer.core.database.CustomFeedEntity
import com.rjbiermann.giffyviewer.core.ui.Feature

/**
 * The mobile FeedScreen feed-tabs row, extracted verbatim (#19) so TV
 * full-feed pages host the SAME tabs by construction — labels, fixed set, the
 * More ▾ menu and the selected-state logic stay single-sourced here.
 * State arrives as plain parameters (zero ViewModel coupling — the caller
 * collects from its own FeedViewModel); [onOpen] is the feed-switch seam:
 * mobile wires `viewModel.open` (in-place swap), TV wires the full-page
 * openFeed path. The Explore/Following/Niches/Collections/CustomFeeds rows
 * are SCREEN seams — null hides the row (TV hosts Niches/Collections/
 * CustomFeeds on Root browser states; Explore/Following have no TV host
 * surface today). No Surprise seam — that feature was removed from the app.
 */
@Composable
fun FeedTabStrip(
    source: FeedSource,
    isLoggedIn: Boolean,
    /** Raw pinned-niche "id|name" entries (PLAN §7 pin-to-tabs) — parsed here. */
    pinnedNiches: Set<String>,
    favoritedFeeds: List<CustomFeedEntity>,
    pinnedCreators: Set<String>,
    customFeeds: List<CustomFeedEntity>,
    /** Feed-switch seam: mobile = in-place FeedViewModel.open; TV = full page. */
    onOpen: (FeedSource) -> Unit,
    /** Screen seams (null = hide the row — no host surface). */
    onOpenFollowing: (() -> Unit)? = null,
    onOpenNiches: (() -> Unit)? = null,
    onOpenCollections: (() -> Unit)? = null,
    onOpenCustomFeeds: (() -> Unit)? = null,
) {
    val nicheTabs =
        remember(pinnedNiches) {
            pinnedNiches.mapNotNull { entry ->
                parseNicheRef(entry)?.let { (id, name) -> FeedSource.Niche(id, name) }
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // FIXED set only — three primary tabs + a More menu (consolidated;
        // pinned/custom grow unbounded and the row became unusable).
        // For You = server personalized feed, logged-in only (PLAN §7).
        // Site home order: For You first when logged in (verified sweep).
        listOf(
            FeedSource.ForYou,
            FeedSource.Trending,
            FeedSource.Favorites,
        ).filterNot { it is FeedSource.ForYou && !isLoggedIn }
            .forEach { candidate ->
                FilterChip(
                    selected = source == candidate,
                    onClick = { onOpen(candidate) },
                    label = { Text(candidate.title()) },
                )
            }

        // More ▾ = every secondary surface, grouped in sections (M3
        // DropdownMenu): screens · custom feeds · pinned tabs. The open
        // surface keeps a selected marker when it lives here.
        var moreOpen by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected =
                    source is FeedSource.TopThisWeek ||
                        source is FeedSource.Liked ||
                        source is FeedSource.Custom ||
                        (source is FeedSource.Niche) ||
                        source is FeedSource.Creator,
                onClick = { moreOpen = true },
                label = { Text("More ▾") },
            )
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Top This Week") },
                    onClick = {
                        moreOpen = false
                        onOpen(FeedSource.TopThisWeek)
                    },
                )
                if (isLoggedIn) {
                    DropdownMenuItem(
                        text = { Text("Liked GIFs & Images") },
                        onClick = {
                            moreOpen = false
                            onOpen(FeedSource.Liked)
                        },
                    )
                    if (onOpenFollowing != null) {
                        DropdownMenuItem(
                            text = { Text("Following…") },
                            onClick = {
                                moreOpen = false
                                onOpenFollowing()
                            },
                        )
                    }
                    if (onOpenCollections != null) {
                        DropdownMenuItem(
                            text = { Text("Collections…") },
                            onClick = {
                                moreOpen = false
                                onOpenCollections()
                            },
                        )
                    }
                }
                if (onOpenNiches != null) {
                    DropdownMenuItem(
                        text = { Text("Niches…") },
                        onClick = {
                            moreOpen = false
                            onOpenNiches()
                        },
                    )
                }
                // Custom feeds (#10): the builder + management surfaces are
                // disabled for now — DB rows and the BLOCKED-feed filter
                // stage keep working underneath (Feature.CUSTOM_FEEDS).
                if (Feature.CUSTOM_FEEDS) {
                    // Always reachable: the builder is the ONLY way to
                    // create a feed, so "New feed…" must not be gated on
                    // having any (fresh install would otherwise never
                    // reach the screen).
                    if (customFeeds.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            "Custom feeds",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        customFeeds.forEach { def ->
                            DropdownMenuItem(
                                text = { Text(def.name) },
                                onClick = {
                                    moreOpen = false
                                    onOpen(FeedSource.Custom(def.id, def.name, parseCustomRefs(def.sourcesJson)))
                                },
                            )
                        }
                    }
                    if (onOpenCustomFeeds != null) {
                        DropdownMenuItem(
                            text = { Text(if (customFeeds.isEmpty()) "New custom feed…" else "New feed…") },
                            onClick = {
                                moreOpen = false
                                onOpenCustomFeeds()
                            },
                        )
                    }
                }
                val pinnedSection =
                    nicheTabs.map { it as FeedSource } +
                        (
                            if (Feature.CUSTOM_FEEDS) {
                                favoritedFeeds
                                    .filter { it.state == "FAVORITED" }
                                    .map {
                                        FeedSource.Custom(
                                            it.id,
                                            it.name,
                                            parseCustomRefs(it.sourcesJson),
                                        ) as FeedSource
                                    }
                            } else {
                                emptyList()
                            }
                        ) +
                        pinnedCreators.map { FeedSource.Creator(it) as FeedSource }
                if (pinnedSection.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        "Pinned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    pinnedSection.forEach { feed ->
                        DropdownMenuItem(
                            text = { Text(feed.title()) },
                            onClick = {
                                moreOpen = false
                                onOpen(feed)
                            },
                        )
                    }
                }
            }
        }
    }
}
