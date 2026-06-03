package com.handwriting.app.domain.training

import com.handwriting.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the progressive training workflow for handwriting recognition.
 * Handles character set selection, prompt generation, and progress tracking.
 */
class TrainingWorkflowManager {

    // Current training state
    private val _currentState = MutableStateFlow(TrainingState())
    val currentState: StateFlow<TrainingState> = _currentState.asStateFlow()

    // Available character sets
    private val characterSets = mapOf(
        CharacterSet.LATIN_UPPERCASE to ('A'..'Z').map { it.toString() },
        CharacterSet.LATIN_LOWERCASE to ('a'..'z').map { it.toString() },
        CharacterSet.NUMBERS to ('0'..'9').map { it.toString() },
        CharacterSet.BURMESE to listOf(
            "က", "ခ", "ဂ", "ဃ", "င",
            "စ", "ဆ", "ဇ", "ဈ", "ည",
            "တ", "ထ", "ဒ", "ဓ", "န",
            "ပ", "ဖ", "ဗ", "ဘ", "မ",
            "ယ", "ရ", "လ", "ဝ", "သ",
            "ဟ", "အ"
        ),
        CharacterSet.CUSTOM to emptyList()
    )

    /**
     * Start training with a specific character set.
     */
    fun startTraining(characterSet: CharacterSet, targetSamplesPerChar: Int = 5) {
        val characters = characterSets[characterSet] ?: emptyList()
        
        _currentState.value = TrainingState(
            characterSet = characterSet,
            totalCharacters = characters.size,
            targetSamplesPerCharacter = targetSamplesPerChar,
            currentDifficulty = 1,
            progress = TrainingProgress(
                characterSet = characterSet,
                targetSamplesPerCharacter = targetSamplesPerChar
            ),
            currentPrompt = generateNextPrompt(characters, emptySet(), 1)
        )
    }

    /**
     * Record a completed training sample.
     */
    fun recordSample(character: String, strokes: List<Stroke>) {
        val state = _currentState.value
        val progress = state.progress
        
        val updatedSamplesCount = progress.totalSamplesPerCharacter
            .toMutableMap()
            .apply {
                this[character] = (this[character] ?: 0) + 1
            }
        
        val completedChars = mutableSetOf<String>()
        updatedSamplesCount.forEach { (char, count) ->
            if (count >= state.targetSamplesPerCharacter) {
                completedChars.add(char)
            }
        }
        
        val updatedProgress = progress.copy(
            totalSamplesPerCharacter = updatedSamplesCount,
            completedCharacters = completedChars,
            lastTrainedAt = System.currentTimeMillis()
        )
        
        // Check if we should advance difficulty
        val allCurrentLevelComplete = completedChars.size >= state.totalCharacters
        
        val nextPrompt = if (allCurrentLevelComplete) {
            // Advance to next difficulty level
            val nextDifficulty = state.currentDifficulty + 1
            _currentState.value = state.copy(
                progress = updatedProgress,
                currentDifficulty = nextDifficulty
            )
            generateNextPrompt(
                characterSets[state.characterSet] ?: emptyList(),
                completedChars,
                nextDifficulty
            )
        } else {
            generateNextPrompt(
                characterSets[state.characterSet] ?: emptyList(),
                completedChars,
                state.currentDifficulty
            )
        }
        
        _currentState.value = state.copy(
            progress = updatedProgress,
            currentPrompt = nextPrompt
        )
    }

    /**
     * Skip the current character and move to the next.
     */
    fun skipCharacter() {
        val state = _currentState.value
        val characters = characterSets[state.characterSet] ?: emptyList()
        
        val nextPrompt = generateNextPrompt(
            characters,
            state.progress.completedCharacters,
            state.currentDifficulty
        )
        
        _currentState.value = state.copy(currentPrompt = nextPrompt)
    }

    /**
     * Set custom characters for training.
     */
    fun setCustomCharacters(characters: List<String>) {
        characterSets.toMutableMap()[CharacterSet.CUSTOM] = characters
    }

    /**
     * Generate the next training prompt based on difficulty level.
     * Difficulty 1: Single characters
     * Difficulty 2: Bigrams (two-character combinations)
     * Difficulty 3: Short words or trigrams
     */
    private fun generateNextPrompt(
        characters: List<String>,
        completed: Set<String>,
        difficulty: Int
    ): TrainingPrompt? {
        if (characters.isEmpty()) return null
        
        val remaining = characters.filter { it !in completed }
        if (remaining.isEmpty() && difficulty == 1) {
            return null // All characters completed at basic level
        }
        
        return when (difficulty) {
            1 -> {
                // Single character
                val char = remaining.firstOrNull() ?: characters.random()
                TrainingPrompt(
                    character = char,
                    characterSet = getCharacterSetForChar(char),
                    instruction = "Write the character: $char",
                    difficulty = 1
                )
            }
            2 -> {
                // Bigram - two character combination
                val first = remaining.firstOrNull() ?: characters.random()
                val second = remaining.getOrNull(1) ?: characters.random()
                val bigram = first + second
                TrainingPrompt(
                    character = bigram,
                    characterSet = getCharacterSetForChar(first),
                    instruction = "Write: $bigram",
                    difficulty = 2
                )
            }
            else -> {
                // Trigram or short word
                val chars = if (remaining.size >= 3) {
                    remaining.take(3)
                } else {
                    characters.take(3)
                }
                val trigram = chars.joinToString("")
                TrainingPrompt(
                    character = trigram,
                    characterSet = getCharacterSetForChar(chars.firstOrNull() ?: ""),
                    instruction = "Write: $trigram",
                    difficulty = 3
                )
            }
        }
    }

    private fun getCharacterSetForChar(char: String): CharacterSet {
        return when {
            char.isNotEmpty() && char[0].isUpperCase() -> CharacterSet.LATIN_UPPERCASE
            char.isNotEmpty() && char[0].isLowerCase() -> CharacterSet.LATIN_LOWERCASE
            char.isNotEmpty() && char[0].isDigit() -> CharacterSet.NUMBERS
            char.isNotEmpty() -> CharacterSet.BURMESE
            else -> CharacterSet.CUSTOM
        }
    }

    /**
     * Reset training progress.
     */
    fun resetTraining() {
        _currentState.value = TrainingState()
    }

    /**
     * Get statistics about training progress.
     */
    fun getTrainingStats(): TrainingStats {
        val state = _currentState.value
        return TrainingStats(
            characterSet = state.characterSet,
            totalCharacters = state.totalCharacters,
            completedCharacters = state.progress.completedCharacters.size,
            totalSamplesCollected = state.progress.totalSamplesPerCharacter.values.sum(),
            completionPercentage = state.progress.getCompletionPercentage(),
            currentDifficulty = state.currentDifficulty
        )
    }
}

/**
 * Current training session state.
 */
data class TrainingState(
    val characterSet: CharacterSet = CharacterSet.LATIN_UPPERCASE,
    val totalCharacters: Int = 0,
    val targetSamplesPerCharacter: Int = 5,
    val currentDifficulty: Int = 1,
    val progress: TrainingProgress = TrainingProgress(characterSet = CharacterSet.LATIN_UPPERCASE),
    val currentPrompt: TrainingPrompt? = null
)

/**
 * Training statistics.
 */
data class TrainingStats(
    val characterSet: CharacterSet,
    val totalCharacters: Int,
    val completedCharacters: Int,
    val totalSamplesCollected: Int,
    val completionPercentage: Float,
    val currentDifficulty: Int
)
