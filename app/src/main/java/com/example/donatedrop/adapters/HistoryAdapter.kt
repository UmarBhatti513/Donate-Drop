package com.example.donatedrop.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.donatedrop.R
import com.example.donatedrop.models.DonationHistoryItem
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private val items: MutableList<DonationHistoryItem> = mutableListOf(),
    private val onItemClick: ((DonationHistoryItem) -> Unit)? = null
) : RecyclerView.Adapter<HistoryAdapter.HistoryVH>() {

    fun setItems(newItems: List<DonationHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addItem(item: DonationHistoryItem) {
        items.add(0, item)
        notifyItemInserted(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return HistoryVH(v)
    }

    override fun onBindViewHolder(holder: HistoryVH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBloodType: TextView = itemView.findViewById(R.id.tv_blood_type)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tv_date_time)
        private val tvLocation: TextView = itemView.findViewById(R.id.tv_location)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        private val imgDot: ImageView = itemView.findViewById(R.id.img_status_dot)

        fun bind(item: DonationHistoryItem) {
            tvBloodType.text = item.bloodType.ifBlank { "Unknown" }

            // show completedAt if available, otherwise createdAt
            val timeStamp = item.completedAt ?: item.createdAt
            if (timeStamp != null) {
                val date = timeStamp.toDate()
                val sdf = SimpleDateFormat("dd MMM yyyy\nhh:mm a", Locale.getDefault())
                tvDateTime.text = sdf.format(date)
            } else {
                tvDateTime.text = ""
            }

            tvLocation.text = item.address.ifBlank { "Unknown location" }

            val statusDisplay = item.status?.takeIf { it.isNotBlank() } ?: "Completed"
            tvStatus.text = statusDisplay

            val ctx = itemView.context
            val color = when (statusDisplay.trim().lowercase(Locale.getDefault())) {
                "completed" -> R.color.statusCompleted
                "pending" -> R.color.amber
                "open" -> R.color.primary_red
                else -> R.color.textSecondary
            }
            tvStatus.setTextColor(ContextCompat.getColor(ctx, color))
            imgDot.setColorFilter(ContextCompat.getColor(ctx, color))

            itemView.setOnClickListener { onItemClick?.invoke(item) }
        }
    }
}

