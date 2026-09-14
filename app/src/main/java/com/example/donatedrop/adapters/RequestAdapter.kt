
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.donatedrop.R
import com.example.donatedrop.models.CreateRequest
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

class RequestAdapter(
    private val context: Context,
    private var items: MutableList<CreateRequest>,
    private val listener: OnRequestActionListener
) : RecyclerView.Adapter<RequestAdapter.RequestVH>() {

    interface OnRequestActionListener {
        fun onIgnore(request: CreateRequest)
        fun onItemClick(request: CreateRequest)
        fun onRespond(request: CreateRequest)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_request_blood, parent, false)
        return RequestVH(view)
    }

    override fun onBindViewHolder(holder: RequestVH, position: Int) {
        val req = items[position]
        holder.bind(req)
    }

    /**
     * Remove the item with requestId and return a small wrapper containing the removed item and its index
     * so caller can re-add it if needed for rollback.
     */
    data class RemovedItem(val item: CreateRequest, val index: Int)

    fun removeAndReturn(requestId: String): RemovedItem? {
        val idx = items.indexOfFirst { it.id == requestId }
        if (idx == -1) return null
        val removed = items.removeAt(idx)
        notifyItemRemoved(idx)
        return RemovedItem(removed, idx)
    }

    /** Re-insert a previously removed item at the index (for rollback) */
    fun addBackAt(index: Int, item: CreateRequest) {
        val safeIndex = index.coerceIn(0, items.size)
        items.add(safeIndex, item)
        notifyItemInserted(safeIndex)
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<CreateRequest>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    // optimistic update helper (you already had this; kept as-is)
    fun updateRequestStatusOptimistic(requestId: String, newStatus: String, responderId: String?, responderName: String?) {
        val idx = items.indexOfFirst { it.id == requestId }
        if (idx == -1) return

        val old = items[idx]
        val updated = old.copy(
            status = newStatus,
            responderId = responderId ?: (old.responderId ?: ""),
            responderName = responderName ?: (old.responderName ?: "")
        )
        items[idx] = updated
        notifyItemChanged(idx)
    }

    fun addItem(item: CreateRequest) {
        items.add(0, item)
        notifyItemInserted(0)
    }

    inner class RequestVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBloodType: TextView = itemView.findViewById(R.id.tvBloodType)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvRequestTitle)
        private val tvForName: TextView = itemView.findViewById(R.id.tvForName)
        private val tvHospital: TextView = itemView.findViewById(R.id.tvHospital)
        private val tvNeededBy: TextView = itemView.findViewById(R.id.tvNeededBy)
        private val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        private val tvUrgencyTag: TextView = itemView.findViewById(R.id.tvUrgencyTag)
        private val tvStatus: TextView = itemView.findViewById(R.id.req_status)
        private val btnignore: Button = itemView.findViewById(R.id.ignorebtn)
        private val btnrespond: Button = itemView.findViewById(R.id.btnrespond)

        fun bind(req: CreateRequest) {
            // format createdAt to date string
            val timestamp = req.createdAt
            if (timestamp != null) {
                val date = timestamp.toDate()
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                val formattedDate = sdf.format(date)
                tvNeededBy.text = formattedDate
            } else {
                tvNeededBy.text = "Unknown"
            }

            tvBloodType.text = req.bloodType.ifEmpty { "A+" }
            tvTitle.text = "Blood Needed"
            tvForName.text = "for ${req.name.ifEmpty { "Unknown" }}"
            tvHospital.text = req.address
            tvPhone.text = req.contact
            tvUrgencyTag.text = req.urgency

            // Determine current user
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

            // Normalize status safely (handle null/missing)
            val statusRaw = req.status ?: (req.status ?: "") // fallback if null
            val statusNorm = statusRaw.trim().lowercase(Locale.getDefault())


            if (statusRaw.isEmpty()) {
                tvStatus.visibility = View.GONE
            } else {
                tvStatus.visibility = View.VISIBLE
                val statusNorm = statusRaw.lowercase(Locale.getDefault())
                val display = statusNorm.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                tvStatus.text = display

                val colorRes = when (statusNorm) {
                    "open" -> R.color.primary_red
                    "pending" -> R.color.amber      // Add this in colors.xml if missing
                    "completed" -> R.color.success_green
                    else -> R.color.textSecondary
                }
                tvStatus.setTextColor(ContextCompat.getColor(itemView.context, colorRes))
            }
            // Set button / ignore visibility based on status & responder
            when (statusNorm) {
                "open" -> {
                    btnrespond.isEnabled = true
                    btnrespond.text = "Respond"
                    btnignore.visibility = View.VISIBLE
                }
                "pending" -> {
                    // hide ignore for pending requests
                    btnignore.visibility = View.GONE

                    if (!req.responderId.isNullOrEmpty() && req.responderId == currentUid) {
                        // current user is the responder -> show Complete
                        btnrespond.isEnabled = true
                        btnrespond.text = "Complete"
                    } else {
                        // someone else claimed -> show Pending and disable respond
                        btnrespond.isEnabled = false
                        btnrespond.text = "Pending"
                    }
                }
                "completed" -> {
                    btnrespond.isEnabled = false
                    btnrespond.text = "Completed"
                    btnignore.visibility = View.GONE
                }
                else -> {
                    // fallback
                    btnrespond.isEnabled = true
                    btnrespond.text = "Respond"
                    btnignore.visibility = View.VISIBLE
                }
            }

            // click handlers delegate to fragment
            itemView.setOnClickListener { listener.onItemClick(req) }
            btnignore.setOnClickListener { listener.onIgnore(req) }
            btnrespond.setOnClickListener { listener.onRespond(req) }
        }
    }
}
