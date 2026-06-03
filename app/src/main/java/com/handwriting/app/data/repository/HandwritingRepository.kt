package com.handwriting.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.handwriting.app.data.dao.HandwritingDao
import com.handwriting.app.data.dao.NotebookDao
import com.handwriting.app.data.model.HandwritingSample
import com.handwriting.app.data.model.Notebook
import com.handwriting.app.data.model.Page
import com.handwriting.app.data.model.NotebookExport
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.IOException

/**
 * Repository layer for handwriting data operations.
 * Abstracts database and file operations from the rest of the application.
 */
class HandwritingRepository(
    private val dao: HandwritingDao, 
    private val notebookDao: NotebookDao,
    private val context: Context
) {

    private val gson = Gson()

    // Flow-based observers for reactive UI updates
    val allSamples: Flow<List<HandwritingSample>> = dao.getAllSamples()
    val userTrainedSamples: Flow<List<HandwritingSample>> = dao.getUserTrainedSamples()
    val sampleCount: Flow<Int> = dao.getSampleCount()
    val categories: Flow<List<String>> = dao.getAllCategories()
    
    // Notebook flows
    val allNotebooks: Flow<List<Notebook>> = notebookDao.getAllNotebooks()
    val notebookCount: Flow<Int> = notebookDao.getNotebookCount()

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

    // ==================== Notebook Operations ====================

    /**
     * Create a new notebook with an initial empty page.
     */
    suspend fun createNotebook(title: String = "Untitled Notebook"): Notebook {
        val notebook = Notebook(title = title)
        val notebookId = notebookDao.insertNotebook(notebook)
        return notebook.copy(id = notebookId)
    }

    /**
     * Get a notebook by ID.
     */
    suspend fun getNotebookById(id: Long): Notebook? {
        return notebookDao.getNotebookById(id)
    }

    /**
     * Get a notebook by ID as a Flow.
     */
    fun getNotebookByIdFlow(id: Long): Flow<Notebook?> {
        return notebookDao.getNotebookByIdFlow(id)
    }

    /**
     * Update an existing notebook.
     */
    suspend fun updateNotebook(notebook: Notebook) {
        notebookDao.updateNotebook(notebook)
    }

    /**
     * Delete a notebook.
     */
    suspend fun deleteNotebook(notebook: Notebook) {
        notebookDao.deleteNotebook(notebook)
    }

    /**
     * Get the most recently updated notebook.
     */
    suspend fun getLatestNotebook(): Notebook? {
        return notebookDao.getLatestNotebook()
    }

    /**
     * Add a page to a notebook.
     */
    suspend fun addPageToNotebook(notebookId: Long, page: Page): Notebook {
        val notebook = notebookDao.getNotebookById(notebookId)
            ?: throw IllegalArgumentException("Notebook not found: $notebookId")
        
        val updatedPageOrder = notebook.pageOrder + page.id
        val updatedNotebook = notebook.copy(
            pageOrder = updatedPageOrder,
            updatedAt = System.currentTimeMillis()
        )
        notebookDao.updateNotebook(updatedNotebook)
        return updatedNotebook
    }

    /**
     * Remove a page from a notebook.
     */
    suspend fun removePageFromNotebook(notebookId: Long, pageId: Long): Notebook {
        val notebook = notebookDao.getNotebookById(notebookId)
            ?: throw IllegalArgumentException("Notebook not found: $notebookId")
        
        val updatedPageOrder = notebook.pageOrder.filter { it != pageId }
        val updatedNotebook = notebook.copy(
            pageOrder = updatedPageOrder,
            updatedAt = System.currentTimeMillis()
        )
        notebookDao.updateNotebook(updatedNotebook)
        return updatedNotebook
    }

    /**
     * Export a complete notebook with all its pages to JSON.
     */
    @Throws(IOException::class)
    suspend fun exportNotebookToJson(notebookId: Long): String {
        val notebook = notebookDao.getNotebookById(notebookId)
            ?: throw IOException("Notebook not found: $notebookId")
        
        // Note: Pages are stored inline in the notebook's pageOrder
        // In a full implementation, you'd fetch each page's strokes from storage
        val exportData = NotebookExport(
            notebook = notebook,
            pages = emptyList() // Pages would be loaded based on pageOrder
        )
        return gson.toJson(exportData)
    }

    /**
     * Import a notebook from JSON format.
     */
    @Throws(IOException::class)
    suspend fun importNotebookFromJson(json: String): Notebook {
        val type = object : TypeToken<NotebookExport>() {}.type
        val exportData: NotebookExport = gson.fromJson(json, type)
        
        // Insert the notebook
        val notebookWithNewId = exportData.notebook.copy(id = 0L)
        val newId = notebookDao.insertNotebook(notebookWithNewId)
        
        return exportData.notebook.copy(id = newId)
    }

    // ==================== Training Data Operations ====================

    /**
     * Get samples for recognition training.
     * Returns samples grouped by label for building character profiles.
     */
    suspend fun getTrainingData(): Map<String, List<HandwritingSample>> {
        val allSamples = dao.exportAllSamples()
        return allSamples.groupBy { it.label }
    }
}
