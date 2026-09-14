package com.example.donatedrop.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.donatedrop.R
import com.example.donatedrop.adapters.HistoryAdapter
import com.example.donatedrop.databinding.FragmentHistoryBinding
import com.example.donatedrop.models.DonationHistoryItem
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var donationsAdapter: HistoryAdapter
    private lateinit var requestsAdapter: HistoryAdapter

    private var donationsListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null

    // add at class level
    private var summaryListener: ListenerRegistration? = null
    private val summaryDateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If you want options menu:
        // setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.title_history) // or "DonateDrop"
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.toolbar_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.action_notifications -> {

                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, androidx.lifecycle.Lifecycle.State.RESUMED)

        donationsAdapter = HistoryAdapter(mutableListOf()) {item ->
            showDonationDetailsDialog(item)
        }
        requestsAdapter = HistoryAdapter(mutableListOf()) { item ->
            showRequestDetailsDialog(item)
        }

        binding.rvDonations.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDonations.adapter = donationsAdapter
        binding.rvDonations.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))


        binding.rvRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRequests.adapter = requestsAdapter
        binding.rvRequests.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))

        // If you had code to populate summary based on sample data, the ViewModel already provides it.
    }

    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        startListening()
        startSummaryListener()
    }

    override fun onStop() {
        super.onStop()
        donationsListener?.remove()
        donationsListener = null
        requestsListener?.remove()
        requestsListener = null

        summaryListener?.remove()
        summaryListener = null
    }

    @UnstableApi
    private fun startListening() {
        val currentUid = uid
        if (currentUid == null) {
            Toast.makeText(requireContext(), "Sign in to see your history", Toast.LENGTH_SHORT).show()
            return
        }

        // Past Donations: where CompletedBy == currentUid
        donationsListener = db.collection("DonationHistory")
            .whereEqualTo("CompletedBy", currentUid)
            .orderBy("CompletedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    Log.e("HistoryFragment", "donations listen error: ${err.message}", err)
                    return@addSnapshotListener
                }
                val list = mutableListOf<DonationHistoryItem>()
                if (snaps != null) {
                    for (doc in snaps.documents) {
                        list.add(parseHistoryDoc(doc))
                    }
                }
                donationsAdapter.setItems(list)
            }

        // Past Requests: where Created By == currentUid (try both 'Created By' and 'createdBy')
        // Firestore doesn't support OR queries simply, so we attach two listeners and merge results.
        requestsListener = db.collection("DonationHistory")
            .whereEqualTo("Created By", currentUid)
            .orderBy("CompletedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    Log.e("HistoryFragment", "requests listen error: ${err.message}", err)
                    return@addSnapshotListener
                }
                val list = mutableListOf<DonationHistoryItem>()
                if (snaps != null) {
                    for (doc in snaps.documents) list.add(parseHistoryDoc(doc))
                }
                requestsAdapter.setItems(list)
            }

        // If your app writes createdBy (lowercase) instead of `Created By` use an additional listener:
        // (optional)
        db.collection("DonationHistory")
            .whereEqualTo("Created By", currentUid)
            .orderBy("CompletedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    Log.w("HistoryFragment", "alternate createdBy listen error: ${err.message}")
                    return@addSnapshotListener
                }
                // merge with current requestsAdapter list (avoid duplicates by id)
                val current = requestsAdapter.let { // snapshot of current items
                    (0 until it.itemCount).mapNotNull { i -> /* no direct getter; we'll overwrite below */ null }
                }
                // simpler: rebuild complete list by querying both collections server side instead of merging here
            }
    }

    private fun parseHistoryDoc(doc: DocumentSnapshot): DonationHistoryItem {
        val id = doc.id
        val bloodType = doc.getString("Blood Type") ?: ""
        val receiverName = doc.getString("Receiver's Name") ?: ""
        val responderName = doc.getString("CompletedByName")
            ?: doc.getString("Responder Name")
            ?: ""
        val contact = doc.getString("Receiver's Phone") ?: ""
        val address = doc.getString("Hospital Address") ?: ""
        val createdAt = doc.getTimestamp("Created At") ?: ""
        val completedAt = doc.getTimestamp("CompletedAt") ?: ""
        val status = doc.getString("Status") ?: "completed"
        val createdBy = doc.getString("Created By") ?: ""
        val completedBy = doc.getString("CompletedBy") ?: ""

        return DonationHistoryItem(
            id = id,
            bloodType = bloodType,
            receiverName = receiverName,
            responderName = responderName,
            contact = contact,
            address = address,
            createdAt = createdAt as Timestamp?,
            completedAt = completedAt as Timestamp?,
            status = status,
            createdBy = createdBy,
            completedBy = completedBy
        )
    }

    private fun showDonationDetailsDialog(item: DonationHistoryItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_donation_details, null)

        dialogView.findViewById<TextView>(R.id.tvReceiverName).text = "Receiver: ${item.receiverName ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.tvBloodType).text = "Blood Type: ${item.bloodType ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.tvQuantity).text = "Quantity: ${item.quantity ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.tvHospitalAddress).text = "Hospital Address: ${item.address ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.tvCompletedBy).text = "Completed By: ${item.responderName ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.tvCompletedAt).text =
            "Completed At: ${(item.completedAt ?: item.createdAt)?.toDate()?.toString() ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.tvStatus).text = "Status: ${item.status ?: "completed"}"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .show()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showRequestDetailsDialog(item: DonationHistoryItem) {
        try {
            val ctx = requireContext()
            val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_request_details, null)

            val tvReceiver = view.findViewById<TextView>(R.id.tv_req_receiver)
            val tvBlood = view.findViewById<TextView>(R.id.tv_req_blood)
            val tvQty = view.findViewById<TextView>(R.id.tv_req_quantity)
            val tvHosp = view.findViewById<TextView>(R.id.tv_req_hospital)
            val tvStatus = view.findViewById<TextView>(R.id.tv_req_status)
            val tvResponder = view.findViewById<TextView>(R.id.tv_req_responder)
            val tvDates = view.findViewById<TextView>(R.id.tv_req_dates)

            tvReceiver.text = "Receiver: ${item.receiverName?.takeIf { it.isNotBlank() } ?: "Unknown"}"
            tvBlood.text = "Blood Type: ${item.bloodType?.takeIf { it.isNotBlank() } ?: "A+"}"
            tvQty.text = "Quantity: ${item.quantity?.toString() ?: "N/A"}"
            tvHosp.text = "Hospital: ${item.address?.takeIf { it.isNotBlank() } ?: "N/A"}"

            val statusDisplay = item.status?.let { s ->
                s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } ?: "Completed"
            tvStatus.text = "Status: $statusDisplay"

            // prefer CompletedByName (or Responder Name) if present, else resolver later
            val responderDisplay = item.responderName?.takeIf { it.isNotBlank() }
                ?: item.responderName?.takeIf { it.isNotBlank() }
                ?: item.completedBy // uid fallback (we can fetch user's name if needed)
                ?: "Unknown"
            tvResponder.text = "Responder: $responderDisplay"

            // dates
            val created = item.createdAt?.toDate()?.let { d ->
                java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(d)
            } ?: "--"
            val completed = item.completedAt?.toDate()?.let { d ->
                java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(d)
            } ?: "--"
            tvDates.text = "Created: $created    Completed: $completed"

            // If the responderDisplay is a UID and you want to resolve the human name:
            if (responderDisplay.length == 28 && (item.responderName.isNullOrBlank())) {
                // looks like a uid — fetch users/{uid} -> Name/displayName
                val uid = responderDisplay
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(uid).get()
                    .addOnSuccessListener { userDoc ->
                        val resolved = userDoc?.getString("Name")
                            ?: uid
                        if (isAdded) tvResponder.text = "Responder: $resolved"
                    }
            }

            val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setView(view)
                .setCancelable(true)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()

        } catch (e: Exception) {
            android.util.Log.e("HistoryFragment", "showRequestDetailsDialog failed", e)
            Toast.makeText(requireContext(), "Unable to show details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSummaryListener() {
        val currentUid = uid
        if (currentUid == null) {
            // user not signed in -> reset UI
            binding.tvTotalCount.text = "0"
            binding.tvLastDate.text = "--"
            return
        }

        // remove previous if any
        summaryListener?.remove()
        summaryListener = null

        summaryListener = db.collection("DonationHistory")
            .whereEqualTo("CompletedBy", currentUid)
            .orderBy("CompletedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    android.util.Log.e("HistoryFragment", "summary listen error: ${err.message}", err)
                    // keep UI safe
                    binding.tvTotalCount.text = "0"
                    binding.tvLastDate.text = "--"
                    return@addSnapshotListener
                }

                if (snaps == null || snaps.isEmpty) {
                    binding.tvTotalCount.text = "0"
                    binding.tvLastDate.text = "--"
                    return@addSnapshotListener
                }

                // total count
                binding.tvTotalCount.text = snaps.size().toString()

                // latest CompletedAt is first doc because of DESC ordering
                val firstDoc = snaps.documents.firstOrNull()
                val ts = firstDoc?.getTimestamp("CompletedAt")
                val lastDateText = ts?.toDate()?.let { d -> summaryDateFormat.format(d) } ?: "--"
                binding.tvLastDate.text = lastDateText
            }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
