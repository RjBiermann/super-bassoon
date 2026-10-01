package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.NicheGroupEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Groups screen state (PLAN §6/§7): user-defined tag bundles. FAVORITED groups
 * pin as tabs, BLOCKED groups feed the ContentFilter (leak-zero via stage 2),
 * NEUTRAL groups are preview-only.
 */
@HiltViewModel
class GroupsViewModel
    @Inject
    constructor(
        private val db: GiffyDatabase,
    ) : ViewModel() {
        val groups: StateFlow<List<NicheGroupEntity>> =
            db.nicheGroupDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun create(
            name: String,
            tagList: List<String>,
        ) {
            val tags = tagList.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }
            if (name.isBlank() || tags.isEmpty()) return
            viewModelScope.launch {
                db.nicheGroupDao().upsert(
                    NicheGroupEntity(
                        name = name.trim(),
                        tagList = tags.joinToString(","),
                        state = "NEUTRAL",
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

        /** FAVORITED → pinned tab · NEUTRAL → preview · BLOCKED → macro-filter. */
        fun cycleState(group: NicheGroupEntity) {
            val next =
                when (group.state) {
                    "FAVORITED" -> "BLOCKED"
                    "BLOCKED" -> "NEUTRAL"
                    else -> "FAVORITED"
                }
            viewModelScope.launch {
                db.nicheGroupDao().upsert(group.copy(state = next))
            }
        }

        fun delete(group: NicheGroupEntity) {
            viewModelScope.launch { db.nicheGroupDao().delete(group.id) }
        }
    }
