package com.handwriting.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.handwriting.app.data.dao.HandwritingDao
import com.handwriting.app.data.model.HandwritingSample
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.IOException

/**
 * Repository layer for handwriting data operations.
 * Abstracts database and file operations from the rest of the application.
 */
class HandwritingRepository(private val dao: HandwritingDao, private val context: Context) {

    private val gson = Gson()

    // Flow-based observers for reactive UI updates
    val allSamples: Flow<List<HandwritingSample>> = dao.getAllSamples()
    val userTrainedSamples: Flow<List<HandwritingSample>> = dao.getUserTrainedSamples()
    val sampleCount: Flow<Int> = dao.getSampleCount()
    val categories: Flow<List<String>> = dao.getAllCategories()

    /**
     * Get samples filtered by category.
     */
    fun getSamplesByCategory(category: String): Flow<List<HandwritingSample>> {
        return dao.getSamplesByCategory(category)
    }

    /**
     * Get samples for a specific label.
     */
    fun getSamplesByLabel(label: String): Flow<List<HandwritingSample>> {
        return dao.getSamplesByLabel(label)
    }

    /**
     * Insert a new handwriting sample.
     * Returns the generated ID.
     */
    suspend fun insertSample(sample: HandwritingSample): Long {
        return dao.insertSample(sample)
    }

    /**
     * Insert multiple samples (batch operation).
     */
    suspend fun insertSamples(samples: List<HandwritingSample>): List<Long> {
        return dao.insertSamples(samples)
    }

    /**
     * Update an existing sample.
     */
    suspend fun updateSample(sample: HandwritingSample) {
        dao.updateSample(sample)
    }

    /**
     * Delete a sample.
     */
    suspend fun deleteSample(sample: HandwritingSample) {
        dao.deleteSample(sample)
    }

    /**
     * Delete a sample by ID.
     */
    suspend fun deleteSampleById(id: Long) {
        dao.deleteSampleById(id)
    }

    /**
     * Clear all samples from the database.
     */
    suspend fun deleteAllSamples() {
        dao.deleteAllSamples()
    }

    /**
     * Search samples by label prefix.
     */
    suspend fun searchByPrefix(prefix: String): List<HandwritingSample> {
        return dao.searchByPrefix(prefix)
    }

    /**
     * Export all samples to JSON format.
     */
    @Throws(IOException::class)
    fun exportToJson(): String {
        val samples = runCatching {
            // Run on IO thread
            kotlinx.coroutines.runBlocking {
                dao.exportAllSamples()
            }
        }.getOrDefault(emptyList())
        return gson.toJson(samples)
    }

    /**
     * Import samples from JSON format.
     */
    @Throws(IOException::class)
    suspend fun importFromJson(json: String): Int {
        val type = object : TypeToken<List<HandwritingSample>>() {}.type
        val samples: List<HandwritingSample> = gson.fromJson(json, type)
        
        if (samples.isEmpty()) return 0
        
        // Reset IDs to avoid conflicts
        val samplesWithResetIds = samples.map { it.copy(id = 0) }
        return dao.insertSamples(samplesWithResetIds).size
    }

    /**
     * Export samples to a file.
     */
    @Throws(IOException::class)
    fun exportToFile(file: File) {
        val json = exportToJson()
        file.writeText(json)
    }

    /**
     * Import samples from a file.
     */
    @Throws(IOException::class)
    suspend fun importFromFile(file: File): Int {
        if (!file.exists()) throw IOException("File does not exist")
        val json = file.readText()
        return importFromJson(json)
    }

    /**
     * Get samples for recognition training.
     * Returns samples grouped by label for building character profiles.
     */
    suspend fun getTrainingData(): Map<String, List<HandwritingSample>> {
        val allSamples = dao.exportAllSamples()
        return allSamples.groupBy { it.label }
    }
}
