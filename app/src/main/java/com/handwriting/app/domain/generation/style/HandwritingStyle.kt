package com.handwriting.app.domain.generation.style

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.handwriting.app.domain.generation.Alignment

/**
 * Represents the learned handwriting style from user samples.
 * Contains statistical parameters extracted from training data.
 */
@Parcelize
data class HandwritingStyle(
    /** Average character height in pixels */
    val averageCharacterHeight: Float,
    
    /** Average character width in pixels */
    val averageCharacterWidth: Float,
    
    /** Average character size (used for scaling templates) */
    val averageCharacterSize: Float,
    
    /** Average spacing between characters */
    val averageCharacterSpacing: Float,
    
    /** Vertical offset of baseline from top of writing area */
    val baselineOffset: Float,
    
    /** Left margin in pixels */
    val leftMargin: Float,
    
    /** Right margin in pixels */
    val rightMargin: Float,
    
    /** Natural size variation (0.0 = uniform, 1.0 = high variation) */
    val sizeVariationFactor: Float,
    
    /** Natural rotation variation in radians */
    val rotationVariation: Float,
    
    /** Natural baseline position variation in pixels */
    val baselineVariation: Float,
    
    /** Pressure sensitivity factor */
    val pressureVariationFactor: Float,
    
    /** Width of space character */
    val spaceWidth: Float,
    
    /** Text alignment preference */
    val alignment: Alignment,
    
    /** Average slant angle in radians (positive = right slant) */
    val slantAngle: Float,
    
    /** Character aspect ratio (width/height) */
    val aspectRatio: Float
) : Parcelable {

    companion object {
        /**
         * Default neutral style before any user training.
         */
        fun default(): HandwritingStyle {
            return HandwritingStyle(
                averageCharacterHeight = 40f,
                averageCharacterWidth = 30f,
                averageCharacterSize = 40f,
                averageCharacterSpacing = 5f,
                baselineOffset = 60f,
                leftMargin = 20f,
                rightMargin = 20f,
                sizeVariationFactor = 0.1f,
                rotationVariation = 0.05f,
                baselineVariation = 3f,
                pressureVariationFactor = 1.0f,
                spaceWidth = 15f,
                alignment = Alignment.LEFT,
                slantAngle = 0f,
                aspectRatio = 0.75f
            )
        }

        /**
         * Create a style from analyzed sample statistics.
         */
        fun fromSamples(
            averageHeight: Float,
            averageWidth: Float,
            averageSpacing: Float,
            baselineY: Float,
            sizeVariance: Float,
            rotationVariance: Float,
            baselineVariance: Float,
            slant: Float
        ): HandwritingStyle {
            val avgSize = kotlin.math.max(averageHeight, averageWidth)
            return HandwritingStyle(
                averageCharacterHeight = averageHeight.coerceAtLeast(10f),
                averageCharacterWidth = averageWidth.coerceAtLeast(8f),
                averageCharacterSize = avgSize.coerceAtLeast(20f),
                averageCharacterSpacing = averageSpacing.coerceAtLeast(2f),
                baselineOffset = baselineY.coerceAtLeast(30f),
                leftMargin = 20f,
                rightMargin = 20f,
                sizeVariationFactor = sizeVariance.coerceIn(0f, 0.5f),
                rotationVariation = rotationVariance.coerceIn(0f, 0.3f),
                baselineVariation = baselineVariance.coerceIn(0f, 10f),
                pressureVariationFactor = 1.0f,
                spaceWidth = averageWidth * 0.5f,
                alignment = Alignment.LEFT,
                slantAngle = slant.coerceIn(-0.3f, 0.3f),
                aspectRatio = (averageWidth / averageHeight.coerceAtLeast(1f)).coerceIn(0.5f, 1.5f)
            )
        }
    }
}

/**
 * Template for a single character variant.
 * Users may write the same character in multiple ways.
 */
@Parcelize
data class CharacterTemplate(
    /** The character this template represents */
    val character: Char,
    
    /** Strokes that form this character */
    val strokes: List<StrokeData>,
    
    /** Bounding box of the original sample */
    val boundingBox: BoundingBoxData,
    
    /** Frequency of this variant (optional, for weighted selection) */
    val frequency: Int = 1
) : Parcelable

/**
 * Stroke data for template storage.
 * Normalized to unit coordinates for scalable rendering.
 */
@Parcelize
data class StrokeData(
    val points: List<PointData>
) : Parcelable

/**
 * Point data for stroke templates.
 * Uses normalized coordinates (0-1 range).
 */
@Parcelize
data class PointData(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
    val relativeTime: Float = 0f  // Normalized time within stroke (0-1)
) : Parcelable

/**
 * Bounding box for character templates.
 */
@Parcelize
data class BoundingBoxData(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) : Parcelable {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}
