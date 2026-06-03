package com.handwriting.app.domain.pipeline.segmentation

import com.handwriting.app.data.model.Stroke
import com.handwriting.app.data.model.StrokePoint

/**
 * Segmentation engine for splitting continuous handwriting into characters/words.
 * Uses pen-lift detection and spatial-temporal heuristics.
 */
class SegmentationEngine {

    companion object {
        // Maximum time gap (ms) between strokes to consider them part of same character
        private const val MAX_STROKE_GAP_MS = 500L
        
        // Maximum spatial distance (pixels) to merge nearby strokes
        private const val MAX_SPATIAL_DISTANCE_PX = 100f
        
        // Minimum stroke count for a valid segment
        private const val MIN_STROKES_PER_SEGMENT = 1
    }

    /**
     * Segment strokes into individual characters based on pen lifts and spacing.
     * 
     * @param strokes List of input strokes in temporal order
     * @return List of stroke groups, each representing a potential character
     */
    fun segmentIntoCharacters(strokes: List<Stroke>): List<List<Stroke>> {
        if (strokes.isEmpty()) return emptyList()
        
        val segments = mutableListOf<List<Stroke>>()
        var currentSegment = mutableListOf<Stroke>()
        
        currentSegment.add(strokes[0])
        
        for (i in 1 until strokes.size) {
            val prevStroke = strokes[i - 1]
            val currStroke = strokes[i]
            
            val shouldSplit = shouldSplitSegment(prevStroke, currStroke)
            
            if (shouldSplit && currentSegment.size >= MIN_STROKES_PER_SEGMENT) {
                segments.add(currentSegment.toList())
                currentSegment = mutableListOf()
            }
            
            currentSegment.add(currStroke)
        }
        
        // Add the last segment
        if (currentSegment.isNotEmpty() && currentSegment.size >= MIN_STROKES_PER_SEGMENT) {
            segments.add(currentSegment.toList())
        }
        
        return segments
    }

    /**
     * Segment strokes into words based on larger spatial gaps.
     * 
     * @param strokes List of input strokes in temporal order
     * @return List of stroke groups, each representing a potential word
     */
    fun segmentIntoWords(strokes: List<Stroke>): List<List<Stroke>> {
        if (strokes.isEmpty()) return emptyList()
        
        val words = mutableListOf<List<Stroke>>()
        var currentWord = mutableListOf<Stroke>()
        
        currentWord.add(strokes[0])
        
        for (i in 1 until strokes.size) {
            val prevStroke = strokes[i - 1]
            val currStroke = strokes[i]
            
            val shouldSplit = shouldSplitWord(prevStroke, currStroke)
            
            if (shouldSplit && currentWord.isNotEmpty()) {
                words.add(currentWord.toList())
                currentWord = mutableListOf()
            }
            
            currentWord.add(currStroke)
        }
        
        // Add the last word
        if (currentWord.isNotEmpty()) {
            words.add(currentWord.toList())
        }
        
        return words
    }

    /**
     * Determine if two consecutive strokes should be split into different characters.
     * Uses temporal gap and spatial distance heuristics.
     */
    private fun shouldSplitSegment(prevStroke: Stroke, currStroke: Stroke): Boolean {
        // Check temporal gap
        val timeGap = getTimeGap(prevStroke, currStroke)
        if (timeGap > MAX_STROKE_GAP_MS) {
            return true
        }
        
        // Check spatial distance
        val spatialDistance = getSpatialDistance(prevStroke, currStroke)
        if (spatialDistance > MAX_SPATIAL_DISTANCE_PX * 0.5f) {
            return true
        }
        
        return false
    }

    /**
     * Determine if two consecutive strokes should be split into different words.
     * Uses larger spatial threshold than character segmentation.
     */
    private fun shouldSplitWord(prevStroke: Stroke, currStroke: Stroke): Boolean {
        // Check temporal gap (longer pause indicates word boundary)
        val timeGap = getTimeGap(prevStroke, currStroke)
        if (timeGap > MAX_STROKE_GAP_MS * 2) {
            return true
        }
        
        // Check spatial distance (larger gap indicates word boundary)
        val spatialDistance = getSpatialDistance(prevStroke, currStroke)
        if (spatialDistance > MAX_SPATIAL_DISTANCE_PX) {
            return true
        }
        
        return false
    }

    /**
     * Calculate time gap between two strokes.
     */
    private fun getTimeGap(prevStroke: Stroke, currStroke: Stroke): Long {
        if (prevStroke.points.isEmpty() || currStroke.points.isEmpty()) {
            return 0L
        }
        
        val prevEndTime = prevStroke.points.last().timestamp
        val currStartTime = currStroke.points.first().timestamp
        
        return currStartTime - prevEndTime
    }

    /**
     * Calculate spatial distance between end of previous stroke and start of current stroke.
     */
    private fun getSpatialDistance(prevStroke: Stroke, currStroke: Stroke): Float {
        if (prevStroke.points.isEmpty() || currStroke.points.isEmpty()) {
            return 0f
        }
        
        val prevEnd = prevStroke.points.last()
        val currStart = currStroke.points.first()
        
        val dx = currStart.x - prevEnd.x
        val dy = currStart.y - prevEnd.y
        
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Merge strokes that are within a spatial-temporal window.
     * Useful for combining fragmented strokes of the same character.
     */
    fun mergeNearbyStrokes(
        strokes: List<Stroke>,
        timeThreshold: Long = MAX_STROKE_GAP_MS,
        distanceThreshold: Float = MAX_SPATIAL_DISTANCE_PX
    ): List<Stroke> {
        if (strokes.size <= 1) return strokes
        
        val merged = mutableListOf<Stroke>()
        var currentGroup = mutableListOf<Stroke>()
        
        currentGroup.add(strokes[0])
        
        for (i in 1 until strokes.size) {
            val prevStroke = currentGroup.last()
            val currStroke = strokes[i]
            
            val timeGap = getTimeGap(prevStroke, currStroke)
            val spatialDistance = getSpatialDistance(prevStroke, currStroke)
            
            if (timeGap <= timeThreshold && spatialDistance <= distanceThreshold) {
                // Merge into current group
                currentGroup.add(currStroke)
            } else {
                // Finalize current group and start new one
                merged.add(mergeStrokesInGroup(currentGroup))
                currentGroup = mutableListOf(currStroke)
            }
        }
        
        // Add the last group
        if (currentGroup.isNotEmpty()) {
            merged.add(mergeStrokesInGroup(currentGroup))
        }
        
        return merged
    }

    /**
     * Merge multiple strokes into a single stroke.
     */
    private fun mergeStrokesInGroup(strokes: List<Stroke>): Stroke {
        if (strokes.isEmpty()) {
            return Stroke(points = emptyList())
        }
        
        if (strokes.size == 1) {
            return strokes[0]
        }
        
        val allPoints = strokes.flatMap { it.points }
        return Stroke(
            points = allPoints,
            characterLabel = strokes.firstOrNull()?.characterLabel
        )
    }

    /**
     * Get bounding box for a group of strokes.
     */
    fun getSegmentBoundingBox(strokes: List<Stroke>): BoundingBox? {
        if (strokes.isEmpty() || strokes.all { it.points.isEmpty() }) {
            return null
        }
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        for (stroke in strokes) {
            for (point in stroke.points) {
                minX = kotlin.math.min(minX, point.x)
                minY = kotlin.math.min(minY, point.y)
                maxX = kotlin.math.max(maxX, point.x)
                maxY = kotlin.math.max(maxY, point.y)
            }
        }
        
        return BoundingBox(minX, minY, maxX, maxY)
    }
}

/**
 * Bounding box for a stroke segment.
 */
data class BoundingBox(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
}
