package com.rjbiermann.giffyviewer.core.network

import com.rjbiermann.giffyviewer.core.network.dto.GifDtoShell
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import com.rjbiermann.giffyviewer.core.network.dto.TemporaryTokenDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * the site's undocumented API (PLAN §2, live-verified 2026). Endpoint paths only may
 * drift — architecture doesn't.
 */
interface GifsApi {
    @GET("v2/auth/temporary")
    suspend fun temporaryToken(): TemporaryTokenDto

    /** Probe-only: candidate date-range param shapes (gate: date chips). */
    @GET("v2/gifs/search")
    suspend fun searchDateProbe(
        @Query("search_text") text: String = "dance",
        @Query("createdAfter") createdAfter: String? = null,
        @Query("created_after") created_after: String? = null,
        @Query("date") date: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("count") count: Int = 40,
    ): GifsPageDto

    @GET("v2/gifs/search")
    suspend fun search(
        @Query("search_text") searchText: String,
        @Query("order") order: String = "trending",
        @Query("count") count: Int = 40,
        @Query("page") page: Int = 1,
        // Full-scope search spec (AGENTS-APP): the site sends type=g for the
        // GIFs tab explicitly (default g); type=i is the Images tab (stills).
        @Query("type") type: String? = null,
    ): GifsPageDto

    /** One creator's gifs — live-verified: filters by userName, paginated.
     *  Orders verified 2026-09-30: trending, oldest, latest, top, top7, top28. */
    @GET("v2/users/{username}/search")
    suspend fun userGifs(
        @Path("username") username: String,
        @Query("count") count: Int = 40,
        @Query("page") page: Int = 1,
        @Query("order") order: String = "trending",
    ): GifsPageDto

    @GET("v2/feeds/trending/popular")
    suspend fun trendingPopular(
        @Query("count") count: Int = 40,
        @Query("page") page: Int = 1,
        // order=top_week = Top This Week feed (live-verified 2026-09-30:
        // different ordering from the default)
        @Query("order") order: String? = null,
    ): GifsPageDto

    /** Niches (live-verified 2026-09-30, anonymous OK): paginated taxonomy.
     *  Live-verified 2026-10: category filter (one name from v2/niches/categories,
     *  omit/empty for all) and order — accepted: posts, subscribers, best_match,
     *  alphabetical_asc, alphabetical_desc, random (trending 400s BadOrder). */
    @GET("v2/niches")
    suspend fun niches(
        @Query("count") count: Int = 60,
        @Query("page") page: Int = 1,
        @Query("category") category: String? = null,
        @Query("order") order: String? = null,
    ): NichesPageDto

    /** Niche category names for the filter chips (live-verified 2026-10, anonymous OK). */
    @GET("v2/niches/categories")
    suspend fun nicheCategories(): NicheCategoriesDto

    /** Niches feed (live-verified 2026-10-01, anonymous OK). Live drift 2026-10:
     *  the server now REJECTS order=trending (BadOrder — despite its own error
     *  message listing it); omit the param for the default ordering. */
    @GET("v2/niches/{id}/gifs")
    suspend fun nicheGifs(
        @Path("id") nicheId: String,
        @Query("count") count: Int = 40,
        @Query("page") page: Int = 1,
        @Query("order") order: String? = null,
    ): GifsPageDto

    /** Verified-creator list (live-verified 2026-10-01, anonymous OK) — the
     *  Explore surface (§9: site Explore = Top Creators). Server pages in 10s. */
    @GET("v2/creators/verified")
    suspend fun verifiedCreators(
        @Query("count") count: Int = 20,
        @Query("page") page: Int = 1,
    ): VerifiedCreatorsPageDto

    /** Typed autocomplete (live-verified 2026-09-30, anonymous OK): returns a
     *  bare JSON array of {type, text, gifs-count}. */
    @GET("v2/search/suggest")
    suspend fun suggest(
        @Query("query") query: String,
    ): List<SuggestDto>

    /** Trending tags (live-verified 2026-10-02): the site search "Tags" tab. */
    @GET("v2/tags/trending")
    suspend fun trendingTags(
        @Query("count") count: Int = 20,
    ): TrendingTagsDto

    /** Creator preview tiles (full-scope search spec, live-verified 2026-10-02,
     *  anonymous OK): one preview gif per matched creator — dedupe by gif.userName. */
    @GET("v2/creators/search/previews")
    suspend fun creatorSearchPreviews(
        @Query("query") query: String,
        @Query("order") order: String = "best_match",
        @Query("count") count: Int = 30,
        @Query("page") page: Int = 1,
    ): CreatorPreviewsDto

    /** Niche preview tiles (same spec): {niche, gif} rows — owner unused. */
    @GET("v2/niches/search/previews")
    suspend fun nicheSearchPreviews(
        @Query("query") query: String,
        @Query("order") order: String = "best_match",
        @Query("count") count: Int = 30,
        @Query("page") page: Int = 1,
    ): NicheSearchPreviewsDto

    /** Tag-name matches (same spec): bare JSON array ("[\"Feet\"]"). */
    @GET("v1/tags/match")
    suspend fun tagsMatch(
        @Query("query") query: String,
    ): List<String>

    /** Creator search (live-verified 2026-09-30, anonymous OK): paginated
     *  creator cards with follower/gif counts — results-row source. */
    @GET("v2/creators/search")
    suspend fun creatorsSearch(
        @Query("query") query: String,
        @Query("count") count: Int = 8,
    ): CreatorSearchPageDto

    /** Personalized server feed — verified exists (401 anonymous); logged-in only. */
    @GET("v2/feeds/for-you")
    suspend fun feedForYou(
        @Query("count") count: Int = 40,
        @Query("page") page: Int = 1,
    ): GifsPageDto

    @GET("v2/feeds/liked")
    suspend fun likedFeed(
        @Query("count") count: Int = 40,
        @Query("page") page: Int = 1,
    ): GifsPageDto

    // --- account actions (verified: JSON content-type + body required, bare calls 400) ---

    @PUT("v2/gifs/{id}/like")
    suspend fun likeGif(
        @Path("id") id: String,
        @Body body: LikeBody = LikeBody(),
    )

    @HTTP(method = "DELETE", path = "v2/gifs/{id}/like", hasBody = true)
    suspend fun unlikeGif(
        @Path("id") id: String,
        @Body body: LikeBody = LikeBody(),
    )

    @GET("v2/likes")
    suspend fun likedIds(): List<String>

    @PUT("v1/me/follows/{username}")
    suspend fun followCreator(
        @Path("username") username: String,
        @Body body: FollowBody = FollowBody(),
    )

    @DELETE("v1/me/follows/{username}")
    suspend fun unfollowCreator(
        @Path("username") username: String,
        @Body body: FollowBody = FollowBody(),
    )

    @GET("v1/me/follows")
    suspend fun followedCreators(): List<String>

    @POST("v2/niches/{id}/subscription")
    suspend fun subscribeNiche(
        @Path("id") nicheId: String,
        @Body body: EmptyBody = EmptyBody(),
    )

    @DELETE("v2/niches/{id}/subscription")
    suspend fun unsubscribeNiche(
        @Path("id") nicheId: String,
        @Body body: EmptyBody = EmptyBody(),
    )

    @GET("v2/niches/following")
    suspend fun followedNiches(): FollowedNichesDto

    /** Saved Collections (live-verified 2026-10-01): list = {collections, users,
     *  page, pages, totalCount}; create body REQUIRES {name, published}; rename
     *  = PATCH {folderName} (PUT 405); delete by folderId. */
    @GET("v2/me/collections")
    suspend fun meCollections(
        @Query("page") page: Int = 1,
        @Query("count") count: Int = 50,
    ): CollectionsPageDto

    @POST("v2/me/collections")
    suspend fun createCollection(
        @Body body: CreateCollectionBody,
    ): CollectionDto

    @HTTP(method = "PATCH", path = "v2/me/collections/{id}", hasBody = true)
    suspend fun renameCollection(
        @Path("id") id: String,
        @Body body: RenameCollectionBody,
    ): CollectionDto

    @DELETE("v2/me/collections/{id}")
    suspend fun deleteCollection(
        @Path("id") id: String,
    )

    /** Add gif to a saved collection (live-probed 2026-10: POST {gifId} → 204;
     *  remove = DELETE {gifId} → 204). */
    @POST("v2/me/collections/{id}/gifs")
    suspend fun addToCollection(
        @Path("id") folderId: String,
        @Body body: CollectionGifBody,
    )

    @HTTP(method = "DELETE", path = "v2/me/collections/{id}/gifs", hasBody = true)
    suspend fun removeFromCollection(
        @Path("id") folderId: String,
        @Body body: CollectionGifBody,
    )

    /** Add a gif to a joined niche (live-probed 2026-10: PUT {nicheId} → 202;
     *  remove = DELETE {nicheId} → 202). */
    @HTTP(method = "PUT", path = "v2/gifs/{id}/niches", hasBody = true)
    suspend fun addToNiche(
        @Path("id") gifId: String,
        @Body body: NicheAddBody,
    )

    @HTTP(method = "DELETE", path = "v2/gifs/{id}/niches", hasBody = true)
    suspend fun removeFromNiche(
        @Path("id") gifId: String,
        @Body body: NicheAddBody,
    )

    /** Creator profile stats (live-verified 2026-10): the site profile's
     *  counts; v2/user_profile 404s — this is the real profile read. */
    @GET("v1/users/{username}")
    suspend fun userStats(
        @Path("username") username: String,
    ): UserStatsDto

    /** Niche detail (live-verified 2026-10-01, anonymous OK): description,
     *  cover, counts, rules — the About tab source. `following` reflects the
     *  caller's auth state. */
    @GET("v2/niches/{id}")
    suspend fun nicheDetail(
        @Path("id") id: String,
    ): NicheDetailDto

    /** Niche top-creators (live-verified 2026-10-01, anonymous OK). */
    @GET("v2/niches/{id}/top-creators")
    suspend fun nicheTopCreators(
        @Path("id") id: String,
    ): VerifiedCreatorsPageDto

    /** Related niches (live-verified 2026-10-01, anonymous OK). */
    @GET("v2/niches/{id}/related")
    suspend fun nicheRelated(
        @Path("id") id: String,
    ): FollowedNichesDto

    /** Following (live-verified 2026-10-01): rich creator objects, paginated. */
    @GET("v2/me/following")
    suspend fun followingCreators(
        @Query("page") page: Int = 1,
        @Query("count") count: Int = 100,
    ): FollowingCreatorsDto

    /** Niche join/leave (API word: subscription; UI: Join/Leave Niche — §9 lingo)
     *  lives on [subscribeNiche]/[unsubscribeNiche] ({} body, verified 202). */
}

@Serializable
data class NicheDetailDto(
    val niche: NicheDetail = NicheDetail(),
)

@Serializable
data class NicheDetail(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val cover: String? = null,
    val thumbnail: String? = null,
    val gifs: Int = 0,
    val subscribers: Int = 0,
    val rules: List<String>? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class CollectionUserDto(
    val name: String = "",
    val followers: Int = 0,
    val gifs: Int = 0,
)

@Serializable
data class CollectionDto(
    val folderId: String = "",
    val folderName: String? = null,
    val description: String? = null,
    val contentCount: Int = 0,
    val published: Boolean = false,
    val thumb: String? = null,
)

@Serializable
data class CollectionsPageDto(
    val collections: List<CollectionDto> = emptyList(),
    val users: List<CollectionUserDto> = emptyList(),
    val page: Int = 1,
    val pages: Int = 1,
    val totalCount: Int = 0,
)

@Serializable
data class CreateCollectionBody(
    val name: String,
    val published: Boolean = false,
)

@Serializable
data class RenameCollectionBody(
    @SerialName("folderName") val folderName: String,
)

@Serializable
data class CollectionGifBody(
    val gifId: String,
)

@Serializable
data class NicheAddBody(
    val nicheId: String,
)

/** v1/users/{username} — the site profile read (v2/user_profile 404s). */
@Serializable
data class UserStatsDto(
    val username: String = "",
    val followers: Long = 0,
    val following: Long = 0,
    val gifs: Long = 0,
    val likes: Long = 0,
    val views: Long = 0,
    val verified: Boolean = false,
    @SerialName("profileImageUrl") val profileImageUrl: String? = null,
    val description: String? = null,
)

@Serializable
data class FollowingCreatorsDto(
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
    val items: List<CreatorSearchItemDto> = emptyList(),
)

@Serializable
data class LikeBody(
    val context: String = "trending",
    val source: String = "watchlist",
    val position: Int = 0,
)

@Serializable
data class FollowBody(
    val source: String = "profile",
    val source_id: String = "giffy-viewer",
    val position: Int = 0,
)

@Serializable
data class EmptyBody(
    val v: Int = 1,
)

@Serializable
data class FollowedNichesDto(
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
    val niches: List<FollowedNicheDto> = emptyList(),
)

@Serializable
data class FollowedNicheDto(
    val id: String,
    val name: String? = null,
    val gifs: Int = 0,
    val subscribers: Int = 0,
    val tags: List<String> = emptyList(),
)

@Serializable
data class NicheDto(
    val id: String,
    val name: String,
    val gifs: Long = 0,
    val subscribers: Long = 0,
    val tags: List<String> = emptyList(),
)

@Serializable
data class NicheCategoriesDto(
    val categories: List<String> = emptyList(),
)

@Serializable
data class NichesPageDto(
    val page: Int = 1,
    val pages: Int = 1,
    val niches: List<NicheDto> = emptyList(),
)

/** /v2/search/suggest row — tag-type suggestions tap into a tag feed. */
@Serializable
data class SuggestDto(
    val type: String = "tag",
    val text: String = "",
    val gifs: Int = 0,
)

/** /v2/tags/trending — the site search's 4th "Tags" tab source (live-verified
 *  2026-10-02: `{tags: [{name, count}]}`). App parity: trending-tags section in
 *  the search screen's empty state, tap = tag search feed. */
@Serializable
data class TrendingTagsDto(
    val tags: List<TrendingTagDto> = emptyList(),
)

@Serializable
data class TrendingTagDto(
    val name: String = "",
    val count: Int = 0,
)

/** /v2/creators/search/previews (full-scope search spec, live-verified
 *  2026-10-02): one preview gif per matched creator. */
@Serializable
data class CreatorPreviewsDto(
    val gifs: List<GifDtoShell> = emptyList(),
)

/** /v2/niches/search/previews (same spec): `{previews: [{niche, gif, user}]}` —
 *  only niche + gif consumed. */
@Serializable
data class NicheSearchPreviewsDto(
    val previews: List<NichePreviewRowDto> = emptyList(),
)

@Serializable
data class NichePreviewRowDto(
    val niche: NichePreviewNicheDto? = null,
    val gif: GifDtoShell? = null,
)

/** Lenient niche object inside a preview row (only id/name consumed; the full
 *  niche payload carries far more). */
@Serializable
data class NichePreviewNicheDto(
    val id: String = "",
    val name: String? = null,
)

/** /v2/creators/search item — username is the identity; counts for the row. */
@Serializable
data class CreatorSearchItemDto(
    val username: String = "",
    val followers: Int = 0,
    val gifs: Int = 0,
    @SerialName("publishedCollections") val publishedCollections: Int = 0,
    @SerialName("profileImageUrl") val profileImageUrl: String? = null,
    /** Creator verified badge (live 2026-10: `verified` on every creator object). */
    val verified: Boolean = false,
)

@Serializable
data class VerifiedCreatorsPageDto(
    val creators: List<CreatorSearchItemDto> = emptyList(),
)

@Serializable
data class CreatorSearchPageDto(
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
    val items: List<CreatorSearchItemDto> = emptyList(),
)
