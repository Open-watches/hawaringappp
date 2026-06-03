package com.handwriting.app.ui.notebook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.handwriting.app.data.model.Notebook
import com.handwriting.app.data.model.Page
import com.handwriting.app.data.model.PageBackground
import com.handwriting.app.data.repository.HandwritingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for notebook management.
 * Handles page operations, notebook persistence, and UI state.
 */
class NotebookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HandwritingRepository = HandwritingRepository(
        dao = com.handwriting.app.data.database.HandwritingDatabase.getDatabase(application).handwritingDao(),
        notebookDao = com.handwriting.app.data.database.HandwritingDatabase.getDatabase(application).notebookDao(),
        context = application
    )

    // Current notebook state
    private val _currentNotebook = MutableLiveData<Notebook?>()
    val currentNotebook: LiveData<Notebook?> = _currentNotebook

    // Current page being viewed/edited
    private val _currentPage = MutableLiveData<Page?>()
    val currentPage: LiveData<Page?> = _currentPage

    // List of all pages in current notebook
    private val _pages = MutableLiveData<List<Page>>()
    val pages: LiveData<List<Page>> = _pages

    // Initialize with empty page list
    private val pageStore = mutableMapOf<Long, Page>()

    /**
     * Load existing notebook or create a new one.
     */
    suspend fun loadOrCreateNotebook() {
        viewModelScope.launch {
            val notebook = repository.getLatestNotebook()
                ?: repository.createNotebook("My Notebook")
            
            _currentNotebook.value = notebook
            
            // Create initial page if notebook is empty
            if (notebook.pageOrder.isEmpty()) {
                val initialPage = Page(
                    id = System.currentTimeMillis(),
                    backgroundType = PageBackground.RULED
                )
                pageStore[initialPage.id] = initialPage
                _pages.value = listOf(initialPage)
                _currentPage.value = initialPage
            } else {
                // Load existing pages from store (in real app, fetch from DB)
                val existingPages = notebook.pageOrder.mapNotNull { pageStore[it] }
                _pages.value = existingPages
                _currentPage.value = existingPages.firstOrNull()
            }
        }
    }

    /**
     * Add a new page to the notebook.
     */
    suspend fun addPage(page: Page) {
        viewModelScope.launch {
            val notebook = _currentNotebook.value ?: return@launch
            
            // Assign new ID
            val newPage = page.copy(id = System.currentTimeMillis())
            pageStore[newPage.id] = newPage
            
            // Update notebook page order
            val updatedNotebook = notebook.copy(
                pageOrder = notebook.pageOrder + newPage.id,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNotebook(updatedNotebook)
            _currentNotebook.value = updatedNotebook
            
            // Update pages list
            val updatedPages = _pages.value?.plus(newPage) ?: listOf(newPage)
            _pages.value = updatedPages
            _currentPage.value = newPage
        }
    }

    /**
     * Delete a page from the notebook.
     */
    suspend fun deletePage(pageId: Long) {
        viewModelScope.launch {
            val notebook = _currentNotebook.value ?: return@launch
            val currentPageList = _pages.value ?: return@launch
            
            if (currentPageList.size <= 1) return@launch // Don't delete last page
            
            // Remove from store
            pageStore.remove(pageId)
            
            // Update notebook page order
            val updatedPageOrder = notebook.pageOrder.filter { it != pageId }
            val updatedNotebook = notebook.copy(
                pageOrder = updatedPageOrder,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNotebook(updatedNotebook)
            _currentNotebook.value = updatedNotebook
            
            // Update pages list
            val updatedPages = currentPageList.filter { it.id != pageId }
            _pages.value = updatedPages
            
            // Set new current page
            _currentPage.value = updatedPages.firstOrNull()
        }
    }

    /**
     * Update the background type of the current page.
     */
    suspend fun updatePageBackground(backgroundType: PageBackground) {
        viewModelScope.launch {
            val currentPage = _currentPage.value ?: return@launch
            val notebook = _currentNotebook.value ?: return@launch
            
            val updatedPage = currentPage.copy(
                backgroundType = backgroundType,
                updatedAt = System.currentTimeMillis()
            )
            pageStore[updatedPage.id] = updatedPage
            
            // Update pages list
            val updatedPages = _pages.value?.map { 
                if (it.id == currentPage.id) updatedPage else it 
            } ?: listOf(updatedPage)
            _pages.value = updatedPages
            _currentPage.value = updatedPage
            
            // Persist notebook update
            val updatedNotebook = notebook.copy(updatedAt = System.currentTimeMillis())
            repository.updateNotebook(updatedNotebook)
        }
    }

    /**
     * Set the current page for editing.
     */
    fun setCurrentPage(page: Page) {
        _currentPage.value = page
    }

    /**
     * Get the current page.
     */
    fun getCurrentPage(): Page? = _currentPage.value

    /**
     * Get the current page index.
     */
    fun getCurrentPageIndex(): Int {
        val currentPage = _currentPage.value ?: return 0
        val pagesList = _pages.value ?: return 0
        return pagesList.indexOfFirst { it.id == currentPage.id }.coerceAtLeast(0)
    }

    /**
     * Get total page count.
     */
    fun getPageCount(): Int = _pages.value?.size ?: 0

    /**
     * Save current page strokes (called from WriteFragment).
     */
    fun savePageStrokes(pageId: Long, strokes: List<com.handwriting.app.data.model.Stroke>) {
        viewModelScope.launch {
            val updatedPage = pageStore[pageId]?.copy(
                strokes = strokes,
                updatedAt = System.currentTimeMillis()
            )
            updatedPage?.let { pageStore[pageId] = it }
        }
    }
}
