package com.handwriting.app.ui.train

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.handwriting.app.data.model.HandwritingSample
import com.handwriting.app.data.model.PageBackground
import com.handwriting.app.databinding.FragmentTrainBinding
import com.handwriting.app.util.StrokeSimplifier
import kotlinx.coroutines.launch

/**
 * Training screen fragment - allows users to label and save handwriting samples.
 * Integrated with progressive training workflow.
 */
class TrainFragment : Fragment() {

    private var _binding: FragmentTrainBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TrainViewModel

    private val simplificationEpsilon = 2.0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[TrainViewModel::class.java]

        setupCanvas()
        setupButtons()
        observeViewModel()
    }

    private fun setupCanvas() {
        binding.canvas.apply {
            setAutoRecognition(false) // Manual control in training mode
            setBackgroundType(PageBackground.RULED) // Show ruled lines for guidance
            
            onStrokesChanged = { strokes ->
                updateSaveButtonState()
            }
        }
    }

    private fun setupButtons() {
        binding.btnUndo.setOnClickListener {
            binding.canvas.undo()
            updateSaveButtonState()
        }

        binding.btnClear.setOnClickListener {
            binding.canvas.clear()
            binding.labelInput.text?.clear()
            updateSaveButtonState()
        }

        binding.btnSave.setOnClickListener {
            saveTrainingSample()
        }
        
        binding.btnSkip.setOnClickListener {
            viewModel.skipCharacter()
            Toast.makeText(context, "Skipped to next character", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSaveButtonState() {
        val hasContent = binding.canvas.hasContent()
        val hasLabel = !binding.labelInput.text.isNullOrBlank()
        binding.btnSave.isEnabled = hasContent && hasLabel
    }

    private fun saveTrainingSample() {
        val strokes = binding.canvas.getAllStrokes()
        val label = binding.labelInput.text.toString().trim()

        if (strokes.isEmpty()) {
            Toast.makeText(context, "Please draw something first", Toast.LENGTH_SHORT).show()
            return
        }

        if (label.isBlank()) {
            Toast.makeText(context, "Please enter a label", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Simplify points before saving
                val simplifiedStrokes = strokes.map { stroke ->
                    stroke.copy(
                        points = StrokeSimplifier.simplify(stroke.points, simplificationEpsilon)
                    )
                }

                val sample = HandwritingSample(
                    strokes = simplifiedStrokes,
                    label = label,
                    isUserTrained = true,
                    category = "training"
                )

                viewModel.saveSample(sample)
                
                Toast.makeText(context, "Training data saved!", Toast.LENGTH_SHORT).show()
                
                // Clear canvas for next sample
                binding.canvas.clear()
                binding.labelInput.text?.clear()
                updateSaveButtonState()
            } catch (e: Exception) {
                Toast.makeText(
                    context, 
                    "Error saving: ${e.message}", 
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.savedSamples.observe(viewLifecycleOwner) { samples ->
            // Update history list (to be implemented with RecyclerView)
        }

        viewModel.isSaving.observe(viewLifecycleOwner) { isSaving ->
            binding.btnSave.isEnabled = !isSaving
        }
        
        viewModel.currentPrompt.observe(viewLifecycleOwner) { prompt ->
            binding.trainingPrompt.text = prompt
            // Auto-fill label for single character prompts
            if (prompt.contains(": ")) {
                val character = prompt.substringAfter(": ").trim()
                if (character.length == 1) {
                    binding.labelInput.setText(character)
                }
            }
        }
        
        viewModel.trainingProgress.observe(viewLifecycleOwner) { progress ->
            binding.progressBar.progress = progress.toInt()
            binding.progressText.text = "${progress.toInt()}% complete"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): TrainFragment {
            return TrainFragment()
        }
    }
}
