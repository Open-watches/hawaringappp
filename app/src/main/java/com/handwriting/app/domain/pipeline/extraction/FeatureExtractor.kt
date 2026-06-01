package com.handwriting.app.domain.pipeline.extraction

import com.handwriting.app.data.model.FeatureVector
import com.handwriting.app.data.model.Stroke
import com.handwriting.app.util.StrokeNormalizer

/**
 * Feature extraction engine for handwriting recognition.
 * Converts normalized strokes into mathematical feature vectors.
 */
class FeatureExtractor {

    companion object {
        const val DIRECTION_BINS = 8  // 8-direction chain code
        const val DEFAULT_POINT_COUNT = 64
    }

    /**
     * Extract features from a list of strokes.
     * @param strokes Normalized input strokes
     * @return Feature vector for recognition
     */
    fun extractFeatures(strokes: List<Stroke>): FeatureVector {
        if (strokes.isEmpty() || strokes.all { it.points.isEmpty() }) {
            return createEmptyFeatureVector()
        }

        val allPoints = strokes.flatMap { it.points }
        
        // Resample to fixed point count for consistency
        val resampledPoints = StrokeNormalizer.resampleToFixedPoints(
            allPoints, 
            DEFAULT_POINT_COUNT
        )

        // Extract directional features (8-direction chain code histogram)
        val directionHistogram = extractDirectionHistogram(resampledPoints)

        // Extract curvature features
        val curvatureFeatures = extractCurvatureFeatures(resampledPoints)

        // Calculate aspect ratio
        val boundingBox = StrokeNormalizer.calculateBoundingBox(allPoints)
        val aspectRatio = if (boundingBox.height > 0) {
            boundingBox.width / boundingBox.height
        } else {
            1f
        }

        // Extract temporal features (speed, acceleration)
        val temporalFeatures = extractTemporalFeatures(allPoints)

        // Calculate total path length
        val totalLength = calculateTotalLength(allPoints)

        return FeatureVector(
            directionHistogram = directionHistogram,
            curvatureFeatures = curvatureFeatures,
            aspectRatio = aspectRatio,
            strokeCount = strokes.size,
            totalLength = totalLength,
            temporalFeatures = temporalFeatures
        )
    }

    /**
     * Extract 8-direction chain code histogram.
     * Captures the dominant directions in the stroke.
     */
    private fun extractDirectionHistogram(points: List<com.handwriting.app.data.model.StrokePoint>): FloatArray {
        val histogram = FloatArray(DIRECTION_BINS) { 0f }
        
        if (points.size < 2) return histogram

        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            
            // Calculate angle in radians
            val angle = kotlin.math.atan2(dy, dx)
            
            // Convert to degrees and normalize to [0, 360)
            var degrees = Math.toDegrees(angle.toDouble()).toFloat()
            if (degrees < 0) degrees += 360f
            
            // Map to one of 8 bins (each bin covers 45 degrees)
            val binIndex = ((degrees + 22.5f) / 45f).toInt() % DIRECTION_BINS
            histogram[binIndex]++
        }

        // Normalize histogram
        val sum = histogram.sum()
        if (sum > 0) {
            for (i in histogram.indices) {
                histogram[i] /= sum
            }
        }

        return histogram
    }

    /**
     * Extract curvature features at key points along the stroke.
     * Uses angle changes between consecutive segments.
     */
    private fun extractCurvatureFeatures(points: List<com.handwriting.app.data.model.StrokePoint>): FloatArray {
        if (points.size < 3) return floatArrayOf(0f, 0f, 0f, 0f)

        val curvatures = mutableListOf<Float>()
        
        // Sample at regular intervals
        val sampleCount = 4
        val stepSize = points.size / sampleCount

        for (i in 0 until sampleCount) {
            val centerIdx = i * stepSize + stepSize / 2
            if (centerIdx <= 0 || centerIdx >= points.size - 1) {
                curvatures.add(0f)
                continue
            }

            val prevPoint = points[centerIdx - 1]
            val currPoint = points[centerIdx]
            val nextPoint = points[centerIdx + 1]

            // Calculate angle between segments
            val angle = calculateAngle(prevPoint, currPoint, nextPoint)
            curvatures.add(angle)
        }

        return curvatures.toFloatArray()
    }

    /**
     * Calculate angle between three points (prev-curr-next).
     * Returns angle in radians.
     */
    private fun calculateAngle(
        prev: com.handwriting.app.data.model.StrokePoint,
        curr: com.handwriting.app.data.model.StrokePoint,
        next: com.handwriting.app.data.model.StrokePoint
    ): Float {
        val dx1 = curr.x - prev.x
        val dy1 = curr.y - prev.y
        val dx2 = next.x - curr.x
        val dy2 = next.y - curr.y

        // Dot product formula for angle between vectors
        val dotProduct = dx1 * dx2 + dy1 * dy2
        val mag1 = kotlin.math.sqrt(dx1 * dx1 + dy1 * dy1)
        val mag2 = kotlin.math.sqrt(dx2 * dx2 + dy2 * dy2)

        return if (mag1 > 0 && mag2 > 0) {
            val cosAngle = (dotProduct / (mag1 * mag2)).coerceIn(-1f, 1f)
            kotlin.math.acos(cosAngle)
        } else {
            0f
        }
    }

    /**
     * Extract temporal features: average speed, speed variance, acceleration.
     */
    private fun extractTemporalFeatures(points: List<com.handwriting.app.data.model.StrokePoint>): FloatArray {
        if (points.size < 2) return floatArrayOf(0f, 0f, 0f)

        val speeds = mutableListOf<Float>()
        var totalDistance = 0f
        var totalTime = 0L

        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            val timeDiff = points[i].timestamp - points[i - 1].timestamp

            if (timeDiff > 0) {
                val speed = distance / timeDiff  // pixels per millisecond
                speeds.add(speed)
            }

            totalDistance += distance
            totalTime += timeDiff
        }

        val avgSpeed = if (speeds.isNotEmpty()) speeds.average().toFloat() else 0f
        
        val speedVariance = if (speeds.size > 1) {
            val mean = avgSpeed
            speeds.map { (it - mean) * (it - mean) }.average().toFloat()
        } else {
            0f
        }

        // Simple acceleration metric: change in speed over time
        val acceleration = if (speeds.size > 1) {
            (speeds.last() - speeds.first()) / speeds.size
        } else {
            0f
        }

        return floatArrayOf(avgSpeed, speedVariance, acceleration)
    }

    /**
     * Calculate total path length of all strokes.
     */
    private fun calculateTotalLength(points: List<com.handwriting.app.data.model.StrokePoint>): Float {
        var length = 0f
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            length += kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return length
    }

    /**
     * Create an empty feature vector for error cases.
     */
    private fun createEmptyFeatureVector(): FeatureVector {
        return FeatureVector(
            directionHistogram = FloatArray(DIRECTION_BINS) { 0f },
            curvatureFeatures = floatArrayOf(0f, 0f, 0f, 0f),
            aspectRatio = 1f,
            strokeCount = 0,
            totalLength = 0f,
            temporalFeatures = floatArrayOf(0f, 0f, 0f)
        )
    }

    /**
     * Calculate Euclidean distance between two feature vectors.
     * Lower distance indicates higher similarity.
     */
    fun calculateDistance(features1: FeatureVector, features2: FeatureVector): Float {
        var distance = 0f

        // Direction histogram distance
        for (i in features1.directionHistogram.indices) {
            val diff = features1.directionHistogram[i] - features2.directionHistogram[i]
            distance += diff * diff
        }

        // Curvature features distance
        for (i in features1.curvatureFeatures.indices) {
            val diff = features1.curvatureFeatures[i] - features2.curvatureFeatures[i]
            distance += diff * diff
        }

        // Aspect ratio distance (weighted)
        val aspectDiff = features1.aspectRatio - features2.aspectRatio
        distance += aspectDiff * aspectDiff * 2f  // Weight aspect ratio more

        // Stroke count difference (discrete)
        val strokeDiff = kotlin.math.abs(features1.strokeCount - features2.strokeCount)
        distance += strokeDiff * strokeDiff * 3f  // Weight stroke count heavily

        // Temporal features distance
        for (i in features1.temporalFeatures.indices) {
            val diff = features1.temporalFeatures[i] - features2.temporalFeatures[i]
            distance += diff * diff
        }

        return kotlin.math.sqrt(distance)
    }
}
