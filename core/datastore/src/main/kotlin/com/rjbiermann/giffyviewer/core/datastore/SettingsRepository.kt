package com.rjbiermann.giffyviewer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
            val MUTED = booleanPreferencesKey("muted")
            val AUTO_SWIPE = booleanPreferencesKey("auto_swipe")
            val PIN_HASH = stringPreferencesKey("pin_hash")
            val PINNED_NICHES = stringSetPreferencesKey("pinned_niches")
            val PINNED_CREATORS = stringSetPreferencesKey("pinned_creators")
            val GRID_COLUMNS = intPreferencesKey("grid_columns")
            val FORYOU_SCOPE = stringPreferencesKey("foryou_scope")
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

        /** Player mute toggle — persists across sessions (PLAN §9). */
        val muted: Flow<Boolean> = dataStore.data.map { it[Keys.MUTED] ?: false }

        suspend fun setMuted(enabled: Boolean) {
            dataStore.edit { it[Keys.MUTED] = enabled }
        }

        /** Auto-advance to the next video on natural end (PLAN §9); data-saver forces off. */
        val autoSwipe: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_SWIPE] ?: false }

        suspend fun setAutoSwipe(enabled: Boolean) {
            dataStore.edit { it[Keys.AUTO_SWIPE] = enabled }
        }

        /** Pinned niche ids — Home chip row tabs (PLAN §7 pin-to-tabs). */
        val pinnedNiches: Flow<Set<String>> =
            dataStore.data.map { it[Keys.PINNED_NICHES] ?: emptySet() }

        suspend fun togglePinnedNiche(
            id: String,
            name: String,
        ) {
            dataStore.edit {
                val cur = it[Keys.PINNED_NICHES] ?: emptySet()
                it[Keys.PINNED_NICHES] =
                    if (cur.any { e -> e.startsWith("$id|") }) {
                        cur.filterNot { e -> e.startsWith("$id|") }.toSet()
                    } else {
                        cur + "$id|$name"
                    }
            }
        }

        /** Grid columns (PLAN §6, responsive-first): 0 = Auto (width-derived). */
        val gridColumns: Flow<Int> = dataStore.data.map { it[Keys.GRID_COLUMNS] ?: 0 }

        suspend fun setGridColumns(columns: Int) {
            dataStore.edit { it[Keys.GRID_COLUMNS] = columns }
        }

        /** For You scope (§7 Creators · Niches · All, logged-in; default All). */
        val forYouScope: Flow<String> = dataStore.data.map { it[Keys.FORYOU_SCOPE] ?: "all" }

        suspend fun setForYouScope(scope: String) {
            dataStore.edit { it[Keys.FORYOU_SCOPE] = scope }
        }

        /** Per-feed server sort (PLAN §8): dynamic keys "sort:<baseKey>", "" = default. */
        fun feedSort(baseKey: String): Flow<String> = dataStore.data.map { it[stringPreferencesKey("sort:$baseKey")] ?: "" }

        suspend fun setFeedSort(
            baseKey: String,
            sort: String,
        ) {
            dataStore.edit { it[stringPreferencesKey("sort:$baseKey")] = sort }
        }

        /** Pinned creators — Home tabs (PLAN §7 pin-to-tabs); entries "username".
         *  Shared by both apps; each app renders its own tab/pill chrome. */
        val pinnedCreators: Flow<Set<String>> =
            dataStore.data.map { it[Keys.PINNED_CREATORS] ?: emptySet() }

        suspend fun togglePinnedCreator(username: String) {
            dataStore.edit {
                val cur = it[Keys.PINNED_CREATORS] ?: emptySet()
                val u = username.lowercase().trim()
                it[Keys.PINNED_CREATORS] = if (u in cur) cur - u else cur + u
            }
        }

        /** Optional PIN app lock (PLAN §6 Phase 6). Null = no lock set.
         *  Salted SHA-256 — the plaintext PIN never hits storage. */
        val pinHash: Flow<String?> = dataStore.data.map { it[Keys.PIN_HASH] }

        suspend fun setPin(pin: String) {
            require(pin.length >= 4 && pin.all { it.isDigit() }) { "PIN must be 4+ digits" }
            dataStore.edit { it[Keys.PIN_HASH] = pin.sha256() }
        }

        suspend fun clearPin() {
            dataStore.edit { it.remove(Keys.PIN_HASH) }
        }

        suspend fun verifyPin(pin: String): Boolean = pinHash.first() == pin.sha256()

        suspend fun setDynamicColor(enabled: Boolean) {
            dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
        }
    }

private fun String.sha256(): String =
    java.security.MessageDigest
        .getInstance("SHA-256")
        .digest((this + PIN_SALT).toByteArray())
        .joinToString("") { "%02x".format(it) }

private const val PIN_SALT = "giffy_viewer_pin"
