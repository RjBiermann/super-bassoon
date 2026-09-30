package com.rjbiermann.giffyviewer.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/**
 * List fields stored as JSON strings. upstream tags contain no commas, but JSON is
 * future-proof and needs no escaping assumptions.
 */
class ListConverters {
    private val json = Json

    @TypeConverter
    fun stringsToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToStrings(value: String): List<String> = if (value.isBlank()) emptyList() else json.decodeFromString(value)
}
