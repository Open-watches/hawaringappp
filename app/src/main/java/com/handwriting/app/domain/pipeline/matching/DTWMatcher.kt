package com.handwriting.app.domain.pipeline.matching

import com.handwriting.app.data.model.Stroke
import com.handwriting.app.data.model.StrokePoint
import kotlin.math.*

/**
 * Dynamic Time Warping (DTW) matcher for comparing stroke sequences.
 * Uses tangent angles and 2D coordinates for robust shape matching.
 */
class DTWMatcher {

    companion object {
        // Weight for coordinate distance vs angle distance
        private const val COORD_WEIGHT = 0.5f
        private const val ANGLE_WEIGHT = 0.5f
        
        // Sakoe-Chiba band constraint to prevent pathological warping
        private const val SAKOE_CHIBA_RADIUS = 10
    }

    /**
     * Calculate DTW distance between two stroke sequences.
     * Lower distance indicates higher similarity.
     * 
     * @param strokes1 First stroke sequence
     * @param strokes2 Second stroke sequence
     * @return DTW distance (lower is better)
     */
    fun calculateDistance(strokes1: List<Stroke>, strokes2: List<Stroke>): Float {
        if (strokes1.isEmpty() || strokes2.isEmpty()) {
            return Float.MAX_VALUE
        }

        // Extract point sequences from strokes
        val points1 = extractPoints(strokes1)
        val points2 = extractPoints(strokes2)

        if (points1.size < 2 || points2.size < 2) {
            return Float.MAX_VALUE
        }

        // Resample to equal point counts for fair comparison
        val targetPoints = max(points1.size, points2.size).coerceIn(32, 128)
        val resampled1 = resamplePoints(points1, targetPoints)
        val resampled2 = resamplePoints(points2, targetPoints)

        // Calculate local cost matrix using combined coordinate and angle distances
        val n = resampled1.size
        val m = resampled2.size
        
        // DP table for DTW
        val dp = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dp[0][0] = 0f

        for (i in 1..n) {
            // Apply Sakoe-Chiba band constraint
            val jStart = max(1, i - SAKOE_CHIBA_RADIUS)
            val jEnd = min(m, i + SAKOE_CHIBA_RADIUS)
            
            for (j in jStart..jEnd) {
                val cost = calculateLocalCost(resampled1[i - 1], resampled2[j - 1])
                
                val prevMin = minOf(
                    dp[i - 1][j],      // Insertion
                    dp[i][j - 1],      // Deletion
                    dp[i - 1][j - 1]   // Match
                )
                
                if (prevMin != Float.MAX_VALUE) {
                    dp[i][j] = prevMin + cost
                }
            }
        }

        // Normalize by path length
        val dtwDistance = dp[n][m]
        val normalizedDistance = dtwDistance / (n + m)

        return normalizedDistance
    }

    /**
     * Calculate DTW distance with tangent angle emphasis.
     * Better for recognizing characters with similar shapes but different sizes.
     */
    fun calculateDistanceWithAngleEmphasis(
        strokes1: List<Stroke>,
        strokes2: List<Stroke>,
        angleWeight: Float = 0.7f
    ): Float {
        if (strokes1.isEmpty() || strokes2.isEmpty()) {
            return Float.MAX_VALUE
        }

        val points1 = extractPoints(strokes1)
        val points2 = extractPoints(strokes2)

        if (points1.size < 2 || points2.size < 2) {
            return Float.MAX_VALUE
        }

        val targetPoints = max(points1.size, points2.size).coerceIn(32, 128)
        val resampled1 = resamplePoints(points1, targetPoints)
        val resampled2 = resamplePoints(points2, targetPoints)

        // Calculate tangent angles for each point
        val angles1 = calculateTangentAngles(resampled1)
        val angles2 = calculateTangentAngles(resampled2)

        val n = resampled1.size
        val m = resampled2.size
        
        val dp = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dp[0][0] = 0f

        for (i in 1..n) {
            val jStart = max(1, i - SAKOE_CHIBA_RADIUS)
            val jEnd = min(m, i + SAKOE_CHIBA_RADIUS)
            
            for (j in jStart..jEnd) {
                val coordCost = calculateCoordinateDistance(resampled1[i - 1], resampled2[j - 1])
                val angleCost = calculateAngleDistance(angles1[i - 1], angles2[j - 1])
                
                val cost = (1 - angleWeight) * coordCost + angleWeight * angleCost
                
                val prevMin = minOf(
                    dp[i - 1][j],
                    dp[i][j - 1],
                    dp[i - 1][j - 1]
                )
                
                if (prevMin != Float.MAX_VALUE) {
                    dp[i][j] = prevMin + cost
                }
            }
        }

        return dp[n][m] / (n + m)
    }

    /**
     * Extract all points from a list of strokes in order.
     */
    private fun extractPoints(strokes: List<Stroke>): List<StrokePoint> {
        return strokes.flatMap { it.points }
    }

    /**
     * Resample points to a fixed count using linear interpolation.
     */
    private fun resamplePoints(points: List<StrokePoint>, targetCount: Int): List<StrokePoint> {
        if (points.size <= 2 || points.size == targetCount) {
            return points
        }

        val totalLength = calculatePathLength(points)
        val stepLength = totalLength / (targetCount - 1)

        val resampled = mutableListOf<StrokePoint>()
        resampled.add(points.first())

        var accumulatedLength = 0f
        var currentIdx = 0

        for (i in 1 until targetCount - 1) {
            val targetLength = i * stepLength

            while (currentIdx < points.size - 1 && accumulatedLength < targetLength) {
                val segmentLength = calculateSegmentLength(points[currentIdx], points[currentIdx + 1])
                accumulatedLength += segmentLength
                currentIdx++
            }

            if (currentIdx > 0 && currentIdx < points.size) {
                val ratio = if (accumulatedLength > 0) {
                    (targetLength - (accumulatedLength - calculateSegmentLength(points[currentIdx - 1], points[currentIdx]))) / 
                    calculateSegmentLength(points[currentIdx - 1], points[currentIdx])
                } else {
                    0f
                }.coerceIn(0f, 1f)

                val prev = points[currentIdx - 1]
                val curr = points[currentIdx]

                val interpolatedX = prev.x + (curr.x - prev.x) * ratio
                val interpolatedY = prev.y + (curr.y - prev.y) * ratio
                val interpolatedTime = prev.timestamp + ((curr.timestamp - prev.timestamp) * ratio.toLong())

                resampled.add(
                    StrokePoint(
                        x = interpolatedX,
                        y = interpolatedY,
                        pressure = (prev.pressure + curr.pressure) / 2f,
                        timestamp = interpolatedTime
                    )
                )
            }
        }

        resampled.add(points.last())
        return resampled
    }

    /**
     * Calculate tangent angles at each point.
     */
    private fun calculateTangentAngles(points: List<StrokePoint>): FloatArray {
        val angles = FloatArray(points.size) { 0f }

        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            val dx1 = curr.x - prev.x
            val dy1 = curr.y - prev.y
            val dx2 = next.x - curr.x
            val dy2 = next.y - curr.y

            // Average direction
            val avgDx = (dx1 + dx2) / 2f
            val avgDy = (dy1 + dy2) / 2f

            angles[i] = atan2(avgDy.toDouble(), avgDx.toDouble()).toFloat()
        }

        // Handle endpoints
        if (points.size > 1) {
            angles[0] = atan2(
                (points[1].y - points[0].y).toDouble(),
                (points[1].x - points[0].x).toDouble()
            ).toFloat()
            
            angles[angles.size - 1] = atan2(
                (points.last().y - points[points.size - 2].y).toDouble(),
                (points.last().x - points[points.size - 2].x).toDouble()
            ).toFloat()
        }

        return angles
    }

    /**
     * Calculate local cost combining coordinate and angle distances.
     */
    private fun calculateLocalCost(p1: StrokePoint, p2: StrokePoint): Float {
        val coordCost = calculateCoordinateDistance(p1, p2)
        return coordCost * COORD_WEIGHT
    }

    /**
     * Calculate Euclidean distance between two points (normalized).
     */
    private fun calculateCoordinateDistance(p1: StrokePoint, p2: StrokePoint): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy) / 1000f // Normalize by typical canvas size
    }

    /**
     * Calculate angular difference between two tangent angles.
     */
    private fun calculateAngleDistance(angle1: Float, angle2: Float): Float {
        var diff = abs(angle1 - angle2)
        // Handle wraparound at PI
        if (diff > PI.toFloat()) {
            diff = 2 * PI.toFloat() - diff
        }
        return diff / PI.toFloat() // Normalize to [0, 1]
    }

    /**
     * Calculate total path length.
     */
    private fun calculatePathLength(points: List<StrokePoint>): Float {
        var length = 0f
        for (i in 1 until points.size) {
            length += calculateSegmentLength(points[i - 1], points[i])
        }
        return length
    }

    /**
     * Calculate length of a single segment.
     */
    private fun calculateSegmentLength(p1: StrokePoint, p2: StrokePoint): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculate similarity score from DTW distance.
     * Returns value in [0, 1] where 1 is perfect match.
     */
    fun calculateSimilarity(strokes1: List<Stroke>, strokes2: List<Stroke>): Float {
        val distance = calculateDistance(strokes1, strokes2)
        
        if (distance == Float.MAX_VALUE) {
            return 0f
        }

        // Convert distance to similarity using exponential decay
        return exp(-distance * 2).toFloat()
    }
}
