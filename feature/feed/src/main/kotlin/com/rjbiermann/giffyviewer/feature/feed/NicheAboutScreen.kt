package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel
import com.rjbiermann.giffyviewer.core.ui.GiffyScaffold
import kotlinx.coroutines.launch

/**
 * Niche About page (PLAN §7 — site niche anatomy verified 2026-10-01):
 * description + "N Members / N Posts" + Join/Leave Niche + share + Top
 * Creators + "Niches you might also like". Anonymous-readable; the join
 * button appears only logged-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicheAboutScreen(
    nicheId: String,
    nicheName: String,
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    onOpenNiche: (FeedSource.Niche) -> Unit,
    api: GifsApi,
    joinViewModel: NicheJoinViewModel,
) {
    var detail by remember { mutableStateOf<com.rjbiermann.giffyviewer.core.network.NicheDetail?>(null) }
    var topCreators by remember { mutableStateOf<List<com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto>>(emptyList()) }
    var related by remember { mutableStateOf<List<com.rjbiermann.giffyviewer.core.network.FollowedNicheDto>>(emptyList()) }
    // Audit: failures must surface — detail failure = full-screen error + retry;
    // the two section fetches degrade to empty sections.
    var detailFailed by remember { mutableStateOf(false) }
    val joined by joinViewModel.joined.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun share() {
        val intent =
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, com.rjbiermann.giffyviewer.core.model.Hosts.niche + nicheId)
            }
        context.startActivity(android.content.Intent.createChooser(intent, "Share $nicheName"))
    }

    fun loadAbout() {
        scope.launch {
            detailFailed = false
            runCatching { api.nicheDetail(nicheId) }
                .onSuccess { detail = it.niche }
                .onFailure { if (detail == null) detailFailed = true }
            runCatching { api.nicheTopCreators(nicheId) }.onSuccess { topCreators = it.creators }
            runCatching { api.nicheRelated(nicheId) }.onSuccess { related = it.niches }
        }
    }

    LaunchedEffect(nicheId) {
        joinViewModel.refresh()
        loadAbout()
    }

    GiffyScaffold(
        title = nicheName,
        onBack = onBack,
        actions = {
            IconButton(onClick = { share() }) {
                Icon(Icons.Filled.Share, contentDescription = "share")
            }
        },
    ) { padding ->
        if (detailFailed) {
            com.rjbiermann.giffyviewer.core.ui.EmptyState(
                message = "Couldn't load this niche",
                hint = "Check your connection and retry",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            androidx.compose.material3.TextButton(
                onClick = { loadAbout() },
                modifier = Modifier.padding(16.dp),
            ) { Text("Retry") }
            return@GiffyScaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            detail?.let { d ->
                Column(modifier = Modifier.padding(16.dp)) {
                    d.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "${d.subscribers} Members / ${d.gifs} Posts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (joinViewModel.loggedIn) {
                        Button(
                            onClick = { joinViewModel.toggle(nicheId) },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text(if (nicheId in joined) "Leave Niche" else "Join Niche")
                        }
                    }
                    d.rules?.takeIf { it.isNotEmpty() }?.let { rules ->
                        Text("Niche Rules", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                        rules.forEachIndexed { i, rule ->
                            Text(
                                "${i + 1}. $rule",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    d.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                        Text(
                            tags.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            if (topCreators.isNotEmpty()) {
                Text("Top Creators", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                topCreators.forEach { creator ->
                    ListItem(
                        leadingContent = {
                            AsyncImage(
                                model = creator.profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                            )
                        },
                        headlineContent = {
                            CreatorLabel(creator.username, creator.verified)
                        },
                        supportingContent = { Text("${creator.followers} followers") },
                        modifier = Modifier.clickable { onOpenCreator(creator.username) },
                    )
                }
            }
            if (related.isNotEmpty()) {
                Text("Niches you might also like", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                related.forEach { niche ->
                    ListItem(
                        headlineContent = { Text(niche.name ?: niche.id) },
                        supportingContent = { Text("${niche.gifs} gifs · ${niche.subscribers} members") },
                        modifier =
                            Modifier.clickable {
                                onOpenNiche(FeedSource.Niche(niche.id, niche.name ?: niche.id))
                            },
                    )
                }
            }
            Box(Modifier.size(24.dp))
        }
    }
}
