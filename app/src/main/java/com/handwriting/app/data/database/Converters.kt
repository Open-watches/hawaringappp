package com.handwriting.app.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.handwriting.app.data.model.Stroke

/**
 * Type converters for Room database to handle complex types like List<Stroke>.
 * Uses Gson for JSON serialization/deserialization.
 */
class Converters {

    private val gson = Gson()

    /**
     * Converts a List<Stroke> into a JSON string for storage in the database.
     */
    @TypeConverter
    fun fromStrokeList(strokes: List<Stroke>?): String {
        return if (strokes == null) {
            "[]"
        } else {
            gson.toJson(strokes)
        }
    }

    /**
     * Converts a JSON string back into a List<Stroke>.
     */
    @TypeConverter
    fun toStrokeList(data: String?): List<Stroke> {
        if (data == null || data.isEmpty()) {
            return emptyList()
        }
        val listType = object : TypeToken<List<Stroke>>() {}.type
        return gson.fromJson(data, listType)
    }
}
