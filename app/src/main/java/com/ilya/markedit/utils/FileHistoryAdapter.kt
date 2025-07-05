package com.ilya.markedit.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ilya.markedit.R

class FileHistoryAdapter(
    private val onItemClick: (FileHistoryItem) -> Unit
) : RecyclerView.Adapter<FileHistoryAdapter.ViewHolder>() {

    private val items = mutableListOf<FileHistoryItem>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.displayName
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<FileHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}