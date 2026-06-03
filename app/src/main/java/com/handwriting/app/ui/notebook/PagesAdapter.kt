package com.handwriting.app.ui.notebook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.handwriting.app.R
import com.handwriting.app.data.model.Page
import com.handwriting.app.data.model.PageBackground
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying notebook pages in a RecyclerView.
 */
class PagesAdapter(
    private val onItemClick: (Page) -> Unit
) : RecyclerView.Adapter<PagesAdapter.PageViewHolder>() {

    private var pages: List<Page> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position], onItemClick)
    }

    override fun getItemCount(): Int = pages.size

    fun submitList(newPages: List<Page>) {
        val diffResult = DiffUtil.calculateDiff(PageDiffCallback(pages, newPages))
        pages = newPages
        diffResult.dispatchUpdatesTo(this)
    }

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val pageNumber: TextView = itemView.findViewById(R.id.pageNumber)
        private val pageBackground: TextView = itemView.findViewById(R.id.pageBackground)
        private val pageDate: TextView = itemView.findViewById(R.id.pageDate)
        private val strokeCount: TextView = itemView.findViewById(R.id.strokeCount)

        fun bind(page: Page, onItemClick: (Page) -> Unit) {
            pageNumber.text = "Page ${adapterPosition + 1}"
            
            pageBackground.text = when (page.backgroundType) {
                PageBackground.BLANK -> "Blank"
                PageBackground.RULED -> "Ruled"
                PageBackground.GRAPH -> "Graph"
            }

            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            pageDate.text = dateFormat.format(Date(page.updatedAt))

            strokeCount.text = "${page.strokes.size} strokes"

            itemView.setOnClickListener {
                onItemClick(page)
            }
        }
    }

    private class PageDiffCallback(
        private val oldList: List<Page>,
        private val newList: List<Page>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
