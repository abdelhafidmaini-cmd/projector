package com.projector.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class AppAdapter(
    private var items: List<AppItem>,
    private val isGrid: Boolean,
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: ((AppItem) -> Unit)? = null
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconCard: MaterialCardView = view.findViewById(R.id.iconCard)
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (isGrid) R.layout.item_grid_icon else R.layout.item_dock_icon
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        val card = view.findViewById<MaterialCardView>(R.id.iconCard)
        val strokePx = (3 * parent.resources.displayMetrics.density).toInt()

        view.setOnFocusChangeListener { v, hasFocus ->
            card.strokeWidth = if (hasFocus) strokePx else 0
            val scale = if (hasFocus) 1.12f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(100).start()
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.label

        if (item.customIconRes != null) {
            holder.iconCard.setCardBackgroundColor(Color.parseColor("#660F2244"))
            holder.ivIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (16 * holder.itemView.resources.displayMetrics.density).toInt()
            holder.ivIcon.setPadding(pad, pad, pad, pad)
            holder.ivIcon.setImageDrawable(ContextCompat.getDrawable(holder.itemView.context, item.customIconRes))
        } else {
            holder.iconCard.setCardBackgroundColor(Color.TRANSPARENT)
            holder.ivIcon.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.ivIcon.setPadding(0, 0, 0, 0)
            if (item.icon != null) {
                holder.ivIcon.setImageDrawable(item.icon)
            } else {
                holder.ivIcon.setImageDrawable(ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_apps))
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}
