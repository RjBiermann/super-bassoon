package com.rjbiermann.giffyviewer.core.network.dto

import com.rjbiermann.giffyviewer.core.model.Gif
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class GifDtoShell(
    val id: String,
    val userName: String? = null,
    val tags: List<String> = emptyList(),
    val likes: Long = 0,
    val views: Long = 0,
    val duration: Double = 0.0,
    val hasAudio: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val createDate: Long = 0,
    val published: Boolean = true,
    val avgColor: String? = null,
    val urls: UrlsDto? = null,
    @Serializable(with = NicheListSerializer::class)
    val niches: List<NicheRefDto> = emptyList(),
)

/**
 * Niches arrive as plain strings ("bigger-than-you-thought") in some endpoints
 * and as objects ({id, name}) in others. Accepts both, normalizes to id.
 */
object NicheListSerializer : KSerializer<List<NicheRefDto>> {
    private val elementSerializer = NicheRefDto.serializer()
    override val descriptor: SerialDescriptor =
        ListSerializer(elementSerializer).descriptor

    override fun deserialize(decoder: Decoder): List<NicheRefDto> {
        val json = decoder as? JsonDecoder ?: error("niches must be JSON")
        return when (val el = json.decodeJsonElement()) {
            is JsonArray ->
                el
                    .map { item ->
                        when (item) {
                            is JsonPrimitive -> NicheRefDto(id = item.content)
                            is JsonObject -> {
                                val id = item["id"]?.jsonPrimitive?.content
                                val name = item["name"]?.jsonPrimitive?.content
                                if (id != null) NicheRefDto(id = id, name = name) else null
                            }
                            else -> null
                        }
                    }.filterNotNull()
            else -> emptyList()
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: List<NicheRefDto>,
    ) {
        encoder.encodeSerializableValue(
            ListSerializer(elementSerializer),
            value,
        )
    }
}

@Serializable
data class UrlsDto(
    val sd: String? = null,
    val hd: String? = null,
    val poster: String? = null,
)

@Serializable
data class NicheRefDto(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
data class GifsPageDto(
    val gifs: List<GifDtoShell> = emptyList(),
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
)

@Serializable
data class TemporaryTokenDto(
    val token: String,
    val addr: String? = null,
    val agent: String? = null,
    val session: String? = null,
    val expiry_date: Long? = null,
)

/** Live payload maps niches as objects {id, name}; tags are plain strings. */
fun GifDtoShell.toModel(): Gif =
    Gif(
        id = id,
        userName = userName.orEmpty(),
        tags = tags,
        likes = likes,
        views = views,
        durationSeconds = duration,
        hasAudio = hasAudio,
        width = width,
        height = height,
        createDateEpoch = createDate,
        published = published,
        avgColor = avgColor.orEmpty(),
        sdUrl = urls?.sd,
        hdUrl = urls?.hd,
        posterUrl = urls?.poster,
        niches = niches.mapNotNull { it.id },
    )

fun GifsPageDto.toModels(): List<Gif> = gifs.map { it.toModel() }
