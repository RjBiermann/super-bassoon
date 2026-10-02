package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

/**
 * Following screen (PLAN §7): followed creators + joined niches; entries jump
 * to the creator feed / niche feed. Heading mirrors the site's "Following".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingScreen(
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    onOpenNiche: (FeedSource.Niche) -> Unit,
    viewModel: FollowingViewModel,
) {
    val creators by viewModel.creators.collectAsStateWithLifecycle()
    val niches by viewModel.niches.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Following") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            item(key = "creators-h") {
                Text(
                    "Creators",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(creators, key = { "c" + it.username }) { creator ->
                ListItem(
                    leadingContent = {
                        AsyncImage(
                            model = creator.profileImageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                        )
                    },
                    headlineContent = {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("@${creator.username}")
                            if (creator.verified) {
                                com.rjbiermann.giffyviewer.core.ui.VerifiedTick(
                                    modifier = Modifier.padding(start = 4.dp).size(14.dp),
                                )
                            }
                        }
                    },
                    supportingContent = { Text("${creator.followers} followers · ${creator.gifs} gifs") },
                    modifier = Modifier.clickable { onOpenCreator(creator.username) },
                )
            }
            item(key = "niches-h") {
                Text(
                    "Niches",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(niches, key = { "n" + it.id }) { niche ->
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
    }
}
