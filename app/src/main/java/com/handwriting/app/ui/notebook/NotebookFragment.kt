package com.handwriting.app.ui.notebook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.handwriting.app.data.model.Page
import com.handwriting.app.data.model.PageBackground
import com.handwriting.app.databinding.FragmentNotebookBinding
import kotlinx.coroutines.launch

/**
 * Notebook management fragment - handles multi-page notebook creation, navigation, and editing.
 */
class NotebookFragment : Fragment() {

    private var _binding: FragmentNotebookBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NotebookViewModel
    private lateinit var pagesAdapter: PagesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotebookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[NotebookViewModel::class.java]

        setupRecyclerView()
        setupButtons()
        observeViewModel()
        
        // Load or create default notebook
        lifecycleScope.launch {
            viewModel.loadOrCreateNotebook()
        }
    }

    private fun setupRecyclerView() {
        pagesAdapter = PagesAdapter { page ->
            // Navigate to write screen with selected page
            navigateToWritePage(page)
        }
        
        binding.pagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pagesAdapter
        }
    }

    private fun setupButtons() {
        binding.btnAddPage.setOnClickListener {
            addNewPage()
        }

        binding.btnChangeBackground.setOnClickListener {
            showBackgroundSelectionDialog()
        }

        binding.btnDeletePage.setOnClickListener {
            deleteCurrentPage()
        }
    }

    private fun addNewPage() {
        lifecycleScope.launch {
            try {
                val newPage = Page(
                    id = System.currentTimeMillis(), // Temporary ID
                    backgroundType = PageBackground.RULED
                )
                viewModel.addPage(newPage)
                Toast.makeText(context, "Page added", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error adding page: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteCurrentPage() {
        val currentPage = viewModel.getCurrentPage() ?: return
        
        if (viewModel.getPageCount() <= 1) {
            Toast.makeText(context, "Cannot delete the last page", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                viewModel.deletePage(currentPage.id)
                Toast.makeText(context, "Page deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error deleting page: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBackgroundSelectionDialog() {
        val backgrounds = arrayOf("Blank", "Ruled", "Graph")
        val currentPage = viewModel.getCurrentPage()
        val currentIndex = when (currentPage?.backgroundType) {
            PageBackground.BLANK -> 0
            PageBackground.RULED -> 1
            PageBackground.GRAPH -> 2
            else -> 1
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Background")
            .setSingleChoiceItems(backgrounds, currentIndex) { dialog, which ->
                val newBackground = when (which) {
                    0 -> PageBackground.BLANK
                    1 -> PageBackground.RULED
                    2 -> PageBackground.GRAPH
                    else -> PageBackground.RULED
                }
                
                lifecycleScope.launch {
                    viewModel.updatePageBackground(newBackground)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToWritePage(page: Page) {
        viewModel.setCurrentPage(page)
        
        val action = NotebookFragmentDirections.actionNotebookToWrite(page.id)
        findNavController().navigate(action)
    }

    private fun observeViewModel() {
        viewModel.currentPage.observe(viewLifecycleOwner) { page ->
            page?.let {
                updatePageInfo(it)
            }
        }

        viewModel.pages.observe(viewLifecycleOwner) { pages ->
            pagesAdapter.submitList(pages)
            updatePageCount(pages.size)
        }

        viewModel.currentNotebook.observe(viewLifecycleOwner) { notebook ->
            notebook?.let {
                binding.toolbar.title = it.title
            }
        }
    }

    private fun updatePageInfo(page: Page) {
        val pageInfo = when (page.backgroundType) {
            PageBackground.BLANK -> "Blank"
            PageBackground.RULED -> "Ruled"
            PageBackground.GRAPH -> "Graph"
        }
        binding.pageInfo.text = "Page ${viewModel.getCurrentPageIndex() + 1} • $pageInfo"
    }

    private fun updatePageCount(count: Int) {
        binding.pageCount.text = "$count page${if (count != 1) "s" else ""}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): NotebookFragment {
            return NotebookFragment()
        }
    }
}
