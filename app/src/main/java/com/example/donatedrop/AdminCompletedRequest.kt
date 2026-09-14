package com.example.donatedrop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.donatedrop.adapters.HistoryAdapter
import com.example.donatedrop.models.DonationHistoryItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class AdminCompletedRequest : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    private val firestore = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null
    private val TAG = "CompletedRequests"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin_completed_request)

        recyclerView = findViewById(R.id.rvCompleted) // or R.id.rvRequests if you reused the id
        progressBar = findViewById(R.id.progressBarCompleted) // match your XML
        tvEmpty = findViewById(R.id.tvEmpty)

        adapter = HistoryAdapter(mutableListOf()) { item ->
            showDonationDetailsDialogActivity(item)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fetchCompletedRealtime()
    }

    @OptIn(UnstableApi::class)
    private fun fetchCompletedRealtime() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        val collectionRef = firestore.collection("DonationHistory")
        // If your status value is different ("completed", "Completed", etc.) change the string below
        listenerRegistration = collectionRef
            .whereEqualTo("Status", "completed")
            .orderBy("CompletedAt", Query.Direction.DESCENDING) // if you don't have this field, remove orderBy
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    // Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "listen error", error)
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    adapter.setItems(emptyList())
                    tvEmpty.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                // Map documents to your DonationHistoryItem model (defensive about missing fields)
                val list = snapshot.documents.map { doc ->
                    Log.d(TAG, "doc ${doc.id} -> ${doc.data}") // debug: shows exact keys
                    val id = doc.id
                    val bloodType = doc.getString("Blood Type") ?: ""
                    val receiverName = doc.getString("Receiver's Name") ?: ""
                    val responderName = doc.getString("Responder Name") ?: ""
                    val contact = doc.getString("Receiver's Phone") ?: ""
                    val address = doc.getString("Hospital Address") ?: ""
                    val createdAt = doc.getTimestamp("Created At") ?: ""
                    val completedAt = doc.getTimestamp("CompletedAt") ?: ""
                    val status = doc.getString("Status") ?: "Completed"
                    val quantity = doc.get("Quantity").toString()
                    val createdBy = doc.getString("Created By")
                    val completedBy = doc.getString("CompletedByName")

                    DonationHistoryItem(
                        id = id,
                        bloodType = bloodType,
                        receiverName = receiverName,
                        responderName = responderName,
                        contact = contact,
                        address = address,
                        createdAt = createdAt as Timestamp?,
                        completedAt = completedAt as Timestamp?,
                        status = status,
                        quantity = quantity,
                        createdBy = createdBy,
                        completedBy = completedBy
                    )
                }

                adapter.setItems(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun showDonationDetailsDialogActivity(item: DonationHistoryItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_donation_details, null)

        dialogView.findViewById<TextView>(R.id.tvReceiverName).text =
            "Receiver: ${item.receiverName.ifBlank { "N/A" }}"

        dialogView.findViewById<TextView>(R.id.tvBloodType).text =
            "Blood Type: ${item.bloodType.ifBlank { "N/A" }}"

        dialogView.findViewById<TextView>(R.id.tvQuantity).text =
            "Quantity: ${item.quantity?.ifBlank { "N/A" }}"

        dialogView.findViewById<TextView>(R.id.tvHospitalAddress).text =
            "Hospital Address: ${item.address.ifBlank { "N/A" }}"

        dialogView.findViewById<TextView>(R.id.tvCompletedBy).text =
            "Completed By: ${item.responderName.ifBlank { "N/A" }}"

        val completedAtText = (item.completedAt ?: item.createdAt)?.toDate()?.toString() ?: "N/A"

        dialogView.findViewById<TextView>(R.id.tvCompletedAt).text = "Completed At: $completedAtText"

        dialogView.findViewById<TextView>(R.id.tvStatus).text =
            "Status: ${item.status?.ifBlank { "completed" } ?: "completed"}"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .show()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }

}