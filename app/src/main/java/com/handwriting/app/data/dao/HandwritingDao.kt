package com.handwriting.app.data.dao

import androidx.room.*
import com.handwriting.app.data.model.HandwritingSample
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for handwriting samples.
 * Provides CRUD operations for the Room database.
 */
@Dao
interface HandwritingDao {

    @Query("SELECT * FROM handwriting_samples ORDER BY createdAt DESC")
    fun getAllSamples(): Flow<List<HandwritingSample>>

    @Query("SELECT * FROM handwriting_samples WHERE category = :category ORDER BY createdAt DESC")
    fun getSamplesByCategory(category: String): Flow<List<HandwritingSample>>

    @Query("SELECT * FROM handwriting_samples WHERE label = :label ORDER BY createdAt DESC")
    fun getSamplesByLabel(label: String): Flow<List<HandwritingSample>>

    @Query("SELECT * FROM handwriting_samples WHERE isUserTrained = 1 ORDER BY createdAt DESC")
    fun getUserTrainedSamples(): Flow<List<HandwritingSample>>

    @Query("SELECT * FROM handwriting_samples WHERE id = :id")
    suspend fun getSampleById(id: Long): HandwritingSample?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: HandwritingSample): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<HandwritingSample>): List<Long>

    @Update
    suspend fun updateSample(sample: HandwritingSample)

    @Delete
    suspend fun deleteSample(sample: HandwritingSample)

    @Query("DELETE FROM handwriting_samples WHERE id = :id")
    suspend fun deleteSampleById(id: Long)

    @Query("DELETE FROM handwriting_samples")
    suspend fun deleteAllSamples()

    @Query("SELECT COUNT(*) FROM handwriting_samples")
    fun getSampleCount(): Flow<Int>

    @Query("SELECT DISTINCT category FROM handwriting_samples")
    fun getAllCategories(): Flow<List<String>>

    /**
     * Export all samples as a list (for JSON serialization).
     */
    @Query("SELECT * FROM handwriting_samples")
    suspend fun exportAllSamples(): List<HandwritingSample>

    /**
     * Search samples by label prefix (for autocomplete).
     */
    @Query("SELECT * FROM handwriting_samples WHERE label LIKE :prefix || '%' ORDER BY createdAt DESC LIMIT 10")
    suspend fun searchByPrefix(prefix: String): List<HandwritingSample>
}
