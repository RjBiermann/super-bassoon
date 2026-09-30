package com.rjbiermann.giffyviewer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PLAN §6: per-feed prefs, age gate, settings — local-only, offline-capable.
 * Age gate key: PLAN §3 — start destination blocks content until set.
 */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private object Keys {
            val AGE_CONFIRMED_AT = booleanPreferencesKey("age_confirmed_at")
            val DATA_SAVER = booleanPreferencesKey("data_saver")
            val BLOCK_HINT_SHOWN = booleanPreferencesKey("block_hint_shown")
            val AMOLED = booleanPreferencesKey("amoled")
            val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        }

        /** True once the user attested 18+. Emits false until then; survives restarts. */
        val ageConfirmed: Flow<Boolean> = dataStore.data.map { it[Keys.AGE_CONFIRMED_AT] ?: false }

        val dataSaver: Flow<Boolean> = dataStore.data.map { it[Keys.DATA_SAVER] ?: false }

        suspend fun confirmAge() {
            dataStore.edit { it[Keys.AGE_CONFIRMED_AT] = true }
        }

        /** Settings option to re-run the gate (PLAN §3). */
        suspend fun resetAgeGate() {
            dataStore.edit { it.remove(Keys.AGE_CONFIRMED_AT) }
        }

        suspend fun setDataSaver(enabled: Boolean) {
            dataStore.edit { it[Keys.DATA_SAVER] = enabled }
        }

        /** One-time coach mark: long-press tiles to block content (PLAN §9 discoverability). */
        val blockHintShown: Flow<Boolean> =
            dataStore.data.map { it[Keys.BLOCK_HINT_SHOWN] ?: false }

        suspend fun markBlockHintShown() {
            dataStore.edit { it[Keys.BLOCK_HINT_SHOWN] = true }
        }

        /** PLAN §9 theme options: true-black AMOLED, opt-in dynamic color. */
        val amoled: Flow<Boolean> = dataStore.data.map { it[Keys.AMOLED] ?: false }

        val dynamicColor: Flow<Boolean> = dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: false }

        suspend fun setAmoled(enabled: Boolean) {
            dataStore.edit { it[Keys.AMOLED] = enabled }
        }

        suspend fun setDynamicColor(enabled: Boolean) {
            dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
        }
    }
