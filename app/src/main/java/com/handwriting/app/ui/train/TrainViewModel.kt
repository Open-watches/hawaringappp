package com.handwriting.app.ui.train

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.handwriting.app.data.model.CharacterSet
import com.handwriting.app.data.model.HandwritingSample
import com.handwriting.app.data.repository.HandwritingRepository
import com.handwriting.app.domain.training.TrainingWorkflowManager
import kotlinx.coroutines.launch

/**
 * ViewModel for the Training screen.
 */
class TrainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HandwritingRepository
    private val trainingManager = TrainingWorkflowManager()

    private val _savedSamples = MutableLiveData<List<HandwritingSample>>()
    val savedSamples: LiveData<List<HandwritingSample>> = _savedSamples

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> = _isSaving

    private val _currentPrompt = MutableLiveData<String>()
    val currentPrompt: LiveData<String> = _currentPrompt

    private val _trainingProgress = MutableLiveData<Float>()
    val trainingProgress: LiveData<Float> = _trainingProgress

    init {
        val database = com.handwriting.app.data.database.HandwritingDatabase.getDatabase(application)
        repository = HandwritingRepository(database.handwritingDao(), application)

        // Load training history
        loadTrainingHistory()
        
        // Start default training with Latin uppercase
        startTraining(CharacterSet.LATIN_UPPERCASE)
    }

    /**
     * Start training with a specific character set.
     */
    fun startTraining(characterSet: CharacterSet) {
        trainingManager.startTraining(characterSet, targetSamplesPerChar = 5)
        updatePromptFromManager()
    }

    /**
     * Get the current training prompt.
     */
    fun getCurrentPrompt(): String? {
        return _currentPrompt.value
    }

    /**
     * Record a completed training sample and advance to next prompt.
     */
    fun recordCompletedSample(character: String, strokes: List<com.handwriting.app.data.model.Stroke>) {
        trainingManager.recordSample(character, strokes)
        updatePromptFromManager()
    }

    /**
     * Skip to next character.
     */
    fun skipCharacter() {
        trainingManager.skipCharacter()
        updatePromptFromManager()
    }

    private fun updatePromptFromManager() {
        val state = trainingManager.currentState.value
        val prompt = state?.currentPrompt
        _currentPrompt.value = prompt?.instruction ?: "Write a character"
        _trainingProgress.value = state?.progress?.getCompletionPercentage() ?: 0f
    }

    /**
     * Get training statistics.
     */
    fun getTrainingStats(): com.handwriting.app.domain.training.TrainingStats {
        return trainingManager.getTrainingStats()
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
                // Record completion in training manager
                if (sample.label.length == 1) {
                    recordCompletedSample(sample.label, sample.strokes)
                }
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
