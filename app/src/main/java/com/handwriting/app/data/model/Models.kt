package com.handwriting.app.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Represents a single point within a stroke.
 * Captures spatial coordinates, pressure, and temporal data.
 */
@Parcelize
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,  // Default to 1.0 for finger input
    val timestamp: Long          // Epoch milliseconds
) : Parcelable

/**
 * Represents a single stroke - a continuous touch sequence from down to up.
 * A character or word is composed of one or more strokes.
 */
@Parcelize
data class Stroke(
    @PrimaryKey(autoGenerate = true)
    val strokeId: Long = 0L,
    val points: List<StrokePoint>,
    val characterLabel: String? = null  // Used in training mode
) : Parcelable

/**
 * Complete handwriting sample containing multiple strokes.
 * This is the primary unit for recognition and storage.
 */
@Parcelize
@Entity(tableName = "handwriting_samples")
data class HandwritingSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val strokes: List<Stroke>,
    val label: String,                    // User-provided text label
    val createdAt: Long = System.currentTimeMillis(),
    val isUserTrained: Boolean = false,   // True if added via training mode
    val category: String = "default"      // For grouping (e.g., "letters", "numbers", "custom")
) : Parcelable

/**
 * Normalized stroke data after preprocessing.
 * Used internally by the recognition pipeline.
 */
@Parcelize
data class NormalizedStroke(
    val points: List<StrokePoint>,
    val boundingBox: BoundingBox,
    val aspectRatio: Float,
    val totalLength: Float,
    val duration: Long
) : Parcelable

/**
 * Bounding box for a stroke or stroke group.
 */
@Parcelize
data class BoundingBox(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) : Parcelable {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

/**
 * Feature vector extracted from normalized strokes.
 * Used by the recognition engine for comparison.
 */
@Parcelize
data class FeatureVector(
    val directionHistogram: FloatArray,    // 8-direction chain code histogram
    val curvatureFeatures: FloatArray,     // Curvature at key points
    val aspectRatio: Float,                // Width/height ratio
    val strokeCount: Int,                  // Number of strokes
    val totalLength: Float,                // Total path length
    val temporalFeatures: FloatArray       // Speed and acceleration features
) : Parcelable

/**
 * Recognition candidate with confidence score.
 */
@Parcelize
data class RecognitionCandidate(
    val character: String,
    val confidence: Float,                 // 0.0 to 1.0
    val distance: Float = 0f,              // Distance metric (lower is better)
    val source: CandidateSource            // BASE_MODEL or USER_TRAINED
) : Parcelable

enum class CandidateSource {
    BASE_MODEL,
    USER_TRAINED
}
