package com.example.donatedrop

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.donatedrop.adapters.AdminRequestAdapter
import com.example.donatedrop.models.Request
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class AdminRequests : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AdminRequestAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    private val firestore = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin_requests)

        recyclerView = findViewById(R.id.rvRequests)
        progressBar = findViewById(R.id.progressBar10)
        tvEmpty = findViewById(R.id.tvEmpty)

        adapter = AdminRequestAdapter(listOf()) { request ->
            showRequestActionDialog(request)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fetchRequestsRealtime()

    }

    private fun fetchRequestsRealtime() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        val collectionRef = firestore.collection("Blood Requests")
        // order by timestamp if available; else simply listen
        listenerRegistration = collectionRef
            .orderBy("Created At", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    adapter.updateList(emptyList())
                    tvEmpty.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                val list = snapshot.documents.map { doc ->
                    val id = doc.id
                    val createdBy = doc.getString("Receiver's Name") ?: ""
                    val bloodType = doc.getString("Blood Type") ?: ""
                    // units may be stored as Long (numbers)
                    val units = when {
                        doc.contains("Quantity") && doc.getLong("Quantity") != null -> doc.getLong("Quantity")!!.toInt()
                        else -> null
                    }
                    val phone = doc.getString("Receiver's Phone") ?: ""
                    val city = doc.getString("Hospital Address") ?: ""
                    val status = doc.getString("Status") ?: "N/A"

                    Request(
                        id = id,
                        createdBy = createdBy,
                        bloodType = bloodType,
                        units = units,
                        phone = phone,
                        address = city,
                        status = status
                    )
                }
                adapter.updateList(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun showRequestActionDialog(request: Request) {
        // Defensive: ensure we have an id
        if (request.id.isBlank()) {
            Toast.makeText(this, "Cannot delete: missing id", Toast.LENGTH_SHORT).show()
            return
        }

        // Build a simple detail message. You can replace with a custom view if you prefer.
        val details = buildString {
            appendLine("Requester: ${request.createdBy ?: request.createdBy ?: "N/A"}")
            appendLine("Blood Type: ${request.bloodType ?: "-"}")
            appendLine("Quantity: ${request.units ?: "-"}")
            if (!request.address.isNullOrEmpty()) appendLine("Hospital Address: ${request.address}")
            if (!request.phone.isNullOrEmpty()) appendLine("Phone: ${request.phone}")
            appendLine()
            append("Are you sure you want to delete this request?")
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Request actions")
            .setMessage(details)
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
            }
            .setPositiveButton("Delete") { d, _ ->
                // We'll perform delete with a small progress feedback.
                d.dismiss()
                performDeleteRequest(request)
            }
            .create()

        dialog.show()
    }

    private fun performDeleteRequest(request: Request) {
        // Optional: show a simple progress dialog while deleting
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(layoutInflater.inflate(R.layout.dialog_progress_simple, null)) // optional custom small spinner layout
            .setCancelable(false)
            .create()
        progressDialog.show()


        // If you don't have a custom progress layout, you can skip progressDialog and just show a Toast.
        // progressDialog.show()

        val docId = request.id
        firestore.collection("Blood Requests")
            .document(docId)
            .delete()
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Request deleted", Toast.LENGTH_SHORT).show()
                // The realtime listener will update adapter automatically.
                // If you prefer to remove optimistically:
                // adapter.updateList(adapter.items.filter { it.id != docId })
            }
            .addOnFailureListener { e ->
                // progressDialog.dismiss()
                Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
            }

    }


    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }

}