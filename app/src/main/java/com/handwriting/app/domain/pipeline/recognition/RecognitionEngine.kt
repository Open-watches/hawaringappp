package com.handwriting.app.domain.pipeline.recognition

import com.handwriting.app.data.model.CandidateSource
import com.handwriting.app.data.model.FeatureVector
import com.handwriting.app.data.model.HandwritingSample
import com.handwriting.app.data.model.RecognitionCandidate
import com.handwriting.app.data.repository.HandwritingRepository
import com.handwriting.app.domain.pipeline.extraction.FeatureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recognition engine that compares input features against stored samples.
 * Supports both base model patterns and user-trained custom data.
 */
class RecognitionEngine(private val repository: HandwritingRepository) {

    private val featureExtractor = FeatureExtractor()

    // Base character templates (simplified for demonstration)
    // In production, these would be pre-trained models or larger datasets
    private val baseTemplates = mutableMapOf<String, FeatureVector>()

    // User-trained feature vectors cached in memory for fast access
    private var userTrainedCache: Map<String, List<FeatureVector>> = emptyMap()
    private var cacheTimestamp: Long = 0L
    private val CACHE_VALIDITY_MS = 30_000L // 30 seconds

    /**
     * Initialize base recognition templates.
     * Called once at app startup.
     */
    suspend fun initializeBaseTemplates() = withContext(Dispatchers.IO) {
        // Load or generate base templates for common characters
        // This is a simplified example - in production, load from assets or pre-trained data
        baseTemplates.clear()
        
        // Placeholder: In production, load actual trained templates
        // For now, we'll rely on user training to build the recognition database
    }

    /**
     * Refresh the user-trained cache from database.
     */
    private suspend fun refreshUserCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - cacheTimestamp > CACHE_VALIDITY_MS || userTrainedCache.isEmpty()) {
            userTrainedCache = buildUserTrainedCache()
            cacheTimestamp = now
        }
    }

    /**
     * Build feature vector cache from user-trained samples.
     */
    private suspend fun buildUserTrainedCache(): Map<String, List<FeatureVector>> {
        val trainingData = repository.getTrainingData()
        val cache = mutableMapOf<String, List<FeatureVector>>()

        for ((label, samples) in trainingData) {
            val features = samples.mapNotNull { sample ->
                if (sample.strokes.isNotEmpty()) {
                    featureExtractor.extractFeatures(sample.strokes)
                } else {
                    null
                }
            }
            if (features.isNotEmpty()) {
                cache[label] = features
            }
        }

        return cache
    }

    /**
     * Recognize handwriting from extracted features.
     * Returns ranked list of candidates.
     * 
     * @param inputFeatures Features extracted from user input
     * @param maxCandidates Maximum number of candidates to return
     * @return Sorted list of recognition candidates
     */
    suspend fun recognize(
        inputFeatures: FeatureVector,
        maxCandidates: Int = 6
    ): List<RecognitionCandidate> = withContext(Dispatchers.Default) {
        refreshUserCacheIfNeeded()

        val allCandidates = mutableListOf<RecognitionCandidate>()

        // Compare against user-trained samples first (higher priority)
        for ((label, featureList) in userTrainedCache) {
            val minDistance = featureList.minOfOrNull { features ->
                featureExtractor.calculateDistance(inputFeatures, features)
            } ?: Float.MAX_VALUE

            val confidence = calculateConfidence(minDistance)
            
            if (confidence > 0.1f) { // Threshold for consideration
                allCandidates.add(
                    RecognitionCandidate(
                        character = label,
                        confidence = confidence,
                        distance = minDistance,
                        source = CandidateSource.USER_TRAINED
                    )
                )
            }
        }

        // Compare against base templates
        for ((label, baseFeatures) in baseTemplates) {
            // Skip if we already have this character from user training with higher confidence
            if (allCandidates.any { it.character == label && it.source == CandidateSource.USER_TRAINED }) {
                continue
            }

            val distance = featureExtractor.calculateDistance(inputFeatures, baseFeatures)
            val confidence = calculateConfidence(distance)

            if (confidence > 0.1f) {
                allCandidates.add(
                    RecognitionCandidate(
                        character = label,
                        confidence = confidence,
                        distance = distance,
                        source = CandidateSource.BASE_MODEL
                    )
                )
            }
        }

        // Sort by confidence (descending) and return top candidates
        allCandidates
            .sortedByDescending { it.confidence }
            .take(maxCandidates)
    }

    /**
     * Calculate confidence score from distance metric.
     * Uses exponential decay function.
     */
    private fun calculateConfidence(distance: Float): Float {
        // Tunable parameter: lower = stricter matching
        val sensitivity = 0.5f
        
        // Exponential decay: confidence = e^(-sensitivity * distance)
        return kotlin.math.exp(-sensitivity * distance).toFloat()
    }

    /**
     * Train the engine with a new labeled sample.
     * Updates the user-trained cache immediately.
     */
    suspend fun trainWithSample(sample: HandwritingSample) = withContext(Dispatchers.IO) {
        if (sample.strokes.isEmpty()) return@withContext

        val features = featureExtractor.extractFeatures(sample.strokes)
        
        // Update cache
        val currentList = userTrainedCache[sample.label] ?: emptyList()
        userTrainedCache = userTrainedCache + (sample.label to (currentList + features))
        
        // Invalidate cache timestamp to force refresh on next recognition
        cacheTimestamp = 0L
    }

    /**
     * Clear all user-trained data from cache.
     * Call this after deleting samples.
     */
    fun clearUserCache() {
        userTrainedCache = emptyMap()
        cacheTimestamp = 0L
    }

    /**
     * Get statistics about the current recognition model.
     */
    fun getModelStats(): ModelStats {
        return ModelStats(
            baseTemplateCount = baseTemplates.size,
            userTrainedCharacters = userTrainedCache.keys.size,
            totalUserSamples = userTrainedCache.values.sumOf { it.size },
            isCacheValid = System.currentTimeMillis() - cacheTimestamp < CACHE_VALIDITY_MS
        )
    }

    /**
     * Recognition model statistics.
     */
    data class ModelStats(
        val baseTemplateCount: Int,
        val userTrainedCharacters: Int,
        val totalUserSamples: Int,
        val isCacheValid: Boolean
    )
}
