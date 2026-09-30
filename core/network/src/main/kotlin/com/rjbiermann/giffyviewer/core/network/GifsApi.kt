package com.rjbiermann.giffyviewer.core.network

import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import com.rjbiermann.giffyviewer.core.network.dto.TemporaryTokenDto
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
 * upstream undocumented API (PLAN §2, live-verified 2026). Endpoint paths only may
 * drift — architecture doesn't.
 */
interface upstreamApi {
    @GET("v2/auth/temporary")
    suspend fun temporaryToken(): TemporaryTokenDto

    @GET("v2/gifs/search")
    suspend fun search(
        @Query("search_text") searchText: String,
        @Query("order") order: String = "trending",
        @Query("count") count: Int = 40,
        @Query("page") page: Int = 1,
    ): GifsPageDto

    /** One creator's gifs — live-verified: filters by userName, paginated. */
    @GET("v2/users/{username}/search")
    suspend fun userGifs(
        @Path("username") username: String,
        @Query("count") count: Int = 40,
        @Query("page") page: Int = 1,
    ): GifsPageDto

    @GET("v2/feeds/trending/popular")
    suspend fun trendingPopular(
        @Query("count") count: Int = 40,
        @Query("page") page: Int = 1,
        // order=top_week = Top This Week feed (live-verified 2026-09-30:
        // different ordering from the default)
        @Query("order") order: String? = null,
    ): GifsPageDto

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
}

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
