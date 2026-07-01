package com.tvbykafi.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tvbykafi.app.R

class CategoryAdapter(
    private val onCategoryClick: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var categories = listOf<String>()
    private var activeCategory = "ALL"

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvCategoryName)
    }

    fun setCategories(list: List<String>) {
        val newList = listOf("ALL") + list
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = categories.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = categories[oldPos] == newList[newPos]
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = categories[oldPos] == newList[newPos]
        })
        categories = newList
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return VH(view)
    }

    override fun getItemCount() = categories.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = categories[position]
        holder.tvName.text = cat

        val isActive = cat == activeCategory
        holder.tvName.setBackgroundResource(
            if (isActive) R.drawable.bg_chip_active else R.drawable.bg_chip
        )
        holder.tvName.setTextColor(
            holder.itemView.context.getColor(
                if (isActive) R.color.text_primary else R.color.text_secondary
            )
        )

        holder.itemView.isFocusable = true
        holder.tvName.isFocusable = true

        val clickAction = View.OnClickListener {
            val oldActive = activeCategory
            activeCategory = cat
            val oldPos = categories.indexOf(oldActive)
            val newPos = holder.bindingAdapterPosition
            if (oldPos >= 0) notifyItemChanged(oldPos)
            if (newPos >= 0) notifyItemChanged(newPos)
            onCategoryClick(cat)
        }

        holder.tvName.setOnClickListener(clickAction)
        holder.itemView.setOnClickListener(clickAction)

        holder.tvName.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start()
                (v as TextView).setBackgroundResource(R.drawable.bg_chip_active)
            } else if (!isActive) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                (v as TextView).setBackgroundResource(R.drawable.bg_chip)
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
    }
}
