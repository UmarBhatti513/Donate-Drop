package com.example.donatedrop

import BloodRequestAdapter
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.donatedrop.models.CreateRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class CreatedRequests : AppCompatActivity(), BloodRequestAdapter.OnRequestActionListener {

    companion object {
        private const val TAG = "CreatedRequests"
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: BloodRequestAdapter
    private lateinit var rvRequests: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private var listenerRegistration: ListenerRegistration? = null
    private val requestUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val id = intent.getStringExtra("Req_id") ?: return
            val status = intent.getStringExtra("Status") ?: return
            val responderId = intent.getStringExtra("Responder ID")
            val responderName = intent.getStringExtra("Responder Name")

            // make sure adapter is initialized
            runOnUiThread {
                try {
                    // adapter is your BloodRequestAdapter instance
                    (rvRequests.adapter as? BloodRequestAdapter)?.updateRequestStatusOptimistic(
                        id, status, responderId, responderName
                    )
                } catch (e: Exception) {
                    Log.w("CreatedRequests", "broadcast update failed: ${e.message}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_created_requests) // confirm layout name

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = getColor(android.R.color.white)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon = null

        db = FirebaseFirestore.getInstance()

        rvRequests = findViewById(R.id.rvBloodRequests)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        // adapter must be created and set before starting listener
        adapter = BloodRequestAdapter(this, mutableListOf(), this)
        rvRequests.layoutManager = LinearLayoutManager(this)
        rvRequests.adapter = adapter
        rvRequests.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        // Swipe refresh only restarts listener
        swipeRefresh.setOnRefreshListener { restartListener() }

        // Debug test: uncomment to verify the adapter and layout render a card without Firestore
        // testPopulateDummy()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        swipeRefresh.isRefreshing = true
        startListening()
        registerReceiver(requestUpdateReceiver, IntentFilter("com.example.donatedrop.ACTION_REQUEST_UPDATED"))
    }

    override fun onStop() {
        super.onStop()
        listenerRegistration?.remove()
        listenerRegistration = null
        swipeRefresh.isRefreshing = false
        try {
            unregisterReceiver(requestUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // receiver not registered — ignore
        }
    }

    private fun startListening() {
        if (listenerRegistration != null) {
            Log.d(TAG, "Listener already running")
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            swipeRefresh.isRefreshing = false
            Toast.makeText(this, "You must be signed in to view your requests", Toast.LENGTH_SHORT).show()
            finish() // or return, depending on your UX
            return
        }
        val uid = currentUser.uid

        listenerRegistration = db.collection("Blood Requests")
            .orderBy("Created At", Query.Direction.DESCENDING)
            .whereEqualTo("Created By", uid)
            .addSnapshotListener { snapshots, error ->
                swipeRefresh.isRefreshing = false

                if (error != null) {
                    Log.e(TAG, "Firestore listen error: ${error.message}", error)
                    // Toast.makeText(this, "Listen failed: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    Log.w(TAG, "snapshots is null")
                    Toast.makeText(this, "No data (snapshots null)", Toast.LENGTH_SHORT).show()
                    adapter.setItems(emptyList())
                    return@addSnapshotListener
                }

                // Inform how many docs arrived
                val docCount = snapshots.documents.size
                Log.d(TAG, "Snapshot received, docs=${docCount}")
                Toast.makeText(this, "Loaded $docCount requests", Toast.LENGTH_SHORT).show()

                if (docCount == 0) {
                    adapter.setItems(emptyList())
                    // Optional: show an empty-state view here
                    return@addSnapshotListener
                }

                val list = mutableListOf<CreateRequest>()
                for (doc in snapshots.documents) {
                    try {
                        val id = doc.id
                        val name = doc.getString("Receiver's Name") ?: ""
                        val contact = doc.getString("Receiver's Phone") ?: ""
                        val address = doc.getString("Hospital Address") ?: ""
                        val bloodType = doc.getString("Blood Type") ?: ""
                        val notes = doc.getString("Additional Note") ?: ""
                        val createdAt = doc.getTimestamp("Created At") // may be null
                        val urgency = doc.getString("Urgency Level") ?: ""
                        val status = doc.getString("Status") ?: ""
                        val responderId = doc.getString("Responder ID") ?: ""
                        val responderName = doc.getString("Responder Name") ?: ""
                        val quantityValue = doc.get("Quantity")
                        if (quantityValue is Number) {
                            val quantity = quantityValue.toInt() // Or .toDouble(), .toLong() as needed
                            // Use the quantity
                        } else if (quantityValue is String) {
                            // This is unexpected based on the error, but good for robustness
                            try {
                                val quantity = quantityValue.toInt()
                                // Use the quantity
                            } catch (e: NumberFormatException) {
                                Log.e("FirestoreError", "Quantity string is not a valid number: $quantityValue")
                            }
                        } else {
                            Log.e("FirestoreError", "Unexpected type for Quantity field: ${quantityValue?.javaClass?.name}")
                        }

                        val quantity = ""
                        val req = CreateRequest(
                            id = id,
                            name = name,
                            contact = contact,
                            address = address,
                            bloodType = bloodType,
                            notes = notes,
                            createdAt = createdAt,
                            urgency = urgency,
                            quantityStr = quantity,
                            status = status,
                            responderId = responderId,
                            responderName = responderName,
                            createdBy = uid
                        )
                        list.add(req)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error parsing doc ${doc.id}: ${ex.message}", ex)
                    }
                }

                // Update adapter on UI thread to be safe
                runOnUiThread {
                    adapter.setItems(list)
                    Log.d(TAG, "Adapter updated, itemCount=${adapter.itemCount}")
                }
            }
    }

    private fun restartListener() {
        listenerRegistration?.remove()
        listenerRegistration = null
        swipeRefresh.isRefreshing = true
        startListening()
    }



    // Adapter callbacks
    override fun onCancel(request: CreateRequest) {
        Toast.makeText(this, "Canceled for ${request.name}", Toast.LENGTH_SHORT).show()
    }


    override fun onItemClick(request: CreateRequest) {
        // val intent = Intent(this, RequestDetailActivity::class.java)
        // intent.putExtra("requestId", request.id)
        // startActivity(intent)
    }
}
