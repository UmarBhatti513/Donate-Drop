package com.example.donatedrop.ui.requests

import RequestAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.donatedrop.CreatedRequests
import com.example.donatedrop.R
import com.example.donatedrop.databinding.FragmentRequestsBinding
import com.example.donatedrop.models.CreateRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Locale

class RequestsFragment : Fragment(), RequestAdapter.OnRequestActionListener {

    private var _binding: FragmentRequestsBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: RequestAdapter
    private lateinit var RequestsRv: RecyclerView
    private lateinit var swiperefresh: SwipeRefreshLayout
    private var listenerRegistration: ListenerRegistration? = null

    // optional ViewModel (below file). Not strictly required but created if you want to add state later.
    private lateinit var viewModel: RequestsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enable options menu inside fragment (so onCreateOptionsMenu / onOptionsItemSelected are called)
        setHasOptionsMenu(true)


    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        viewModel = ViewModelProvider(this).get(RequestsViewModel::class.java)
        _binding = FragmentRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.title_blood_request) // or "DonateDrop"
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.menu_requests, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.action_filter -> {
                        showFilterChoicesDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        db = FirebaseFirestore.getInstance()

        // RecyclerView
        adapter = RequestAdapter(requireContext(), mutableListOf(), this)
        RequestsRv = binding.Requestsrv

        RequestsRv.layoutManager = LinearLayoutManager(requireContext())
        RequestsRv.adapter = adapter
        RequestsRv.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))

        // Swipe refresh
        swiperefresh = binding.swipeRefresh
        swiperefresh.setOnRefreshListener { restartListener() }
    }

    override fun onStart() {
        super.onStart()
        binding.run { swiperefresh.isRefreshing = true }
        startListeningOtherUsers()
    }

    override fun onStop() {
        super.onStop()
        listenerRegistration?.remove()
        listenerRegistration = null
        swiperefresh.isRefreshing = false
    }

    private fun startListeningOtherUsers() {
        if (listenerRegistration != null) {
            Log.d("RequestsFragment", "Listener already running")
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Sign in to see requests", Toast.LENGTH_SHORT).show()
            swiperefresh.isRefreshing = false
            return
        }
        val uid = currentUser.uid

        // Server-side filter: returns docs where Created By != uid
        listenerRegistration = db.collection("Blood Requests")
            .whereNotEqualTo("Created By", uid)           // filter out own docs
            .orderBy("Created By")                        // required when using not-equal
            .orderBy("Created At", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                // ensure spinner stops
                swiperefresh.isRefreshing = false

                if (error != null) {
                    // optionally log error
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) {
                    adapter.setItems(emptyList())
                    return@addSnapshotListener
                }

                val list = mutableListOf<CreateRequest>()
                for (doc in snapshots.documents) {
                    try {
                        val ignoredBy = doc.get("IgnoredBy") as? List<*>
                        if (ignoredBy != null && ignoredBy.any { it == uid }) {
                            continue
                        }
                        val id = doc.id
                        val name = doc.getString("Receiver's Name") ?: ""
                        val contact = doc.getString("Receiver's Phone") ?: ""
                        val address = doc.getString("Hospital Address") ?: ""
                        val bloodType = doc.getString("Blood Type") ?: ""
                        val notes = doc.getString("Additional Note") ?: ""
                        val createdAt = doc.getTimestamp("Created At")
                        val createdBy = doc.getString("Created By") ?: ""
                        val urgency = doc.getString("Urgency Level") ?: ""
                        val status = doc.getString("Status") ?: ""
                        val responderId = doc.getString("Responder ID") ?: ""
                        val responderName = doc.getString("Responder Name") ?: ""
                        val quantityValue = doc.get("Quantity")
                        val quantityStr = when (quantityValue) {
                            is Number -> quantityValue.toInt().toString()
                            is String -> try { quantityValue.toInt().toString() } catch (e: NumberFormatException) { quantityValue }
                            else -> ""
                        }

                        list.add(
                            CreateRequest(
                                id = id,
                                name = name,
                                contact = contact,
                                address = address,
                                bloodType = bloodType,
                                notes = notes,
                                createdAt = createdAt,
                                urgency = urgency,
                                quantityStr = quantityStr,
                                status = status,
                                responderId = responderId,
                                responderName = responderName,
                                createdBy = createdBy
                            )
                        )
                    } catch (ex: Exception) {
                        Log.e("RequestsFragment", "Error parsing doc ${doc.id}: ${ex.message}", ex)
                    }
                }

                // update adapter on UI thread
                activity?.runOnUiThread {
                    adapter.setItems(list)
                }
            }
    }

    private fun restartListener() {
        listenerRegistration?.remove()
        listenerRegistration = null
        swiperefresh.isRefreshing = true
        startListeningOtherUsers()
    }

    // RequestAdapter.OnRequestActionListener callbacks
    override fun onItemClick(request: CreateRequest) {
        // implement navigation or details view
        Toast.makeText(requireContext(), "Clicked ${request.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onRespond(request: CreateRequest) {
        showContactDialog(request)
    }

    override fun onIgnore(request: CreateRequest) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please sign in to ignore", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = currentUser.uid
        val docRef = db.collection("Blood Requests").document(request.id)

        // Optimistic removal: remove and keep removed item for rollback
        val removed = adapter.removeAndReturn(request.id) // helper (add to RequestAdapter)
        // If adapter helper returned null, still try to update Firestore; UI will update on snapshot
        docRef.update("IgnoredBy", FieldValue.arrayUnion(uid))
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Ignored", Toast.LENGTH_SHORT).show()
                // nothing else — listener will keep ignoring this doc for this user
            }
            .addOnFailureListener { e ->
                Log.e("RequestsFragment", "Failed to ignore request: ${e.message}", e)
                Toast.makeText(requireContext(), "Ignore failed: ${e.message}", Toast.LENGTH_LONG).show()
                // rollback: add item back if we removed it
                if (removed != null) {
                    adapter.addBackAt(removed.index, removed.item)
                } else {
                    // fallback: refresh listener to restore full list
                    restartListener()
                }
            }
    }

    private fun showContactDialog(request: CreateRequest) {
        try {
            val ctx = requireContext()
            val inflater = LayoutInflater.from(ctx)
            val view = inflater.inflate(R.layout.dialogue_contact, null)

            val tvName = view.findViewById<TextView>(R.id.dialog_tv_name)
            val tvPhone = view.findViewById<TextView>(R.id.dialog_tv_phone)
            val btnCall = view.findViewById<Button>(R.id.dialog_btn_call)
            val btnClose = view.findViewById<Button>(R.id.dialog_btn_close)
            val btnClaimOrComplete = view.findViewById<Button?>(R.id.dialog_btn_claim)

            tvName.text = request.name.takeIf { it.isNotBlank() } ?: "Unknown"
            tvPhone.text = request.contact.takeIf { it.isNotBlank() } ?: "No phone number"

            val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setView(view)
                .setCancelable(true)
                .create()

            // Dialer
            btnCall.setOnClickListener {
                val raw = request.contact?.trim().orEmpty()
                if (raw.isBlank()) {
                    Toast.makeText(ctx, "No phone number available", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val sanitized = raw.replace(Regex("[^+0-9]"), "")
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                intent.data = android.net.Uri.parse("tel:$sanitized")
                startActivity(intent)
            }

            btnClose.setOnClickListener { dialog.dismiss() }

            // Decide dialog buttons based on status & current user
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val statusNorm = (request.status ?: "").trim().lowercase(Locale.getDefault())
            val isResponder = !request.responderId.isNullOrBlank() && request.responderId == currentUid

            // Setup default: hide claim/complete button
            btnClaimOrComplete?.visibility = View.GONE

            when (statusNorm) {
                "open" -> {
                    btnClaimOrComplete?.visibility = View.VISIBLE
                    btnClaimOrComplete?.isEnabled = true
                    btnClaimOrComplete?.text = "Respond"
                    btnClaimOrComplete?.setOnClickListener {
                        btnClaimOrComplete.isEnabled = false

                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser == null) {
                            Toast.makeText(ctx, "Sign in to respond", Toast.LENGTH_SHORT).show()
                            btnClaimOrComplete.isEnabled = true
                            return@setOnClickListener
                        }
                        val uid = currentUser.uid
                        val quickName = currentUser.displayName?.takeIf { it.isNotBlank() }
                            ?: currentUser.email ?: currentUser.phoneNumber ?: "Responder"

                        try {
                            adapter.updateRequestStatusOptimistic(request.id, "pending", uid, quickName)
                        } catch (e: Exception) {
                            Log.w("RequestsFragment", "optimistic update failed: ${e.message}")
                        }

                        claimRequest(request.id) { success, message, resolvedName ->
                            activity?.runOnUiThread {
                                if (success) {
                                    if (!resolvedName.isNullOrBlank() && resolvedName != quickName) {
                                        try {
                                            adapter.updateRequestStatusOptimistic(request.id, "pending", uid, resolvedName)
                                        } catch (e: Exception) {
                                            Log.w("RequestsFragment", "final adapter update failed: ${e.message}")
                                        }
                                    }
                                    Toast.makeText(ctx, "Responded", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                } else {
                                    try {
                                        adapter.updateRequestStatusOptimistic(request.id, "open", null, null)
                                    } catch (ex: Exception) {
                                        Log.w("RequestsFragment", "rollback failed: ${ex.message}")
                                    }
                                    btnClaimOrComplete.isEnabled = true
                                    Toast.makeText(ctx, "Respond failed: $message", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }

                "pending" -> {
                    if (isResponder) {
                        btnClaimOrComplete?.visibility = View.VISIBLE
                        btnClaimOrComplete?.isEnabled = true
                        btnClaimOrComplete?.text = "Complete"

                        // hide close button (if you want only complete & call)
                        val btnCancelInDialog = view.findViewById<Button?>(R.id.dialog_btn_close)
                        btnCancelInDialog?.visibility = View.GONE

                        btnClaimOrComplete?.setOnClickListener {
                            btnClaimOrComplete.isEnabled = false

                            val uid = currentUid
                            val currentNameQuick = FirebaseAuth.getInstance().currentUser?.displayName
                                ?: FirebaseAuth.getInstance().currentUser?.email ?: "Responder"
                            try {
                                adapter.updateRequestStatusOptimistic(request.id, "completed", uid, currentNameQuick)
                            } catch (e: Exception) {
                                Log.w("RequestsFragment", "optimistic complete update failed: ${e.message}")
                            }

                            completeRequest(request.id) { success, message ->
                                activity?.runOnUiThread {
                                    if (success) {
                                        Toast.makeText(ctx, "Marked completed", Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                    } else {
                                        try {
                                            adapter.updateRequestStatusOptimistic(request.id, "pending", request.responderId, request.responderName)
                                        } catch (ex: Exception) {
                                            Log.w("RequestsFragment", "rollback failed: ${ex.message}")
                                        }
                                        btnClaimOrComplete.isEnabled = true
                                        Toast.makeText(ctx, "Complete failed: $message", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    } else {
                        btnClaimOrComplete?.visibility = View.GONE
                    }
                }

                "completed" -> {
                    btnClaimOrComplete?.visibility = View.GONE
                }

                else -> {
                    btnClaimOrComplete?.visibility = View.VISIBLE
                    btnClaimOrComplete?.text = "Complete"
                    btnClaimOrComplete?.setOnClickListener {
                        btnClaimOrComplete.isEnabled = false
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser == null) {
                            Toast.makeText(ctx, "Sign in to complete", Toast.LENGTH_SHORT).show()
                            btnClaimOrComplete.isEnabled = true
                            return@setOnClickListener
                        }
                        val uid = currentUser.uid
                        val quickName = currentUser.displayName?.takeIf { it.isNotBlank() }
                            ?: currentUser.email ?: currentUser.phoneNumber ?: "Responder"

                        try {
                            adapter.updateRequestStatusOptimistic(request.id, "pending", uid, quickName)
                        } catch (e: Exception) { /* ignore */ }

                        claimRequest(request.id) { success, message, resolvedName ->
                            activity?.runOnUiThread {
                                if (success) {
                                    if (!resolvedName.isNullOrBlank() && resolvedName != quickName) {
                                        try { adapter.updateRequestStatusOptimistic(request.id, "pending", uid, resolvedName) } catch(_:Exception){}
                                    }
                                    Toast.makeText(ctx, "Claimed", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                } else {
                                    try { adapter.updateRequestStatusOptimistic(request.id, "open", null, null) } catch(_:Exception){}
                                    btnClaimOrComplete.isEnabled = true
                                    Toast.makeText(ctx, "Claim failed: $message", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e("RequestsFragment", "showContactDialog failed", e)
            Toast.makeText(requireContext(), "Unable to show contact", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showFilterChoicesDialog() {
        val screenNames = arrayOf("Your Requests")
        val activityClasses = arrayOf(
            CreatedRequests::class.java
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Open screen")
            .setItems(screenNames) { _, which ->
                val intent = Intent(requireContext(), activityClasses[which])
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
        listenerRegistration = null
        _binding = null
    }

    // inside RequestsFragment.kt (replace existing claimRequest)
    private fun claimRequest(requestId: String, onComplete: (success: Boolean, message: String, resolvedName: String?) -> Unit) {
        val docRef = db.collection("Blood Requests").document(requestId)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            onComplete(false, "Not signed in", null)
            return
        }
        val uid = currentUser.uid

        fun runClaimTransaction(resolvedName: String) {
            Log.d("claimRequest", "Running tx with Responder Name='$resolvedName' for uid=$uid")
            // Run transaction which verifies status=open and sets pending + responder info
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) throw FirebaseFirestoreException("Request not found", FirebaseFirestoreException.Code.NOT_FOUND)
                val statusRaw = snap.getString("Status") ?: ""
                val status = statusRaw.trim().lowercase(Locale.getDefault())
                if (status != "open") {
                    throw FirebaseFirestoreException("Request not open (Status='$statusRaw')", FirebaseFirestoreException.Code.ABORTED)
                }

                val updates = mapOf<String, Any>(
                    "Status" to "pending",
                    "Responder ID" to uid,
                    "Responder Name" to resolvedName,
                    "Responder At" to com.google.firebase.Timestamp.now()
                )
                tx.update(docRef, updates)
                null
            }.addOnSuccessListener {
                Log.d("claimRequest", "Transaction succeeded for requestId=$requestId")
                // Create notification for the request creator (read doc now — doc still exists)
                docRef.get().addOnSuccessListener { docSnap ->
                    val creatorId = docSnap.getString("Created By") ?: ""
                    val receiverName = docSnap.getString("Receiver's Name") ?: ""
                    val bloodType = docSnap.getString("Blood Type") ?: ""
                    val urgency = docSnap.getString("Urgency Level") ?: ""

                    if (creatorId.isNotBlank()) {
                        val resolvedResponderName = resolvedName
                        val messageText = "$resolvedResponderName has responded to your request."

                        val notif = hashMapOf<String, Any>(
                            "to" to creatorId,
                            "fromId" to uid,
                            "fromName" to resolvedResponderName,
                            "requestId" to requestId,
                            "message" to messageText,
                            "type" to "claimed",
                            "createdAt" to com.google.firebase.Timestamp.now(),
                            "read" to false,
                            "meta" to mapOf("bloodType" to bloodType, "receiverName" to receiverName, "urgency" to urgency)
                        )

                        db.collection("Notifications")
                            .add(notif)
                            .addOnSuccessListener { nref ->
                                Log.d("claimRequest", "Notification created ${nref.id} for creator=$creatorId")
                            }
                            .addOnFailureListener { e ->
                                Log.e("claimRequest", "Failed to create notification: ${e.message}", e)
                            }
                    } else {
                        Log.w("claimRequest", "Creator id blank — skipping notification creation")
                    }

                    onComplete(true, "Responded", resolvedName)
                }.addOnFailureListener { e ->
                    Log.w("claimRequest", "Failed to read doc after tx: ${e.message}", e)
                    // still treat the claim as success — but report that notification couldn't be created/read
                    onComplete(true, "Responded (notification failed)", resolvedName)
                }
            }.addOnFailureListener { e ->
                Log.e("claimRequest", "Transaction failed: ${e.message}", e)
                val msg = if (e is FirebaseFirestoreException) "Firestore error (${e.code}): ${e.message}" else "Error: ${e.message}"
                onComplete(false, msg, null)
            }
        }

        // Resolve name: prefer auth.displayName, otherwise try users/{uid}, then fallback to email/phone/"Responder"
        val authName = currentUser.displayName?.takeIf { it.isNotBlank() }
        if (!authName.isNullOrBlank()) {
            runClaimTransaction(authName)
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val nameFromProfile = userDoc?.getString("Name")
                    ?: userDoc?.getString("fullName")
                    ?: userDoc?.getString("displayName")
                val resolved = nameFromProfile?.takeIf { it.isNotBlank() }
                    ?: currentUser.email
                    ?: currentUser.phoneNumber
                    ?: "Responder"
                runClaimTransaction(resolved)
            }
            .addOnFailureListener { e ->
                val fallback = currentUser.email ?: currentUser.phoneNumber ?: "Responder"
                Log.w("claimRequest", "User profile fetch failed; using fallback: $fallback", e)
                runClaimTransaction(fallback)
            }
    }

    private fun completeRequest(requestId: String, onComplete: (success: Boolean, message: String) -> Unit) {
        val docRef = db.collection("Blood Requests").document(requestId)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            onComplete(false, "Not signed in")
            return
        }
        val uid = currentUser.uid

        docRef.get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    onComplete(false, "Request not found")
                    return@addOnSuccessListener
                }

                // collect useful fields for notification and history
                val creatorId = snap.getString("Created By") ?: ""
                val receiverName = snap.getString("Receiver's Name") ?: ""
                val bloodType = snap.getString("Blood Type") ?: ""
                val responderIdFromDoc = snap.getString("Responder ID") ?: ""
                val responderNameFromDoc = snap.getString("Responder Name") ?: ""
                val dataMap = snap.data ?: mapOf<String, Any>()

                // create a new history document reference now so we can reference it after tx
                val historyRef = db.collection("DonationHistory").document()

                // 2) Run transaction: verify pending & responder matches, write history doc, delete original
                db.runTransaction { tx ->
                    val s = tx.get(docRef)
                    if (!s.exists()) throw FirebaseFirestoreException(
                        "Request not found during tx",
                        FirebaseFirestoreException.Code.NOT_FOUND
                    )
                    val statusRaw = s.getString("Status") ?: ""
                    val status = statusRaw.trim().lowercase(Locale.getDefault())
                    val responderId = s.getString("Responder ID") ?: ""

                    if (status != "pending" || responderId != uid) {
                        throw FirebaseFirestoreException(
                            "Cannot complete: not pending or not responder",
                            FirebaseFirestoreException.Code.PERMISSION_DENIED
                        )
                    }

                    // copy doc data into historyData and augment
                    val historyData = HashMap<String, Any>(dataMap)
                    historyData["Status"] = "completed"
                    historyData["CompletedAt"] = com.google.firebase.Timestamp.now()
                    historyData["CompletedBy"] = uid
                    // prefer Responder Name from doc, fallback to responderNameFromDoc variable
                    historyData["CompletedByName"] =
                        s.getString("Responder Name") ?: responderNameFromDoc

                    // Optionally store original request id in history for lookup
                    historyData["Req_id"] = requestId

                    tx.set(historyRef, historyData)
                    tx.delete(docRef)

                    null
                }.addOnSuccessListener {
                    Log.d(
                        "completeRequest",
                        "Transaction succeeded, moved request $requestId to history ${historyRef.id}"
                    )

                    // 3) Create notification for the original creator using the read values
                    if (creatorId.isNotBlank()) {
                        val completedByName =
                            responderNameFromDoc.ifBlank { currentUser.displayName ?: uid }
                        val messageText =
                            "$completedByName has completed your request."

                        val notif = hashMapOf<String, Any>(
                            "to" to creatorId,
                            "fromId" to uid,
                            "fromName" to completedByName,
                            "requestId" to requestId,
                            "message" to messageText,
                            "type" to "completed",
                            "createdAt" to com.google.firebase.Timestamp.now(),
                            "read" to false,
                            "meta" to mapOf(
                                "bloodType" to bloodType,
                                "receiverName" to receiverName
                            )
                        )

                        db.collection("Notifications")
                            .add(notif)
                            .addOnSuccessListener { nref ->
                                Log.d(
                                    "completeRequest",
                                    "Completion notification created ${nref.id} for creator=$creatorId"
                                )
                            }
                            .addOnFailureListener { e ->
                                Log.e(
                                    "completeRequest",
                                    "Failed to create completion notification: ${e.message}",
                                    e
                                )
                            }
                    } else {
                        Log.w(
                            "completeRequest",
                            "Creator id blank — skipping completion notification"
                        )
                    }

                    onComplete(true, "Completed")
                }.addOnFailureListener { e ->
                    Log.e("completeRequest", "Transaction failed: ${e.message}", e)
                    val msg =
                        if (e is FirebaseFirestoreException) "Firestore error (${e.code}): ${e.message}" else "Error: ${e.message}"
                    onComplete(false, msg)
                }
            }
            .addOnFailureListener { e ->
                Log.e(
                    "completeRequest",
                    "Failed to read request doc before completing: ${e.message}",
                    e
                )
                onComplete(false, "Failed to read request")
            }

    }
}
