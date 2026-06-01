package com.handwriting.app.util

import com.handwriting.app.data.model.BoundingBox
import com.handwriting.app.data.model.Stroke
import com.handwriting.app.data.model.StrokePoint

/**
 * Ramer-Douglas-Peucker algorithm for stroke point simplification.
 * Reduces the number of points while preserving the stroke's shape.
 * Critical for storage optimization and performance.
 */
object StrokeSimplifier {

    /**
     * Simplify a stroke using the Ramer-Douglas-Peucker algorithm.
     * @param points Original list of stroke points
     * @param epsilon Tolerance threshold (higher = more simplification)
     * @return Simplified list of points
     */
    fun simplify(points: List<StrokePoint>, epsilon: Float = 2.0f): List<StrokePoint> {
        if (points.size <= 2) return points

        val simplified = rdpSimplify(points, 0, points.size - 1, epsilon)
        return simplified
    }

    private fun rdpSimplify(
        points: List<StrokePoint>,
        startIndex: Int,
        endIndex: Int,
        epsilon: Float
    ): List<StrokePoint> {
        var maxDistance = 0f
        var index = startIndex

        // Find the point with the maximum perpendicular distance from the line
        for (i in (startIndex + 1) until endIndex) {
            val distance = perpendicularDistance(
                points[i],
                points[startIndex],
                points[endIndex]
            )
            if (distance > maxDistance) {
                maxDistance = distance
                index = i
            }
        }

        // If max distance is greater than epsilon, recursively simplify
        return if (maxDistance > epsilon) {
            val leftResults = rdpSimplify(points, startIndex, index, epsilon)
            val rightResults = rdpSimplify(points, index, endIndex, epsilon)
            
            // Combine results, avoiding duplicate middle point
            leftResults + rightResults.drop(1)
        } else {
            // Return only the endpoints
            listOf(points[startIndex], points[endIndex])
        }
    }

    /**
     * Calculate perpendicular distance from point p to line through p1 and p2.
     */
    private fun perpendicularDistance(p: StrokePoint, p1: StrokePoint, p2: StrokePoint): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y

        // Handle vertical and horizontal lines
        if (dx == 0f && dy == 0f) {
            // p1 and p2 are the same point
            return distance(p, p1)
        }

        // Calculate the projection parameter t
        val t = ((p.x - p1.x) * dx + (p.y - p1.y) * dy) / (dx * dx + dy * dy)

        // Clamp t to [0, 1] to get the closest point on the line segment
        val clampedT = t.coerceIn(0f, 1f)

        // Find the closest point on the line segment
        val closestX = p1.x + clampedT * dx
        val closestY = p1.y + clampedT * dy

        return distance(p, StrokePoint(closestX, closestY, p.pressure, p.timestamp))
    }

    /**
     * Calculate Euclidean distance between two points.
     */
    private fun distance(p1: StrokePoint, p2: StrokePoint): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Downsample points by time interval.
     * Keeps one point per specified time interval.
     */
    fun downsampleByTime(points: List<StrokePoint>, intervalMs: Long = 10L): List<StrokePoint> {
        if (points.isEmpty()) return points

        val result = mutableListOf<StrokePoint>()
        var lastTimestamp = points.first().timestamp

        for (point in points) {
            if (point.timestamp - lastTimestamp >= intervalMs) {
                result.add(point)
                lastTimestamp = point.timestamp
            }
        }

        // Always include the last point
        if (result.lastOrNull() != points.last()) {
            result.add(points.last())
        }

        return result
    }

    /**
     * Downsample points by count (keep every Nth point).
     */
    fun downsampleByCount(points: List<StrokePoint>, factor: Int = 2): List<StrokePoint> {
        if (factor <= 1 || points.size <= 2) return points
        return points.filterIndexed { index, _ -> index % factor == 0 || index == points.size - 1 }
    }
}

/**
 * Normalization utilities for stroke preprocessing.
 */
object StrokeNormalizer {

    /**
     * Normalize stroke points to a standard coordinate system.
     * Scales to fit within a unit square while preserving aspect ratio.
     */
    fun normalizeToUnitSquare(strokes: List<Stroke>): List<Stroke> {
        if (strokes.isEmpty()) return strokes

        // Calculate bounding box for all strokes
        val allPoints = strokes.flatMap { it.points }
        val boundingBox = calculateBoundingBox(allPoints)

        val size = kotlin.math.max(boundingBox.width, boundingBox.height)
        if (size == 0f) return strokes

        val scale = 1.0f / size
        val offsetX = boundingBox.minX
        val offsetY = boundingBox.minY

        return strokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { point ->
                    point.copy(
                        x = (point.x - offsetX) * scale,
                        y = (point.y - offsetY) * scale
                    )
                }
            )
        }
    }

    /**
     * Center strokes around origin (0, 0).
     */
    fun centerAtOrigin(strokes: List<Stroke>): List<Stroke> {
        if (strokes.isEmpty()) return strokes

        val allPoints = strokes.flatMap { it.points }
        val boundingBox = calculateBoundingBox(allPoints)

        val centerX = (boundingBox.minX + boundingBox.maxX) / 2
        val centerY = (boundingBox.minY + boundingBox.maxY) / 2

        return strokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { point ->
                    point.copy(
                        x = point.x - centerX,
                        y = point.y - centerY
                    )
                }
            )
        }
    }

    /**
     * Calculate bounding box for a list of points.
     */
    fun calculateBoundingBox(points: List<StrokePoint>): BoundingBox {
        if (points.isEmpty()) {
            return BoundingBox(0f, 0f, 0f, 0f)
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (point in points) {
            minX = kotlin.math.min(minX, point.x)
            minY = kotlin.math.min(minY, point.y)
            maxX = kotlin.math.max(maxX, point.x)
            maxY = kotlin.math.max(maxY, point.y)
        }

        return BoundingBox(minX, minY, maxX, maxY)
    }

    /**
     * Smooth stroke points using moving average.
     * Reduces jitter from finger/stylus input.
     */
    fun smoothPoints(points: List<StrokePoint>, windowSize: Int = 3): List<StrokePoint> {
        if (points.size <= windowSize) return points

        val smoothed = mutableListOf<StrokePoint>()
        val halfWindow = windowSize / 2

        for (i in points.indices) {
            val startIdx = kotlin.math.max(0, i - halfWindow)
            val endIdx = kotlin.math.min(points.size - 1, i + halfWindow)

            var sumX = 0f
            var sumY = 0f
            var sumPressure = 0f
            var count = 0

            for (j in startIdx..endIdx) {
                sumX += points[j].x
                sumY += points[j].y
                sumPressure += points[j].pressure
                count++
            }

            smoothed.add(
                StrokePoint(
                    x = sumX / count,
                    y = sumY / count,
                    pressure = sumPressure / count,
                    timestamp = points[i].timestamp
                )
            )
        }

        return smoothed
    }

    /**
     * Resample stroke to have a fixed number of points.
     * Useful for feature extraction consistency.
     */
    fun resampleToFixedPoints(points: List<StrokePoint>, targetCount: Int = 64): List<StrokePoint> {
        if (points.isEmpty() || targetCount <= 0) return points
        if (points.size == targetCount) return points

        val totalLength = calculatePathLength(points)
        val segmentLength = totalLength / (targetCount - 1)

        val resampled = mutableListOf<StrokePoint>()
        resampled.add(points.first())

        var accumulatedDistance = 0f
        var targetDistance = segmentLength
        var currentIndex = 1

        while (resampled.size < targetCount && currentIndex < points.size) {
            val prevPoint = points[currentIndex - 1]
            val currPoint = points[currentIndex]
            val segmentDist = distance(prevPoint, currPoint)

            accumulatedDistance += segmentDist

            while (accumulatedDistance >= targetDistance && resampled.size < targetCount) {
                val ratio = (targetDistance - (accumulatedDistance - segmentDist)) / segmentDist
                val newX = prevPoint.x + ratio * (currPoint.x - prevPoint.x)
                val newY = prevPoint.y + ratio * (currPoint.y - prevPoint.y)
                val newTimestamp = prevPoint.timestamp + (ratio * (currPoint.timestamp - prevPoint.timestamp)).toLong()

                resampled.add(StrokePoint(newX, newY, prevPoint.pressure, newTimestamp))
                targetDistance += segmentLength
            }

            currentIndex++
        }

        // Ensure we have exactly targetCount points
        while (resampled.size < targetCount) {
            resampled.add(points.last())
        }

        return resampled.take(targetCount)
    }

    private fun calculatePathLength(points: List<StrokePoint>): Float {
        var length = 0f
        for (i in 1 until points.size) {
            length += distance(points[i - 1], points[i])
        }
        return length
    }

    private fun distance(p1: StrokePoint, p2: StrokePoint): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
