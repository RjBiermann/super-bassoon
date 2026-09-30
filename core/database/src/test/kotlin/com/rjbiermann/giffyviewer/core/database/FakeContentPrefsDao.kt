package com.rjbiermann.giffyviewer.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory ContentPrefsDao for unit tests (no Room). */
class FakeContentPrefsDao : ContentPrefsDao {
    val creators = MutableStateFlow<List<CreatorPrefEntity>>(emptyList())
    val tags = MutableStateFlow<List<TagPrefEntity>>(emptyList())
    val keywords = MutableStateFlow<List<KeywordBlockEntity>>(emptyList())

    override suspend fun upsertCreator(pref: CreatorPrefEntity) {
        creators.update { it.filterNot { c -> c.username == pref.username } + pref }
    }

    override suspend fun upsertTag(pref: TagPrefEntity) {
        tags.update { it.filterNot { t -> t.tag == pref.tag } + pref }
    }

    override suspend fun blockKeyword(pattern: KeywordBlockEntity) {
        keywords.update { it.filterNot { k -> k.pattern == pattern.pattern } + pattern }
    }

    override suspend fun blockedCreators(): List<String> = creators.value.filter { it.state == "BLOCKED" }.map { it.username }

    override suspend fun favoriteCreators(): List<String> = creators.value.filter { it.state == "FAVORITED" }.map { it.username }

    override suspend fun blockedTags(): List<String> = tags.value.filter { it.state == "BLOCKED" }.map { it.tag }

    override suspend fun blockedKeywords(): List<String> = keywords.value.map { it.pattern }

    override suspend fun unblockCreator(username: String) {
        creators.update { it.filterNot { c -> c.username == username } }
    }

    override suspend fun clearTag(tag: String) {
        tags.update { it.filterNot { t -> t.tag == tag } }
    }

    override suspend fun unfavoriteTag(tag: String) {
        tags.update { it.filterNot { t -> t.tag == tag && t.state == "FAVORITED" } }
    }

    override suspend fun unblockKeyword(pattern: String) {
        keywords.update { it.filterNot { k -> k.pattern == pattern } }
    }

    override fun blockedCreatorsFlow(): Flow<List<String>> =
        MutableStateFlow(creators.value.filter { it.state == "BLOCKED" }.map { it.username })

    override fun favoriteCreatorsFlow(): Flow<List<String>> =
        MutableStateFlow(creators.value.filter { it.state == "FAVORITED" }.map { it.username })

    override fun blockedTagsFlow(): Flow<List<String>> = MutableStateFlow(tags.value.filter { it.state == "BLOCKED" }.map { it.tag })

    override fun favoriteTagsFlow(): Flow<List<String>> = MutableStateFlow(tags.value.filter { it.state == "FAVORITED" }.map { it.tag })

    override fun tagState(tag: String): Flow<String?> = MutableStateFlow(tags.value.firstOrNull { it.tag == tag }?.state)

    override fun blockedKeywordsFlow(): Flow<List<String>> = MutableStateFlow(keywords.value.map { it.pattern })

    override fun creatorState(username: String): Flow<String?> =
        MutableStateFlow(creators.value.firstOrNull { it.username == username }?.state)

    override suspend fun allCreatorPrefs(): List<CreatorPrefEntity> = creators.value

    override suspend fun allTagPrefs(): List<TagPrefEntity> = tags.value

    override suspend fun allKeywordBlocks(): List<KeywordBlockEntity> = keywords.value
}
