package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.network.CollectionGifBody
import com.rjbiermann.giffyviewer.core.network.EmptyBody
import com.rjbiermann.giffyviewer.core.network.FollowBody
import com.rjbiermann.giffyviewer.core.network.FollowedNichesDto
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.LikeBody
import com.rjbiermann.giffyviewer.core.network.NicheAddBody
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import com.rjbiermann.giffyviewer.core.network.dto.TemporaryTokenDto

/** In-memory GifsApi for unit tests; shared by the mediator + liked-feed tests. */
internal class FakeApi : GifsApi {
    var userGifsCalls = 0
    var searchCalls = 0
    var nicheGifsCalls = 0
    var nicheGifsArgs = mutableListOf<Pair<String, Int>>()
    var searchArgs = mutableListOf<Pair<String, Int>>() // (text, page)
    var likedPages: MutableMap<Int, GifsPageDto> = mutableMapOf()

    /** page → HTTP status code thrown by likedFeed (pool-cap/error probes). */
    var likedHttpErrors: MutableMap<Int, Int> = mutableMapOf()
    var likedFeedCalls: MutableList<Int> = mutableListOf()
    var forYouPages: MutableMap<Int, GifsPageDto> = mutableMapOf()
    var forYouHttpErrors: MutableMap<Int, Int> = mutableMapOf()
    var forYouCalls: MutableList<Int> = mutableListOf()
    var likedIdsResult: List<String> = emptyList()

    /** page → dto for trendingPopular (cached-path walk tests); empty = empty page. */
    var trendingPages: MutableMap<Int, GifsPageDto> = mutableMapOf()

    /** page → HTTP status code thrown by trendingPopular (pool-cap/error probes). */
    var trendingHttpErrors: MutableMap<Int, Int> = mutableMapOf()
    var trendingCalls: MutableList<Int> = mutableListOf()

    override suspend fun temporaryToken(): TemporaryTokenDto = throw NotImplementedError()

    override suspend fun searchDateProbe(
        text: String,
        createdAfter: String?,
        created_after: String?,
        date: String?,
        dateFrom: String?,
        count: Int,
    ): GifsPageDto = throw NotImplementedError()

    override suspend fun search(
        searchText: String,
        order: String,
        count: Int,
        page: Int,
        type: String?,
    ): GifsPageDto {
        searchCalls++
        searchArgs.add(searchText to page)
        return GifsPageDto(gifs = listOf(gifDtoShell("s$searchCalls", searchText)))
    }

    override suspend fun userGifs(
        username: String,
        count: Int,
        page: Int,
        order: String,
    ): GifsPageDto {
        userGifsCalls++
        return GifsPageDto(gifs = listOf(gifDtoShell("g1", username)))
    }

    override suspend fun trendingPopular(
        count: Int,
        page: Int,
        order: String?,
    ): GifsPageDto {
        trendingCalls.add(page)
        trendingHttpErrors[page]?.let { code -> throw httpError(code) }
        return trendingPages[page] ?: GifsPageDto()
    }

    override suspend fun creatorSearchPreviews(
        query: String,
        order: String,
        count: Int,
        page: Int,
    ) = throw NotImplementedError()

    override suspend fun nicheSearchPreviews(
        query: String,
        order: String,
        count: Int,
        page: Int,
    ) = throw NotImplementedError()

    override suspend fun tagsMatch(query: String): List<String> = throw NotImplementedError()

    override suspend fun niches(
        count: Int,
        page: Int,
        category: String?,
        order: String?,
    ) = throw NotImplementedError()

    override suspend fun nicheCategories() = throw NotImplementedError()

    override suspend fun nicheGifs(
        nicheId: String,
        count: Int,
        page: Int,
        order: String?,
    ): GifsPageDto {
        nicheGifsCalls++
        nicheGifsArgs.add(nicheId to page)
        return GifsPageDto(gifs = listOf(gifDtoShell("n$searchCalls", nicheId)))
    }

    override suspend fun verifiedCreators(
        count: Int,
        page: Int,
    ) = throw NotImplementedError()

    override suspend fun suggest(query: String): List<com.rjbiermann.giffyviewer.core.network.SuggestDto> = throw NotImplementedError()

    override suspend fun trendingTags(count: Int): com.rjbiermann.giffyviewer.core.network.TrendingTagsDto = throw NotImplementedError()

    override suspend fun creatorsSearch(
        query: String,
        count: Int,
    ) = throw NotImplementedError()

    override suspend fun followingCreators(
        page: Int,
        count: Int,
    ) = throw NotImplementedError()

    override suspend fun createCollection(body: com.rjbiermann.giffyviewer.core.network.CreateCollectionBody) = throw NotImplementedError()

    override suspend fun renameCollection(
        id: String,
        body: com.rjbiermann.giffyviewer.core.network.RenameCollectionBody,
    ) = throw NotImplementedError()

    override suspend fun deleteCollection(id: String) = throw NotImplementedError()

    override suspend fun meCollections(
        page: Int,
        count: Int,
    ) = throw NotImplementedError()

    override suspend fun nicheDetail(id: String) = throw NotImplementedError()

    override suspend fun nicheTopCreators(id: String) = throw NotImplementedError()

    override suspend fun nicheRelated(id: String) = throw NotImplementedError()

    override suspend fun likedFeed(
        count: Int,
        page: Int,
    ): GifsPageDto {
        likedFeedCalls.add(page)
        likedHttpErrors[page]?.let { code -> throw httpError(code) }
        return likedPages[page] ?: GifsPageDto()
    }

    override suspend fun feedForYou(
        count: Int,
        page: Int,
    ): GifsPageDto {
        forYouCalls.add(page)
        forYouHttpErrors[page]?.let { code -> throw httpError(code) }
        return forYouPages[page] ?: GifsPageDto()
    }

    override suspend fun likedIds(): List<String> = likedIdsResult

    override suspend fun likeGif(
        id: String,
        body: LikeBody,
    ) = throw NotImplementedError()

    override suspend fun unlikeGif(
        id: String,
        body: LikeBody,
    ) = throw NotImplementedError()

    override suspend fun followCreator(
        username: String,
        body: FollowBody,
    ) = throw NotImplementedError()

    override suspend fun unfollowCreator(
        username: String,
        body: FollowBody,
    ) = throw NotImplementedError()

    override suspend fun followedCreators(): List<String> = throw NotImplementedError()

    override suspend fun subscribeNiche(
        nicheId: String,
        body: EmptyBody,
    ) = throw NotImplementedError()

    override suspend fun unsubscribeNiche(
        nicheId: String,
        body: EmptyBody,
    ) = throw NotImplementedError()

    override suspend fun followedNiches(): FollowedNichesDto = throw NotImplementedError()

    override suspend fun addToCollection(
        folderId: String,
        body: CollectionGifBody,
    ) = throw NotImplementedError()

    override suspend fun removeFromCollection(
        folderId: String,
        body: CollectionGifBody,
    ) = throw NotImplementedError()

    override suspend fun addToNiche(
        gifId: String,
        body: NicheAddBody,
    ) = throw NotImplementedError()

    override suspend fun removeFromNiche(
        gifId: String,
        body: NicheAddBody,
    ) = throw NotImplementedError()

    override suspend fun userStats(username: String) = throw NotImplementedError()
}

/** Retrofit error shape for paging-source tests (pool-cap 400 / plain 5xx). */
internal fun httpError(code: Int): retrofit2.HttpException =
    retrofit2.HttpException(
        retrofit2.Response.error<Any>(code, okhttp3.ResponseBody.create(null as okhttp3.MediaType?, "error".toByteArray())),
    )
