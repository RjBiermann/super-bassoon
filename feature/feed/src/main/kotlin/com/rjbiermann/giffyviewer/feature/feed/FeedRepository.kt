package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.network.upstreamApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache-first feed access (PLAN §5). UI reads Room via [FeedPagingSource];
 * [FeedMediator] refreshes in the background. Airplane mode: rows render from cache.
 */
@Singleton
@OptIn(ExperimentalPagingApi::class)
class FeedRepository
    @Inject
    constructor(
        private val api: upstreamApi,
        private val db: GiffyDatabase,
        private val contentFilter: ContentFilter,
    ) {
        /** [forceRefresh] bypasses the REFRESH TTL once (pull-to-refresh /
         *  "Refresh feed" pill — PLAN §9): the pager restart starts a fresh
         *  generation whose REFRESH must hit the network even when cache is fresh. */
        fun paging(
            feed: FeedSource,
            forceRefresh: Boolean = false,
        ): Flow<PagingData<Gif>> =
            if (feed is FeedSource.Liked) {
                // Network-live, never cached (PLAN §7); no RemoteMediator, no Room.
                Pager(
                    config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 10, enablePlaceholders = false),
                    pagingSourceFactory = { LikedNetworkPagingSource(api, contentFilter, PAGE_SIZE) },
                ).flow
            } else {
                cachedPager(feed, forceRefresh)
            }

        private fun cachedPager(
            feed: FeedSource,
            forceRefresh: Boolean,
        ): Flow<PagingData<Gif>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 10, enablePlaceholders = false),
                initialKey = 1,
                remoteMediator =
                    FeedMediator(
                        feed = feed,
                        gifDao = db.gifDao(),
                        pageDao = db.feedPageDao(),
                        api = api,
                        pageSize = PAGE_SIZE,
                        force = forceRefresh,
                        favorites = { db.contentPrefsDao().favoriteCreators() },
                    ),
                pagingSourceFactory = { FeedPagingSource(db, feed, db.feedPageDao(), PAGE_SIZE, contentFilter) },
            ).flow

        companion object {
            const val PAGE_SIZE = 20
        }
    }
