package com.rjbiermann.giffyviewer.feature.feed

import android.os.Bundle
import androidx.navigation.NavType
import com.rjbiermann.giffyviewer.core.model.Gif
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * NavType implementations for the type-safe route args the shells pass
 * (issue #13): Android nav only auto-derives NavTypes for Json-encodable
 * NON-polymorphic types — `FeedSource` (sealed polymorphic) and `List<Gif>`
 * ride explicit NavTypes instead (device-gate 2026-10-16: bare
 * `MainFeed(source: FeedSource?)` crashed with "could not find any NavType").
 *
 * The wire is kotlinx JSON via [FeedSource]/[Gif]'s own `@Serializable`
 * family — the payload lives only inside nav saved state (never a public
 * format, issue #13 §2). [FeedSourceRoutes] owns the one shared JSON codec
 * both NavTypes use (defaults matter for decode).
 */

private val RouteJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/**
 * Route-string wire wrapper (issue #34 device crash): nav treats `/` as the
 * path-arg delimiter in its encoded route strings, and a custom NavType's
 * `serializeAsValue` is embedded verbatim — raw JSON (gifs carrying a
 * description like "https://onlyfans.com/…") injected extra segments and
 * crashed mid-navigation ("…/0 cannot be found in the navigation graph").
 * Base64-urlsafe is route-segment-safe (alphabet has no `/`), stdlib-only,
 * and the payload stays an opaque envelope — never a public format.
 */
@OptIn(ExperimentalEncodingApi::class)
private val RouteWire = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

@OptIn(ExperimentalEncodingApi::class)
private fun routeEncode(json: String): String = RouteWire.encode(json.encodeToByteArray())

@OptIn(ExperimentalEncodingApi::class)
private fun routeDecode(wire: String): String = RouteWire.decode(wire).decodeToString()

private const val ROUTE_NAME_FEED_SOURCE = "feed_source"

/**
 * `FeedSource` on routes (MainFeed(source: FeedSource?), TvFeed).Nullable
 * allowed (the start destination passes null = seeded default).
 */
val feedSourceNavType: NavType<FeedSource> =
    object : NavType<FeedSource>(isNullableAllowed = true) {
        override val name: String = ROUTE_NAME_FEED_SOURCE

        override fun get(
            bundle: Bundle,
            key: String,
        ): FeedSource? = bundle.getString(key)?.takeIf { it.isNotEmpty() }?.let { FeedSourceRoutes.decode(routeDecode(it)) }

        override fun parseValue(value: String): FeedSource {
            // Nullable-allowed NavType encodes null as the empty string
            // (start-destination RouteEncoder.encodeNull contract).
            return if (value.isEmpty()) FeedSource.Trending else FeedSourceRoutes.decode(routeDecode(value))
        }

        override fun serializeAsValue(value: FeedSource): String = routeEncode(FeedSourceRoutes.encode(value))

        override fun put(
            bundle: Bundle,
            key: String,
            value: FeedSource,
        ) {
            bundle.putString(key, serializeAsValue(value))
        }
    }

private const val ROUTE_NAME_GIF_LIST = "gif_list"

/**
 * `List<Gif>` on routes (TvPlayer's snapshot window). Non-nullable; a route
 * never opens a player without the window.
 */
val gifListNavType: NavType<List<Gif>> =
    object : NavType<List<Gif>>(isNullableAllowed = false) {
        override val name: String = ROUTE_NAME_GIF_LIST

        override fun get(
            bundle: Bundle,
            key: String,
        ): List<Gif> = bundle.getString(key, "[]").let { parseValue(it) }

        override fun parseValue(value: String): List<Gif> = RouteJson.decodeFromString(jsonSerializer, routeDecode(value))

        override fun serializeAsValue(value: List<Gif>): String = routeEncode(RouteJson.encodeToString(jsonSerializer, value))

        override fun put(
            bundle: Bundle,
            key: String,
            value: List<Gif>,
        ) {
            bundle.putString(key, routeEncode(RouteJson.encodeToString(jsonSerializer, value)))
        }

        private val jsonSerializer: KSerializer<List<Gif>> = serializer()
    }

/** Encode/decode side for the route NavTypes — colocated so the JSON config stays one. */
object FeedSourceRoutes {
    fun encode(source: FeedSource): String = RouteJson.encodeToString(FeedSource.serializer(), source)

    fun decode(payload: String): FeedSource = RouteJson.decodeFromString(FeedSource.serializer(), payload)

    fun encodeNiche(niche: FeedSource.Niche): String = RouteJson.encodeToString(FeedSource.Niche.serializer(), niche)

    fun decodeNiche(payload: String): FeedSource.Niche = RouteJson.decodeFromString(FeedSource.Niche.serializer(), payload)
}

private const val ROUTE_NAME_NICHE = "feed_niche"

/**
 * `FeedSource.Niche` on routes (TvNicheAbout's nested arg — niche detail
 * carries its own pool family, same JSON codec as the FeedSource type).
 */
val nicheNavType: NavType<FeedSource.Niche> =
    object : NavType<FeedSource.Niche>(isNullableAllowed = false) {
        override val name: String = ROUTE_NAME_NICHE

        override fun get(
            bundle: Bundle,
            key: String,
        ): FeedSource.Niche = FeedSourceRoutes.decodeNiche(routeDecode(bundle.getString(key, "")))

        override fun parseValue(value: String): FeedSource.Niche = FeedSourceRoutes.decodeNiche(routeDecode(value))

        override fun serializeAsValue(value: FeedSource.Niche): String = routeEncode(FeedSourceRoutes.encodeNiche(value))

        override fun put(
            bundle: Bundle,
            key: String,
            value: FeedSource.Niche,
        ) {
            bundle.putString(key, routeEncode(FeedSourceRoutes.encodeNiche(value)))
        }
    }
