package com.rjbiermann.giffyviewer.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/** Map fields (nicheNames) stored as a JSON object string. */
class MapStringStringConverter {
    private val json = Json

    @TypeConverter
    fun mapToJson(value: Map<String, String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToMap(value: String): Map<String, String> = if (value.isBlank()) emptyMap() else json.decodeFromString(value)
}

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
