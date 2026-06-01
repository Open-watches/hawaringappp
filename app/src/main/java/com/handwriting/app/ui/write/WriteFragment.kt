package com.handwriting.app.ui.write

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.handwriting.app.R
import com.handwriting.app.data.model.HandwritingSample
import com.handwriting.app.data.model.RecognitionCandidate
import com.handwriting.app.data.model.Stroke
import com.handwriting.app.databinding.FragmentWriteBinding
import com.handwriting.app.domain.pipeline.extraction.FeatureExtractor
import com.handwriting.app.domain.pipeline.recognition.RecognitionEngine
import com.handwriting.app.util.StrokeSimplifier
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Writing screen fragment - main recognition interface.
 * Users draw on canvas and see real-time predictions.
 */
class WriteFragment : Fragment() {

    private var _binding: FragmentWriteBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: WriteViewModel
    private lateinit var recognitionEngine: RecognitionEngine
    private val featureExtractor = FeatureExtractor()

    // Simplification epsilon for storage optimization
    private val simplificationEpsilon = 2.0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[WriteViewModel::class.java]

        // Initialize recognition engine
        recognitionEngine = RecognitionEngine(viewModel.repository)
        lifecycleScope.launch {
            recognitionEngine.initializeBaseTemplates()
        }

        setupCanvas()
        setupButtons()
        observeViewModel()
    }

    private fun setupCanvas() {
        binding.canvas.apply {
            setAutoRecognition(true)
            setRecognitionDebounce(400L)

            onStrokesChanged = { strokes ->
                if (strokes.isNotEmpty()) {
                    performRecognition(strokes)
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnUndo.setOnClickListener {
            binding.canvas.undo()
            updateButtonStates()
        }

        binding.btnRedo.setOnClickListener {
            binding.canvas.redo()
            updateButtonStates()
        }

        binding.btnClear.setOnClickListener {
            binding.canvas.clear()
            clearPredictions()
            updateButtonStates()
        }

        binding.btnRecognize.setOnClickListener {
            val strokes = binding.canvas.getAllStrokes()
            if (strokes.isNotEmpty()) {
                performRecognition(strokes)
            } else {
                Toast.makeText(context, "Draw something first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBackground.setOnClickListener {
            binding.canvas.cycleBackground()
        }

        binding.btnSave.setOnClickListener {
            saveCurrentSample()
        }
    }

    private fun updateButtonStates() {
        binding.btnUndo.isEnabled = binding.canvas.getStrokeCount() > 0
        binding.btnClear.isEnabled = binding.canvas.hasContent()
        binding.btnRecognize.isEnabled = binding.canvas.hasContent()
        binding.btnSave.isEnabled = binding.canvas.hasContent()
    }

    private fun performRecognition(strokes: List<Stroke>) {
        if (strokes.isEmpty()) return

        lifecycleScope.launch {
            try {
                // Normalize strokes before feature extraction
                val normalizedStrokes = withContext(Dispatchers.Default) {
                    // Apply simplification for performance
                    strokes.map { stroke ->
                        val simplifiedPoints = StrokeSimplifier.simplify(
                            stroke.points, 
                            simplificationEpsilon
                        )
                        stroke.copy(points = simplifiedPoints)
                    }
                }

                // Extract features
                val features = withContext(Dispatchers.Default) {
                    featureExtractor.extractFeatures(normalizedStrokes)
                }

                // Recognize
                val candidates = recognitionEngine.recognize(features)
                
                withContext(Dispatchers.Main) {
                    displayCandidates(candidates)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context, 
                    "Recognition error: ${e.message}", 
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun displayCandidates(candidates: List<RecognitionCandidate>) {
        binding.candidatesContainer.removeAllViews()

        if (candidates.isEmpty()) {
            binding.topPrediction.text = "?"
            return
        }

        // Display top prediction
        val topCandidate = candidates.first()
        binding.topPrediction.text = topCandidate.character
        
        // Create alternative candidate chips
        candidates.drop(1).forEach { candidate ->
            val chip = Chip(requireContext()).apply {
                text = candidate.character
                isClickable = true
                setOnClickListener {
                    // Insert selected character (for future text input mode)
                    Toast.makeText(
                        context, 
                        "Selected: ${candidate.character}", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            binding.candidatesContainer.addView(chip)
        }
    }

    private fun clearPredictions() {
        binding.topPrediction.text = "-"
        binding.candidatesContainer.removeAllViews()
    }

    private fun saveCurrentSample() {
        val strokes = binding.canvas.getAllStrokes()
        if (strokes.isEmpty()) return

        lifecycleScope.launch {
            try {
                // For now, use top prediction as label
                // In production, prompt user to confirm/edit label
                val label = binding.topPrediction.text.toString()
                    .takeIf { it != "?" && it != "-" } ?: "unknown"

                // Simplify points before saving
                val simplifiedStrokes = strokes.map { stroke ->
                    stroke.copy(
                        points = StrokeSimplifier.simplify(stroke.points, simplificationEpsilon)
                    )
                }

                val sample = HandwritingSample(
                    strokes = simplifiedStrokes,
                    label = label,
                    isUserTrained = true
                )

                viewModel.saveSample(sample)
                
                Toast.makeText(context, "Sample saved!", Toast.LENGTH_SHORT).show()
                
                // Clear canvas after saving
                binding.canvas.clear()
                clearPredictions()
            } catch (e: Exception) {
                Toast.makeText(
                    context, 
                    "Save error: ${e.message}", 
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.candidates.observe(viewLifecycleOwner) { candidates ->
            displayCandidates(candidates)
        }

        viewModel.isSaving.observe(viewLifecycleOwner) { isSaving ->
            binding.btnSave.isEnabled = !isSaving
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): WriteFragment {
            return WriteFragment()
        }
    }
}
