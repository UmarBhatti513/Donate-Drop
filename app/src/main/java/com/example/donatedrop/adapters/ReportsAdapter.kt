package com.example.donatedrop.adapters
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.donatedrop.databinding.ItemReportBinding
import com.example.donatedrop.models.Report

class ReportsAdapter(
    private val items: List<Report>,
    private val onClick: (Report) -> Unit
) : RecyclerView.Adapter<ReportsAdapter.VH>() {

    inner class VH(val binding: ItemReportBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(r: Report) {
            binding.reportTitle.text = r.title
            binding.reportSummary.text = r.summary
            binding.view.setOnClickListener { onClick(r) }
            binding.root.setOnClickListener { onClick(r) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
