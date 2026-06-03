package com.handwriting.app.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.handwriting.app.data.model.Stroke
import com.handwriting.app.data.model.PageBackground
import com.handwriting.app.data.model.CharacterSet

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

    /**
     * Converts a List<Long> (page order) into a JSON string.
     */
    @TypeConverter
    fun fromLongList(longs: List<Long>?): String {
        return if (longs == null) {
            "[]"
        } else {
            gson.toJson(longs)
        }
    }

    /**
     * Converts a JSON string back into a List<Long>.
     */
    @TypeConverter
    fun toLongList(data: String?): List<Long> {
        if (data == null || data.isEmpty()) {
            return emptyList()
        }
        val listType = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(data, listType)
    }

    /**
     * Converts PageBackground enum to string for storage.
     */
    @TypeConverter
    fun fromPageBackground(background: PageBackground): String {
        return background.name
    }

    /**
     * Converts string back to PageBackground enum.
     */
    @TypeConverter
    fun toPageBackground(value: String): PageBackground {
        return try {
            PageBackground.valueOf(value)
        } catch (e: IllegalArgumentException) {
            PageBackground.BLANK
        }
    }

    /**
     * Converts CharacterSet enum to string for storage.
     */
    @TypeConverter
    fun fromCharacterSet(characterSet: CharacterSet): String {
        return characterSet.name
    }

    /**
     * Converts string back to CharacterSet enum.
     */
    @TypeConverter
    fun toCharacterSet(value: String): CharacterSet {
        return try {
            CharacterSet.valueOf(value)
        } catch (e: IllegalArgumentException) {
            CharacterSet.LATIN_UPPERCASE
        }
    }
}
