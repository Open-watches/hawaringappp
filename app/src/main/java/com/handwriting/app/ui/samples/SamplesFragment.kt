package com.handwriting.app.ui.samples

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.handwriting.app.databinding.FragmentSamplesBinding
import kotlinx.coroutines.launch

/**
 * Samples screen fragment - displays and manages saved handwriting samples.
 */
class SamplesFragment : Fragment() {

    private var _binding: FragmentSamplesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SamplesViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSamplesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[SamplesViewModel::class.java]

        setupRecyclerView()
        setupToolbar()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        // Initialize RecyclerView with adapter (to be implemented)
        // binding.samplesRecyclerView.adapter = SamplesAdapter()
    }

    private fun setupToolbar() {
        // Setup toolbar actions for export/import
    }

    private fun observeViewModel() {
        viewModel.allSamples.observe(viewLifecycleOwner) { samples ->
            // Update RecyclerView adapter
            if (samples.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                // binding.samplesRecyclerView.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                // binding.samplesRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Show confirmation dialog before deleting a sample.
     */
    private fun showDeleteConfirmation(sampleId: Long, sampleLabel: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Sample")
            .setMessage("Are you sure you want to delete '$sampleLabel'?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    viewModel.deleteSampleById(sampleId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SamplesFragment {
            return SamplesFragment()
        }
    }
}
