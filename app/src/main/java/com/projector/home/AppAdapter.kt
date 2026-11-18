package com.projector.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private var items: List<AppItem>,
    private val isGrid: Boolean,
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: ((AppItem) -> Unit)? = null
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (isGrid) R.layout.item_app_grid else R.layout.item_app_card
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.label
        if (item.isAddButton) {
            holder.ivIcon.setImageDrawable(ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_add))
        } else if (item.icon != null) {
            holder.ivIcon.setImageDrawable(item.icon)
        } else {
            holder.ivIcon.setImageDrawable(ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_apps))
        }

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<AppItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
