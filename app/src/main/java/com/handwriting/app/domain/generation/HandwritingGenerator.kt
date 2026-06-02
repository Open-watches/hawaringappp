package com.handwriting.app.domain.generation

import com.handwriting.app.data.model.Stroke
import com.handwriting.app.data.model.StrokePoint
import com.handwriting.app.domain.generation.style.HandwritingStyle
import com.handwriting.app.domain.generation.style.CharacterTemplate
import com.handwriting.app.domain.generation.style.StrokeData
import com.handwriting.app.domain.generation.style.PointData
import com.handwriting.app.domain.generation.style.BoundingBoxData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Generic handwriting synthesis engine.
 * 
 * Generates handwritten strokes from typed text using learned user style parameters.
 * The engine itself remains generic - it does not contain hardcoded character shapes.
 * Instead, it learns from user-provided samples and applies style parameters.
 * 
 * Learning parameters:
 * - Character shape (from sample strokes)
 * - Character spacing (inter-character distance)
 * - Alignment (left, center, right, justified)
 * - Baseline position (vertical positioning)
 * - Size variation (natural handwriting variance)
 */
class HandwritingGenerator {

    /**
     * Current handwriting style learned from user samples.
     * Defaults to neutral parameters until trained.
     */
    private var currentStyle: HandwritingStyle = HandwritingStyle.default()

    /**
     * Character templates learned from user samples.
     * Maps characters to their stroke representations.
     */
    private val characterTemplates: MutableMap<Char, List<CharacterTemplate>> = mutableMapOf()

    /**
     * Update the handwriting style based on analyzed samples.
     * Call this after feature extraction from user training data.
     */
    fun updateStyle(style: HandwritingStyle) {
        currentStyle = style
    }

    /**
     * Add or update character templates from user samples.
     * @param character The character this template represents
     * @param templates One or more variant templates for natural variation
     */
    fun addCharacterTemplates(character: Char, templates: List<CharacterTemplate>) {
        if (templates.isNotEmpty()) {
            characterTemplates[character] = templates
        }
    }

    /**
     * Clear all learned character data.
     * Use when resetting user training.
     */
    fun clearLearnedData() {
        characterTemplates.clear()
        currentStyle = HandwritingStyle.default()
    }

    /**
     * Generate handwriting strokes from typed text.
     * 
     * @param text The input text to convert to handwriting
     * @param canvasWidth Available canvas width for layout calculations
     * @param canvasHeight Available canvas height for baseline positioning
     * @return List of strokes representing the handwritten text
     */
    suspend fun generateHandwriting(
        text: String,
        canvasWidth: Float,
        canvasHeight: Float
    ): List<Stroke> = withContext(Dispatchers.Default) {
        if (text.isEmpty()) return@withContext emptyList()

        val lines = text.split("\n")
        val allStrokes = mutableListOf<Stroke>()
        
        // Calculate line height based on learned style
        val lineHeight = currentStyle.averageCharacterHeight * 1.5f
        val startY = currentStyle.baselineOffset.coerceIn(lineHeight, canvasHeight - lineHeight)

        for ((lineIndex, line) in lines.withIndex()) {
            val lineStrokes = generateLine(
                text = line.trimEnd(),
                canvasWidth = canvasWidth,
                baselineY = startY + (lineIndex * lineHeight),
                lineWidth = canvasWidth
            )
            allStrokes.addAll(lineStrokes)
        }

        allStrokes
    }

    /**
     * Generate a single line of handwriting.
     */
    private fun generateLine(
        text: String,
        canvasWidth: Float,
        baselineY: Float,
        lineWidth: Float
    ): List<Stroke> {
        if (text.isEmpty()) return emptyList()

        // Calculate total width needed for this line
        val charWidths = text.map { getCharacterWidth(it) }
        val totalTextWidth = charWidths.sum() + 
            (text.length - 1) * currentStyle.averageCharacterSpacing

        // Determine starting X based on alignment
        val startX = when (currentStyle.alignment) {
            Alignment.CENTER -> (lineWidth - totalTextWidth) / 2f
            Alignment.RIGHT -> lineWidth - totalTextWidth
            Alignment.LEFT, Alignment.JUSTIFIED -> currentStyle.leftMargin
        }

        // For justified text, adjust spacing
        val effectiveSpacing = if (currentStyle.alignment == Alignment.JUSTIFIED && text.length > 1) {
            (lineWidth - currentStyle.leftMargin - currentStyle.rightMargin - totalTextWidth) / 
                (text.length - 1).coerceAtLeast(1)
        } else {
            currentStyle.averageCharacterSpacing
        }

        val strokes = mutableListOf<Stroke>()
        var currentX = startX

        for (char in text) {
            // Skip unsupported characters
            if (!characterTemplates.containsKey(char) && char != ' ') {
                // Try to find similar character or skip
                val fallbackChar = findFallbackCharacter(char)
                if (fallbackChar != null) {
                    strokes.addAll(
                        generateCharacter(
                            character = fallbackChar,
                            startX = currentX,
                            baselineY = baselineY
                        )
                    )
                }
                currentX += getCharacterWidth(char) + effectiveSpacing
                continue
            }

            if (char == ' ') {
                currentX += currentStyle.spaceWidth
                continue
            }

            // Generate strokes for this character
            val charStrokes = generateCharacter(
                character = char,
                startX = currentX,
                baselineY = baselineY
            )
            strokes.addAll(charStrokes)

            // Move to next character position
            currentX += getCharacterWidth(char) + effectiveSpacing
        }

        return strokes
    }

    /**
     * Generate strokes for a single character.
     * Applies natural variation to avoid robotic repetition.
     */
    private fun generateCharacter(
        character: Char,
        startX: Float,
        baselineY: Float
    ): List<Stroke> {
        val templates = characterTemplates[character]
        
        if (templates.isNullOrEmpty()) {
            return emptyList()
        }

        // Select template with variation
        val template = selectTemplateWithVariation(templates)

        // Apply transformations based on learned style
        val scale = calculateCharacterScale(template)
        val rotation = calculateRotationVariation()
        val baselineOffset = calculateBaselineVariation()

        return template.strokes.map { stroke ->
            transformStroke(
                stroke = stroke,
                offsetX = startX,
                offsetY = baselineY + baselineOffset,
                scale = scale,
                rotation = rotation
            )
        }
    }

    /**
     * Select a template variant with natural variation.
     * Users may have multiple ways of writing the same character.
     */
    private fun selectTemplateWithVariation(templates: List<CharacterTemplate>): CharacterTemplate {
        if (templates.size == 1) return templates.first()

        // Weighted random selection based on frequency if available
        // For now, use simple random with slight preference for first (most common)
        val weights = templates.mapIndexed { index, _ -> 
            1.0f / (index + 1) // Decreasing weight for less common variants
        }
        
        val totalWeight = weights.sum()
        val randomValue = Random.nextFloat() * totalWeight
        
        var cumulative = 0f
        for ((i, weight) in weights.withIndex()) {
            cumulative += weight
            if (randomValue <= cumulative) {
                return templates[i]
            }
        }
        
        return templates.last()
    }

    /**
     * Calculate scale factor for character with size variation.
     */
    private fun calculateCharacterScale(template: CharacterTemplate): Float {
        val baseScale = currentStyle.averageCharacterSize / 
            (template.boundingBox.height.coerceAtLeast(1f))
        
        // Apply natural size variation
        val variation = (Random.nextFloat() - 0.5f) * 2f * currentStyle.sizeVariationFactor
        return baseScale * (1f + variation)
    }

    /**
     * Calculate slight rotation variation for natural look.
     */
    private fun calculateRotationVariation(): Float {
        return (Random.nextFloat() - 0.5f) * 2f * currentStyle.rotationVariation
    }

    /**
     * Calculate baseline position variation.
     */
    private fun calculateBaselineVariation(): Float {
        return (Random.nextFloat() - 0.5f) * 2f * currentStyle.baselineVariation
    }

    /**
     * Get the width a character should occupy.
     */
    private fun getCharacterWidth(character: Char): Float {
        val templates = characterTemplates[character]
        return if (templates != null && templates.isNotEmpty()) {
            templates.first().boundingBox.width * currentStyle.averageCharacterSize /
                templates.first().boundingBox.height.coerceAtLeast(1f)
        } else {
            currentStyle.averageCharacterHeight * 0.6f // Default proportional width
        }
    }

    /**
     * Transform a stroke with offset, scale, and rotation.
     */
    private fun transformStroke(
        stroke: Stroke,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        rotation: Float
    ): Stroke {
        val cosR = kotlin.math.cos(rotation.toDouble()).toFloat()
        val sinR = kotlin.math.sin(rotation.toDouble()).toFloat()

        val transformedPoints = stroke.points.map { point ->
            // Apply scale
            var x = point.x * scale
            var y = point.y * scale

            // Apply rotation around origin
            val rotatedX = x * cosR - y * sinR
            val rotatedY = x * sinR + y * cosR
            x = rotatedX
            y = rotatedY

            // Apply offset (flip Y for canvas coordinates)
            StrokePoint(
                x = x + offsetX,
                y = -y + offsetY, // Flip Y because canvas Y increases downward
                pressure = point.pressure * currentStyle.pressureVariationFactor,
                timestamp = point.timestamp
            )
        }

        return Stroke(
            strokeId = System.currentTimeMillis() + Random.nextLong(),
            points = transformedPoints,
            characterLabel = stroke.characterLabel
        )
    }

    /**
     * Find a fallback character for unsupported characters.
     */
    private fun findFallbackCharacter(char: Char): Char? {
        // Simple mappings for common substitutions
        return when (char.lowercaseChar()) {
            'à', 'á', 'â', 'ä', 'ã', 'å' -> 'a'
            'è', 'é', 'ê', 'ë' -> 'e'
            'ì', 'í', 'î', 'ï' -> 'i'
            'ò', 'ó', 'ô', 'ö', 'õ' -> 'o'
            'ù', 'ú', 'û', 'ü' -> 'u'
            'ñ' -> 'n'
            'ç' -> 'c'
            'ß' -> 's'
            else -> null
        }
    }

    /**
     * Check if the generator has learned data for specific characters.
     */
    fun hasCharacterData(char: Char): Boolean {
        return characterTemplates.containsKey(char)
    }

    /**
     * Get list of characters the generator can produce.
     */
    fun getAvailableCharacters(): Set<Char> {
        return characterTemplates.keys.toSet()
    }

    /**
     * Get current style statistics.
     */
    fun getGenerationStats(): GenerationStats {
        return GenerationStats(
            learnedCharacterCount = characterTemplates.size,
            totalTemplates = characterTemplates.values.sumOf { it.size },
            style = currentStyle
        )
    }

    /**
     * Generation statistics.
     */
    data class GenerationStats(
        val learnedCharacterCount: Int,
        val totalTemplates: Int,
        val style: HandwritingStyle
    )
}

/**
 * Text alignment options for generated handwriting.
 */
enum class Alignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFIED
}
