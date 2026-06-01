package com.handwriting.app.ui.train

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.handwriting.app.data.model.HandwritingSample
import com.handwriting.app.data.repository.HandwritingRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the Training screen.
 */
class TrainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HandwritingRepository

    private val _savedSamples = MutableLiveData<List<HandwritingSample>>()
    val savedSamples: LiveData<List<HandwritingSample>> = _savedSamples

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> = _isSaving

    init {
        val database = com.handwriting.app.data.database.HandwritingDatabase.getDatabase(application)
        repository = HandwritingRepository(database.handwritingDao(), application)

        // Load training history
        loadTrainingHistory()
    }

    private fun loadTrainingHistory() {
        viewModelScope.launch {
            repository.userTrainedSamples.collect { samples ->
                _savedSamples.value = samples
            }
        }
    }

    /**
     * Save a training sample to the database.
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
     * Delete a training sample.
     */
    fun deleteSample(sample: HandwritingSample) {
        viewModelScope.launch {
            repository.deleteSample(sample)
        }
    }
}
