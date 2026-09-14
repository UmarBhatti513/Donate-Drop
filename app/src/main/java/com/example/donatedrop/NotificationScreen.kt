package com.example.donatedrop

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.donatedrop.adapters.NotificationAdapter
import com.example.donatedrop.databinding.ActivityNotificationScreenBinding
import com.example.donatedrop.models.Notification
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class NotificationScreen : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationScreenBinding
    private val db = FirebaseFirestore.getInstance()

    private var listenerTargets: ListenerRegistration? = null
    private var listener: ListenerRegistration? = null
    private lateinit var adapter: NotificationAdapter
    private val items = mutableListOf<Notification>()
    private val mergedMap = mutableMapOf<String, Notification>()
    private val TAG = "NotificationScreen"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Notifications"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = NotificationAdapter(items) { notif ->
            // on click: mark read and optionally open related screen
            markSingleRead(notif.id)
        }

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.rvNotifications.adapter = adapter

        binding.btnMarkAllRead.setOnClickListener{
            markAllRead()
        }

    }

    override fun onStart() {
        super.onStart()
        startListening()
    }

    override fun onStop() {
        super.onStop()
        listenerTargets?.remove()
        listener?.remove()
        listener = null
        listenerTargets = null
        mergedMap.clear()
        items.clear()
    }

    private fun startListening() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, "startListening - uid = $uid")
        if (uid == null) {
            adapter.setItems(emptyList())
            binding.tvEmpty.visibility = View.VISIBLE
            Log.d(TAG, "User not signed in -> no notifications")
            return
        }

        listener?.remove()

        val query = db.collection("Notifications")
            .whereEqualTo("to", uid)
            .whereEqualTo("read", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)

        // quick debug: try a one-time get() to print results / errors in Logcat
        query.get()
            .addOnSuccessListener { snap ->
                Log.d(TAG, "DEBUG get(): docs=${snap.size()}")
                for (d in snap.documents) {
                    Log.d(TAG, "DEBUG doc ${d.id} -> ${d.data}")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "DEBUG query.get() failed: ${e.message}", e)
            }

        listener = query.addSnapshotListener { snaps, err ->
            if (err != null) {
                Log.e(TAG, "listen error: ${err.message}", err)
                // If it's an index or permission error, the message will tell you
                runOnUiThread {
                    Toast.makeText(this, "Failed to listen notifications: ${err.message}", Toast.LENGTH_LONG).show()
                    adapter.setItems(emptyList())
                    binding.tvEmpty.visibility = View.VISIBLE
                }
                return@addSnapshotListener
            }

            if (snaps == null || snaps.isEmpty) {
                Log.d(TAG, "snapshot empty or null")
                runOnUiThread {
                    adapter.setItems(emptyList())
                    binding.tvEmpty.visibility = View.VISIBLE
                }
                return@addSnapshotListener
            }

            val list = mutableListOf<Notification>()
            for (doc in snaps.documents) {
                try {
                    Log.d(TAG, "parsing doc ${doc.id} -> ${doc.data}")
                    val id = doc.id
                    val to = doc.getString("to") ?: ""
                    val fromId = doc.getString("fromId") ?: ""
                    val fromName = doc.getString("fromName") ?: ""
                    val requestId = doc.getString("requestId") ?: ""
                    val message = doc.getString("message") ?: ""
                    val type = doc.getString("type") ?: ""
                    val createdAt = doc.getTimestamp("createdAt")
                    val read = doc.getBoolean("read") ?: false
                    val meta = doc.get("meta") as? Map<String, Any>

                    list.add(Notification(id, to, fromId, fromName, requestId, message, type, createdAt, read, meta))
                } catch (ex: Exception) {
                    Log.e(TAG, "parse error ${doc.id}: ${ex.message}", ex)
                }
            }

            runOnUiThread {
                Log.d(TAG, "updating adapter with ${list.size} items")
                adapter.setItems(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }


    private fun markAllRead() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Sign in to mark notifications", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = user.uid

        // Query current user's unread notifications (or all notifications if you prefer)
        db.collection("Notifications")
            .whereEqualTo("to", uid)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    // nothing to mark
                    Toast.makeText(this, "No unread notifications", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.update(doc.reference, "read", true)
                    // optional: store when it was read
                    batch.update(doc.reference, "readAt", Timestamp.now())
                }

                batch.commit()
                    .addOnSuccessListener {
                        // listener listens to unread only, so the list will automatically clear
                        Toast.makeText(this, "All notifications marked read", Toast.LENGTH_SHORT).show()
                        adapter.setItems(emptyList()) // optimistic UI change (listener will also reflect)
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("NotificationActivity", "markAllRead failed: ${e.message}", e)
                        Toast.makeText(this, "Failed to mark read: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationActivity", "Failed to fetch notifications: ${e.message}", e)
                Toast.makeText(this, "Failed to fetch notifications: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun markSingleRead(notificationId: String) {
        if (notificationId.isBlank()) return
        val ref = db.collection("Notifications").document(notificationId)
        ref.update(mapOf("read" to true, "readAt" to Timestamp.now()))
            .addOnSuccessListener {
                // adapter will update from listener since we watch unread notifications
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationActivity", "markSingleRead failed: ${e.message}", e)
            }
    }

}

