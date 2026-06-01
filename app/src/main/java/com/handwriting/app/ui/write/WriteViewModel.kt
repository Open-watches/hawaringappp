package com.handwriting.app.ui.write

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.handwriting.app.data.model.HandwritingSample
import com.handwriting.app.data.model.RecognitionCandidate
import com.handwriting.app.data.repository.HandwritingRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the Write screen.
 * Manages recognition state and sample persistence.
 */
class WriteViewModel(application: Application) : AndroidViewModel(application) {

    val repository: HandwritingRepository

    private val _candidates = MutableLiveData<List<RecognitionCandidate>>()
    val candidates: LiveData<List<RecognitionCandidate>> = _candidates

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> = _isSaving

    private val _currentStrokes = MutableLiveData<List<com.handwriting.app.data.model.Stroke>>()
    val currentStrokes: LiveData<List<com.handwriting.app.data.model.Stroke>> = _currentStrokes

    init {
        val database = com.handwriting.app.data.database.HandwritingDatabase.getDatabase(application)
        repository = HandwritingRepository(database.handwritingDao(), application)
    }

    /**
     * Update current recognition candidates.
     */
    fun updateCandidates(candidates: List<RecognitionCandidate>) {
        _candidates.value = candidates
    }

    /**
     * Update current strokes being drawn.
     */
    fun updateCurrentStrokes(strokes: List<com.handwriting.app.data.model.Stroke>) {
        _currentStrokes.value = strokes
    }

    /**
     * Save a handwriting sample to the database.
     */
    fun saveSample(sample: HandwritingSample) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.insertSample(sample)
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Clear current predictions.
     */
    fun clearPredictions() {
        _candidates.value = emptyList()
    }
}

/**
 * Factory for creating WriteViewModel with dependencies.
 */
class WriteViewModelFactory(
    private val application: Application,
    private val repository: HandwritingRepository
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WriteViewModel::class.java)) {
            return WriteViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
