package com.example.donatedrop.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.donatedrop.R
import com.example.donatedrop.models.DonationHistoryItem
import java.text.SimpleDateFormat
import java.util.Locale

class DonationHistoryAdapter(
    private val items: MutableList<DonationHistoryItem> = mutableListOf(),
) : RecyclerView.Adapter<DonationHistoryAdapter.Holder>() {

    fun setItems(newItems: List<DonationHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addItem(item: DonationHistoryItem) {
        items.add(0, item)
        notifyItemInserted(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.donation_history_item, parent, false)
        // Note: if your layout file name is different, update R.layout.item_history_home
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_status_icon)
        private val tvDonatedTo: TextView = itemView.findViewById(R.id.tv_donated_to)
        private val tvBloodDate: TextView = itemView.findViewById(R.id.tv_blood_date)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)

        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun bind(it: DonationHistoryItem) {
            // Donated to -> prefer address then receiverName
            val recipientName = when {
                !it.receiverName.isNullOrBlank() -> it.receiverName
                !it.address.isNullOrBlank() -> it.address
                else -> "Unknown recipient"
            }
            tvDonatedTo.text = "Donated to $recipientName"

            // Blood type + date
            val blood = it.bloodType.ifBlank { "A+" }
            val dateStr = (it.completedAt ?: it.createdAt)?.toDate()?.let { d -> dateFormat.format(d) } ?: "--"
            tvBloodDate.text = "$blood | $dateStr"

            // Status text (default Completed)
            val statusDisplay = it.status?.takeIf { s -> s.isNotBlank() } ?: "completed"
            tvStatus.text = statusDisplay.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            // Optionally change icon / background based on status
            // (you already set tint/background in XML; override only if needed)
            when (statusDisplay.trim().lowercase(Locale.getDefault())) {
                "completed" -> {
                    ivIcon.setImageResource(R.drawable.check_circle) // ensure drawable exists
                    // tvStatus background is already styled in XML; change here if you want dynamic color
                }
                "pending" -> {
                    ivIcon.setImageResource(R.drawable.ic_pending) // if you have a pending icon
                }
                else -> {
                    ivIcon.setImageResource(R.drawable.check_circle)
                }
            }

            // (Optional) set click listener for history item:
            // itemView.setOnClickListener { /* open details, show dialog, etc. */ }
        }
    }
}
