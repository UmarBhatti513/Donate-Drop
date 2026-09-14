package com.example.donatedrop.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.donatedrop.R
import com.example.donatedrop.models.Request

class AdminRequestAdapter(
    private var items: List<Request>,
    private val onItemClick: (Request) -> Unit
) : RecyclerView.Adapter<AdminRequestAdapter.RequestVH>() {

    inner class RequestVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRequester: TextView = itemView.findViewById(R.id.tvRequester)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(request: Request) {
            tvRequester.text = request.createdBy ?: "Unknown"
            val details = buildString {
                appendLine("Blood Type: ${request.bloodType ?: "-"}")
                appendLine("Quantity: ${request.units ?: "-"}")
                if (!request.address.isNullOrEmpty()) appendLine(" · ${request.address}")
                if (!request.phone.isNullOrEmpty()) appendLine(" · ${request.phone}")
            }.trimEnd()
            tvDetails.text = details
            tvStatus.text = request.status ?: "N/A"

            itemView.setOnClickListener { onItemClick(request) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_requests, parent, false)
        return RequestVH(view)
    }

    override fun onBindViewHolder(holder: RequestVH, position: Int) {
        holder.bind(items[position])
    }


    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<Request>) {
        items = newList
        notifyDataSetChanged()
    }
}