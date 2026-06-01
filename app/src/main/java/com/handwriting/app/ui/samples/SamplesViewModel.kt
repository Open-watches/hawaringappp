package com.handwriting.app.ui.samples

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.handwriting.app.data.model.HandwritingSample
import com.handwriting.app.data.repository.HandwritingRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the Samples screen.
 */
class SamplesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HandwritingRepository

    private val _allSamples = MutableLiveData<List<HandwritingSample>>()
    val allSamples: LiveData<List<HandwritingSample>> = _allSamples

    init {
        val database = com.handwriting.app.data.database.HandwritingDatabase.getDatabase(application)
        repository = HandwritingRepository(database.handwritingDao(), application)

        // Load all samples
        loadSamples()
    }

    private fun loadSamples() {
        viewModelScope.launch {
            repository.allSamples.collect { samples ->
                _allSamples.value = samples
            }
        }
    }

    /**
     * Delete a sample by ID.
     */
    fun deleteSampleById(id: Long) {
        viewModelScope.launch {
            repository.deleteSampleById(id)
        }
    }

    /**
     * Delete a sample.
     */
    fun deleteSample(sample: HandwritingSample) {
        viewModelScope.launch {
            repository.deleteSample(sample)
        }
    }

    /**
     * Export all samples to JSON file.
     */
    fun exportSamplesToFile() {
        viewModelScope.launch {
            // Implementation for file export
        }
    }

    /**
     * Import samples from JSON file.
     */
    fun importSamplesFromFile() {
        viewModelScope.launch {
            // Implementation for file import
        }
    }

    /**
     * Clear all samples from database.
     */
    fun clearAllSamples() {
        viewModelScope.launch {
            repository.deleteAllSamples()
        }
    }
}
