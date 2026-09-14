
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

class BloodRequestAdapter(
    private val context: Context,
    private var items: MutableList<CreateRequest>,
    private val listener: OnRequestActionListener
) : RecyclerView.Adapter<BloodRequestAdapter.RequestVH>() {

    interface OnRequestActionListener {
        fun onCancel(request: CreateRequest)
        fun onItemClick(request: CreateRequest)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_blood_request, parent, false)
        return RequestVH(view)
    }

    override fun onBindViewHolder(holder: RequestVH, position: Int) {
        val req = items[position]
        holder.bind(req)
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<CreateRequest>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addItem(item: CreateRequest) {
        items.add(0, item)
        notifyItemInserted(0)
    }

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

    inner class RequestVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBloodType: TextView = itemView.findViewById(R.id.tvBloodType)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvRequestTitle)
        private val tvForName: TextView = itemView.findViewById(R.id.tvForName)
        private val tvHospital: TextView = itemView.findViewById(R.id.tvHospital)
        private val tvNeededBy: TextView = itemView.findViewById(R.id.tvNeededBy)
        private val tvUrgencyTag: TextView = itemView.findViewById(R.id.tvUrgencyTag)
        private val btncancel: Button = itemView.findViewById(R.id.btncancel)
        private val tvstatus: TextView = itemView.findViewById(R.id.status_req)

        fun bind(req: CreateRequest) {
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
            // if you use notes for needed-by, or change accordingly
            tvUrgencyTag.text = req.urgency

            val raw = (req.status ?: req.status ?: "").toString()
            val statusNorm = raw.trim().lowercase(Locale.getDefault())
            if (statusNorm.isBlank()) {
                tvstatus.visibility = View.GONE
            } else {
                tvstatus.visibility = View.VISIBLE
                // friendly display: capitalize first letter
                val display = statusNorm.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

                // if pending and we have a responderName, show it
                val displayWithResponder = if (statusNorm == "pending" && !req.responderName.isNullOrBlank()) {
                    "$display — Responded by ${req.responderName}"
                } else if (statusNorm == "completed" && !req.responderName.isNullOrBlank()) {
                    "$display by ${req.responderName}"
                } else {
                    display
                }

                tvstatus.text = displayWithResponder

                // color by status
                val colorRes = when (statusNorm) {
                    "open" -> R.color.primary_red         // or a neutral color you prefer
                    "pending" -> R.color.amber            // add amber to colors.xml if missing
                    "completed" -> R.color.success_green
                    else -> R.color.textSecondary
                }
                tvstatus.setTextColor(ContextCompat.getColor(itemView.context, colorRes))
            }

            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val isOwner = req.createdBy == currentUid || req.createdBy == (req.createdBy ?: "")

            when (statusNorm) {
                "open" -> {
                    // allow owner to cancel
                    if (isOwner) {
                        btncancel.visibility = View.VISIBLE
                        btncancel.isEnabled = true
                        btncancel.text = "Cancel" // ensure string resource exists
                    } else {
                        btncancel.visibility = View.GONE
                    }
                }
                "pending" -> {
                    // owner cannot cancel while pending (in progress)
                    if (isOwner) {
                        btncancel.visibility = View.VISIBLE
                        btncancel.isEnabled = false
                        btncancel.text = "In progress"
                    } else {
                        btncancel.visibility = View.GONE
                    }
                }
                "completed" -> {
                    // hide cancel once completed
                    btncancel.visibility = View.GONE
                }
                else -> {
                    // fallback: same as open
                    if (isOwner) {
                        btncancel.visibility = View.VISIBLE
                        btncancel.isEnabled = true
                        btncancel.text = "Cancel"
                    } else {
                        btncancel.visibility = View.GONE
                    }
                }
            }

            itemView.setOnClickListener { listener.onItemClick(req) }
            btncancel.setOnClickListener { listener.onCancel(req) }
        }
    }
}
