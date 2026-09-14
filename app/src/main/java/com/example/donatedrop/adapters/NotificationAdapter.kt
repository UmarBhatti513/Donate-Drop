package com.example.donatedrop.adapters

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.donatedrop.R
import com.example.donatedrop.models.Notification
import com.google.android.material.chip.Chip
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationAdapter(
    private var items: MutableList<Notification> = mutableListOf(),
    private val onClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.Holder>() {

    fun setItems(newItems: List<Notification>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addItem(item: Notification) {
        items.add(0, item)
        notifyItemInserted(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val chipBadge: Chip? = itemView.findViewById(R.id.chipBadge)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTimestampPriority)

        private val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

        fun bind(n: Notification) {
            // Title: simple mapping by type
            val title = when (n.type.lowercase(Locale.getDefault())) {
                "claimed", "responded", "respond" -> "Request responded"
                "completed" -> "Request completed"
                else -> "Notification"
            }
            tvTitle.text = title

            // subtitle -> message
            tvSubtitle.text = n.message
            tvSubtitle.ellipsize = TextUtils.TruncateAt.END
            tvSubtitle.maxLines = 2

            // badge (blood type) if available
            val bloodType = (n.meta?.get("bloodType") as? String) ?: ""
            if (!bloodType.isNullOrBlank() && chipBadge != null) {
                chipBadge.text = bloodType
                chipBadge.isVisible = true
            } else {
                chipBadge?.isVisible = false
            }

            // timestamp
            val ts: Timestamp? = n.createdAt as Timestamp?
            tvTime.text = ts?.toDate()?.let { dateFormat.format(it) } ?: ""

            // icon by type (use your drawables; replace with your icons)
            val iconRes = when (n.type.lowercase(Locale.getDefault())) {
                "claimed", "responded", "respond" -> R.drawable.ic_personn // substitute with claim icon
                "completed" -> R.drawable.check_circle
                else -> R.drawable.notification
            }
            ivIcon.setImageResource(iconRes)

            // unread marker: use ivStar tint or visibility; here we show/hide star

            // click -> notify caller
            itemView.setOnClickListener { onClick(n) }
        }
    }
}
