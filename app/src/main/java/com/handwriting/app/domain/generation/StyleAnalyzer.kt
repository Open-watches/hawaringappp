package com.handwriting.app.domain.generation

import com.handwriting.app.data.model.Stroke
import com.handwriting.app.data.model.StrokePoint
import com.handwriting.app.domain.generation.style.HandwritingStyle
import com.handwriting.app.domain.generation.style.CharacterTemplate
import com.handwriting.app.domain.generation.style.StrokeData
import com.handwriting.app.domain.generation.style.PointData
import com.handwriting.app.domain.generation.style.BoundingBoxData
import com.handwriting.app.util.StrokeNormalizer

/**
 * Analyzes user handwriting samples to extract style parameters.
 * 
 * This component bridges the gap between raw stroke data and the 
 * generic HandwritingGenerator by extracting statistical properties
 * from user samples.
 * 
 * Extracted parameters:
 * - Average character size and aspect ratio
 * - Character spacing patterns
 * - Baseline position and variation
 * - Natural size/rotation variations
 * - Slant angle
 */
class StyleAnalyzer {

    /**
     * Analyze handwriting samples to extract style parameters.
     * @param samples List of labeled handwriting samples
     * @return HandwritingStyle with extracted parameters
     */
    fun analyzeStyle(samples: List<com.handwriting.app.data.model.HandwritingSample>): HandwritingStyle {
        if (samples.isEmpty()) {
            return HandwritingStyle.default()
        }

        val allStrokes = samples.flatMap { it.strokes }
        val allPoints = allStrokes.flatMap { it.points }
        
        if (allPoints.isEmpty()) {
            return HandwritingStyle.default()
        }

        // Calculate bounding boxes for each sample
        val sampleBounds = samples.mapNotNull { sample ->
            val points = sample.strokes.flatMap { it.points }
            if (points.isNotEmpty()) {
                StrokeNormalizer.calculateBoundingBox(points)
            } else null
        }

        // Average character dimensions
        val avgHeight = sampleBounds.map { it.height }.average().toFloat()
        val avgWidth = sampleBounds.map { it.width }.average().toFloat()

        // Calculate spacing between characters (approximate)
        val avgSpacing = calculateAverageSpacing(samples)

        // Baseline estimation (bottom of most characters)
        val baselineY = sampleBounds.map { it.maxY }.average().toFloat()

        // Size variation (standard deviation of heights)
        val sizeVariance = calculateStandardDeviation(sampleBounds.map { it.height }) / avgHeight.coerceAtLeast(1f)

        // Rotation/slant estimation
        val slantAngle = estimateSlantAngle(allStrokes)

        // Baseline variation
        val baselineVariance = calculateStandardDeviation(sampleBounds.map { it.maxY })

        return HandwritingStyle.fromSamples(
            averageHeight = avgHeight,
            averageWidth = avgWidth,
            averageSpacing = avgSpacing,
            baselineY = baselineY,
            sizeVariance = sizeVariance.coerceIn(0f, 0.5f),
            rotationVariance = 0.05f, // Default small rotation variance
            baselineVariance = baselineVariance.coerceIn(0f, 10f),
            slant = slantAngle
        )
    }

    /**
     * Convert user samples into character templates for generation.
     * @param samples Labeled handwriting samples
     * @return Map of characters to their template variants
     */
    fun extractCharacterTemplates(
        samples: List<com.handwriting.app.data.model.HandwritingSample>
    ): Map<Char, List<CharacterTemplate>> {
        val templates = mutableMapOf<Char, MutableList<CharacterTemplate>>()

        for (sample in samples) {
            // Get first character of label as the character this represents
            if (sample.label.isEmpty()) continue
            
            val character = sample.label.first()
            
            if (sample.strokes.isEmpty()) continue

            // Normalize strokes to unit coordinates
            val normalizedStrokes = normalizeStrokes(sample.strokes)
            
            // Calculate bounding box
            val allPoints = sample.strokes.flatMap { it.points }
            val bounds = if (allPoints.isNotEmpty()) {
                StrokeNormalizer.calculateBoundingBox(allPoints)
            } else continue

            // Convert to template format
            val template = CharacterTemplate(
                character = character,
                strokes = normalizedStrokes.map { stroke ->
                    StrokeData(
                        points = stroke.points.map { point ->
                            PointData(
                                x = point.x,
                                y = point.y,
                                pressure = point.pressure,
                                relativeTime = 0f // Could calculate from timestamps
                            )
                        }
                    )
                },
                boundingBox = BoundingBoxData(
                    minX = bounds.minX,
                    minY = bounds.minY,
                    maxX = bounds.maxX,
                    maxY = bounds.maxY
                ),
                frequency = 1
            )

            // Add to templates map
            templates.getOrPut(character) { mutableListOf() }.add(template)
        }

        // Merge variants for same character
        return templates.mapValues { (_, variantList) ->
            // Group similar variants and count frequency
            // For now, just return all variants
            variantList
        }
    }

    /**
     * Normalize strokes to unit coordinate system (0-1 range).
     */
    private fun normalizeStrokes(strokes: List<Stroke>): List<Stroke> {
        val allPoints = strokes.flatMap { it.points }
        val bounds = StrokeNormalizer.calculateBoundingBox(allPoints)
        
        val size = kotlin.math.max(bounds.width, bounds.height).coerceAtLeast(1f)
        val offsetX = bounds.minX
        val offsetY = bounds.minY

        return strokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { point ->
                    point.copy(
                        x = (point.x - offsetX) / size,
                        y = (point.y - offsetY) / size
                    )
                }
            )
        }
    }

    /**
     * Estimate average spacing between characters.
     * Uses word-level samples when available.
     */
    private fun calculateAverageSpacing(
        samples: List<com.handwriting.app.data.model.HandwritingSample>
    ): Float {
        // For multi-character labels, estimate spacing
        val wordSamples = samples.filter { it.label.length > 1 && it.strokes.size > 1 }
        
        if (wordSamples.isEmpty()) {
            return 5f // Default spacing
        }

        var totalSpacing = 0f
        var count = 0

        for (sample in wordSamples) {
            val strokes = sample.strokes
            if (strokes.size < 2) continue

            // Estimate spacing between consecutive strokes
            for (i in 0 until strokes.size - 1) {
                val currentEnd = strokes[i].points.lastOrNull() ?: continue
                val nextStart = strokes[i + 1].points.firstOrNull() ?: continue
                
                val spacing = nextStart.x - currentEnd.x
                if (spacing > 0) {
                    totalSpacing += spacing
                    count++
                }
            }
        }

        return if (count > 0) totalSpacing / count else 5f
    }

    /**
     * Estimate the slant angle of handwriting.
     * Positive angle indicates rightward slant.
     */
    private fun estimateSlantAngle(strokes: List<Stroke>): Float {
        if (strokes.isEmpty()) return 0f

        var totalAngle = 0f
        var count = 0

        for (stroke in strokes) {
            if (stroke.points.size < 2) continue

            // Calculate angle from start to end of stroke
            val start = stroke.points.first()
            val end = stroke.points.last()
            
            val dx = end.x - start.x
            val dy = end.y - start.y

            // Vertical strokes give best slant indication
            if (kotlin.math.abs(dy) > kotlin.math.abs(dx) && dy != 0f) {
                val angle = kotlin.math.atan2(dx.toDouble(), kotlin.math.abs(dy)).toFloat()
                totalAngle += angle
                count++
            }
        }

        return if (count > 0) totalAngle / count else 0f
    }

    /**
     * Calculate standard deviation of a list of values.
     */
    private fun calculateStandardDeviation(values: List<Float>): Float {
        if (values.size < 2) return 0f
        
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }

    /**
     * Build complete generation data from samples.
     * Convenience method that returns both style and templates.
     */
    fun buildGenerationData(
        samples: List<com.handwriting.app.data.model.HandwritingSample>
    ): GenerationData {
        val style = analyzeStyle(samples)
        val templates = extractCharacterTemplates(samples)
        
        return GenerationData(style, templates)
    }

    /**
     * Container for generation data.
     */
    data class GenerationData(
        val style: HandwritingStyle,
        val templates: Map<Char, List<CharacterTemplate>>
    )
}
