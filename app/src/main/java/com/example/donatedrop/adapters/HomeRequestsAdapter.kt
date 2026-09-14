package com.example.donatedrop.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.donatedrop.R
import com.example.donatedrop.models.CreateRequest
import com.google.firebase.Timestamp
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeRequestsAdapter(
    private val items: MutableList<CreateRequest>,
    private val listener: RequestActionListener
) : RecyclerView.Adapter<HomeRequestsAdapter.VH>() {

    interface RequestActionListener {
        fun onRespond(request: CreateRequest)
    }

    fun setItems(newItems: List<CreateRequest>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.blood_request_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], listener)
    }

    override fun getItemCount(): Int = items.size

    class VH (itemView: View): RecyclerView.ViewHolder(itemView) {
        private val tvBloodType = itemView.findViewById<TextView>(R.id.tv_blood_type)
        private val tvLocation = itemView.findViewById<TextView>(R.id.tv_location_distance)
        private val tvTime = itemView.findViewById<TextView>(R.id.tv_time_ago)
        private val btnRespond = itemView.findViewById<Button>(R.id.btn_respond)


        // Change these ids if your blood_request_item.xml uses other ids

        fun bind(m: CreateRequest, listener: RequestActionListener) {
            // Populate views (if a particular id is missing, change it to the id in your layout)
            val bloodText = if (m.bloodType.isNullOrBlank()) "Blood Needed" else "${m.bloodType} Needed"
            tvBloodType?.text = bloodText

            tvLocation?.text = if (m.address.isNullOrBlank()) "Unknown" else m.address

            // format createdAt
            val timeAgoStr = formatTimeAgo(m.createdAt)
            val urgencyPrefix = if (m.urgency.isNullOrBlank()) "" else "${m.urgency} - "
            tvTime?.text = urgencyPrefix + timeAgoStr

            btnRespond?.setOnClickListener { listener.onRespond(m) }
        }
        private fun formatTimeAgo(ts: Timestamp?): String {
            if (ts == null) return ""
            val now = System.currentTimeMillis()
            val then = ts.toDate().time
            val diffMs = now - then
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
            val days = TimeUnit.MILLISECONDS.toDays(diffMs)

            return when {
                minutes < 1 -> "just now"
                minutes < 60 -> "$minutes min${if (minutes > 1) "s" else ""} ago"
                hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
                days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
                else -> {
                    // fallback formatted date
                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    sdf.format(ts.toDate())
                }
            }
        }
    }

}