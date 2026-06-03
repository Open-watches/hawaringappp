package com.handwriting.app.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Represents a notebook containing multiple pages.
 * This is the primary unit for organizing handwritten content.
 */
@Entity(tableName = "notebooks")
data class Notebook(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String = "Untitled Notebook",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pageOrder: List<Long> = emptyList() // Ordered list of page IDs
)

/**
 * Page state for undo/redo operations.
 */
@Parcelize
data class PageState(
    val strokes: List<Stroke>,
    val backgroundType: PageBackground,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

/**
 * Undo/redo action types.
 */
enum class UndoActionType {
    STROKE_ADDED,
    STROKE_REMOVED,
    PAGE_CLEARED,
    PAGE_CHANGED
}

/**
 * Undo/redo record for per-page history.
 */
@Parcelize
data class UndoRecord(
    val actionType: UndoActionType,
    val previousState: PageState,
    val currentState: PageState,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

/**
 * Training character set definitions.
 */
enum class CharacterSet {
    LATIN_UPPERCASE,
    LATIN_LOWERCASE,
    NUMBERS,
    BURMESE,
    CUSTOM
}

/**
 * Training prompt for guided character practice.
 */
@Parcelize
data class TrainingPrompt(
    val character: String,
    val characterSet: CharacterSet,
    val instruction: String,
    val difficulty: Int = 1, // 1=single letter, 2=bigram, 3=word
    val exampleStrokes: List<Stroke>? = null
) : Parcelable

/**
 * Training progress tracking.
 */
@Parcelize
data class TrainingProgress(
    val characterSet: CharacterSet,
    val completedCharacters: Set<String> = emptySet(),
    val totalSamplesPerCharacter: Map<String, Int> = emptyMap(),
    val targetSamplesPerCharacter: Int = 5,
    val currentDifficulty: Int = 1,
    val lastTrainedAt: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun getProgressForCharacter(char: String): Int {
        return totalSamplesPerCharacter[char] ?: 0
    }
    
    fun isCharacterComplete(char: String): Boolean {
        return getProgressForCharacter(char) >= targetSamplesPerCharacter
    }
    
    fun getCompletionPercentage(): Float {
        if (completedCharacters.isEmpty()) return 0f
        val totalExpected = completedCharacters.size * targetSamplesPerCharacter
        val totalCollected = totalSamplesPerCharacter.values.sum()
        return (totalCollected.toFloat() / totalExpected).coerceIn(0f, 1f) * 100f
    }
}

/**
 * Notebook export/import format.
 */
@Parcelize
data class NotebookExport(
    val version: String = "1.0",
    val notebook: Notebook,
    val pages: List<Page>,
    val exportedAt: Long = System.currentTimeMillis()
) : Parcelable
