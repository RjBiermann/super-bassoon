package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.network.CollectionDto
import com.rjbiermann.giffyviewer.core.network.CreateCollectionBody
import com.rjbiermann.giffyviewer.core.network.upstreamApi
import com.rjbiermann.giffyviewer.core.network.RenameCollectionBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Saved Collections (PLAN §7 — site header "Saved Collections", verified
 * 2026-10-01). Server state = source of truth: list from `GET /v2/me/collections`;
 * create (POST, body requires `published`) + rename (PATCH `folderName`) +
 * delete (200 by folderId) — write shapes live-probed before building.
 */
@HiltViewModel
class CollectionsViewModel
    @Inject
    constructor(
        private val api: upstreamApi,
        tokenStore: TokenStore,
    ) : ViewModel() {
        val loggedIn = tokenStore.tokenOrNull() != null

        private val _collections = MutableStateFlow<List<CollectionDto>>(emptyList())
        val collections: StateFlow<List<CollectionDto>> = _collections

        fun refresh() {
            if (!loggedIn) return
            viewModelScope.launch {
                runCatching { api.meCollections() }
                    .onSuccess { _collections.value = it.collections }
            }
        }

        fun create(name: String) {
            if (!loggedIn || name.isBlank()) return
            viewModelScope.launch {
                runCatching { api.createCollection(CreateCollectionBody(name = name.trim())) }
                    .onSuccess { created ->
                        // Live-probed quirk: creation ignores the `name` field —
                        // the name only sets via a PATCH of `folderName` after.
                        if (created.folderName.isNullOrBlank()) {
                            runCatching { api.renameCollection(created.folderId, RenameCollectionBody(folderName = name.trim())) }
                        }
                        refresh()
                    }
            }
        }

        fun rename(
            id: String,
            name: String,
        ) {
            if (!loggedIn || name.isBlank()) return
            viewModelScope.launch {
                runCatching { api.renameCollection(id, RenameCollectionBody(folderName = name.trim())) }
                    .onSuccess { refresh() }
            }
        }

        fun delete(id: String) {
            if (!loggedIn) return
            viewModelScope.launch {
                runCatching { api.deleteCollection(id) }
                    .onSuccess { refresh() }
            }
        }
    }
