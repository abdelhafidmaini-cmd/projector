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
    private val onClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_banner, parent, false)
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.07f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(110).start()
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.label
        if (item.icon != null) {
            holder.ivIcon.setImageDrawable(item.icon)
        } else {
            holder.ivIcon.setImageDrawable(ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_apps))
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
