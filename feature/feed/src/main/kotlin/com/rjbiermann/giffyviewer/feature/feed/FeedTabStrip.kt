package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * #25 Feeds group: the four cached server browsing pools that share one
 * Feeds chip with a sub-row underneath (issue #25). One endpoint family —
 * each member keeps its own keyBase cache, so selecting a sub is the same
 * cache-first TTL-gated swap that any chip swap is today (render issues
 * zero requests). Deliberately NOT a FeedSource type: the group is a
 * strip render concept over the existing singleton source (savedSort/
 * baseKey/back() untouched; sub state IS the source itself).
 * `top7` is excluded — duplicate wording of TopThisWeek with a different
 * endpoint; TopThisWeek stays the canonical week sub.
 */
internal val FEED_GROUP: List<FeedSource> =
    listOf(
        FeedSource.Trending,
        FeedSource.TopThisWeek,
        FeedSource.TopRanged("top28"),
        FeedSource.TopRanged("top"),
    )

/** The Feeds-chip tap target: the active sub in-group, else the last one used. */
internal fun feedGroupTapTarget(
    current: FeedSource,
    lastGroupSub: FeedSource,
): FeedSource = if (current in FEED_GROUP) current else lastGroupSub

/** Sub-row visibility: the source is a Feeds-group member. */
internal fun isInFeedGroup(source: FeedSource): Boolean = source in FEED_GROUP

/**
 * The mobile FeedScreen feed-tabs row, extracted verbatim (#19) so TV
 * full-feed pages host the SAME tabs by construction — labels, fixed set, the
 * More ▾ menu and the selected-state logic stay single-sourced here.
 * #25: primary set is For You (logged-in) · Feeds · Favorites · More ▾; the
 * Feeds chip opens the Feeds group and the sub-row renders beneath the strip
 * while a group member is the active source ([lastGroupSub] remembers the
 * last-open sub when the user is away in a non-group source, source-lifespan
 * only, NOT persisted).
 * State arrives as plain parameters (zero ViewModel coupling — the caller
 * collects from its own FeedViewModel); [onOpen] is the feed-switch seam:
 * mobile wires the onFeedNav route push (MainFeed, issue #13), TV the
 * TvFeed full-page destination push. The Explore/Following/Niches/
 * Collections/CustomFeeds rows are SCREEN seams — null hides the row (both
 * hosts route them to destinations; TV hosts Niches/Collections/
 * CustomFeeds but not Explore/Following — no host surface today). No
 * Surprise seam — that feature was removed from the app.
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
    /** #25 last-open Feeds-group sub; the group chip re-opens it from any
     *  non-group source (default Trending — same lifespan as the feed source,
     *  NOT persisted; see DESIGN issue #25 state note). */
    lastGroupSub: FeedSource = FeedSource.Trending,
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
    // #25: the single strip Row became a Column of primary row + sub-row
    // (the sub-row is a second INDEPENDENT horizontally-scrollable Row —
    // its own rememberScrollState, one row must not scroll both).
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // FIXED set only — three primary chips + a More menu (consolidated;
            // pinned/custom grow unbounded and the row became unusable).
            // For You = server personalized feed, logged-in only (PLAN §7);
            // site home order: For You first when logged in (verified sweep).
            // Trending is now a Feeds-group SUB (#25), no longer a primary chip.
            if (isLoggedIn) {
                FilterChip(
                    selected = source == FeedSource.ForYou,
                    onClick = { onOpen(FeedSource.ForYou) },
                    label = { Text(FeedSource.ForYou.title()) },
                )
            }
            FilterChip(
                selected = isInFeedGroup(source),
                onClick = { onOpen(feedGroupTapTarget(source, lastGroupSub)) },
                label = { Text("Feeds") },
            )
            FilterChip(
                selected = source == FeedSource.Favorites,
                onClick = { onOpen(FeedSource.Favorites) },
                label = { Text(FeedSource.Favorites.title()) },
            )

            // More ▾ = every secondary surface, grouped in sections (M3
            // DropdownMenu): screens · custom feeds · pinned tabs. The open
            // surface keeps a selected marker when it lives here.
            var moreOpen by remember { mutableStateOf(false) }
            Box {
                // More-selected marker drops TopThisWeek (#25): week is a Feeds
                // group sub now, and the group chip carries the selected state
                // for every group member.
                FilterChip(
                    selected =
                        source is FeedSource.Liked ||
                            source is FeedSource.Custom ||
                            (source is FeedSource.Niche) ||
                            source is FeedSource.Creator,
                    onClick = { moreOpen = true },
                    label = { Text("More ▾") },
                )
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
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
        // #25 Feeds group sub-row: the four browsing pools sit beneath the
        // strip whenever a group member is the active source (plain `if` —
        // leaving/entering composition is the established pattern). Selection
        // is exact FeedSource equality; taps go through the same [onOpen]
        // seam as any chip (mobile in-place swap / TV full-page). focusGroup
        // per the TV chip-skip lesson — beam geometry stays in the row.
        if (isInFeedGroup(source)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusGroup()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FEED_GROUP.forEach { member ->
                    FilterChip(
                        selected = source == member,
                        onClick = { onOpen(member) },
                        label = { Text(member.title()) },
                    )
                }
            }
        }
    }
}
