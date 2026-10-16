package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rjbiermann.giffyviewer.core.network.NicheDetail
import com.rjbiermann.giffyviewer.core.network.UserStatsDto
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel

/**
 * Feed identity header (issue #31): who owns this feed — a creator's avatar +
 * bio (issue #31: the mobile/TV creator feeds carried only the counts row) or
 * a niche's description + member/post counts (the niche feed previously had
 * NO header at all). One shared composable for the mobile FeedScreen and the
 * TV source-feed screen; an information-only surface: nothing focusable or
 * clickable, theme-role colors only, 10–12dp spacing.
 */

@Composable
fun CreatorFeedHeader(
    stats: UserStatsDto,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.profileImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = url,
                // Decorative: the @username text announces the creator.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CreatorLabel(
                username = stats.username,
                verified = stats.verified,
                style = MaterialTheme.typography.bodyMedium,
            )
            stats.description?.takeIf { it.isNotBlank() }?.let { bio ->
                Text(
                    text = bio,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun NicheFeedHeader(
    niche: NicheDetail,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = niche.name, style = MaterialTheme.typography.titleMedium)
        niche.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            // Same Locale.US count formatting as the creator stats row.
            text =
                "%,d members · %,d posts".format(
                    java.util.Locale.US,
                    niche.subscribers,
                    niche.gifs,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
