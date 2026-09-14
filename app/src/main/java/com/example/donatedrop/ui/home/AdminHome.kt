package com.example.donatedrop.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.example.donatedrop.AdminCompletedRequest
import com.example.donatedrop.AdminRequests
import com.example.donatedrop.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminHome : Fragment() {

    private val TAG = "AdminHome"
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var tvGreeting: TextView? = null
    private var tvUsersValue: TextView? = null
    private var tvRequestsValue: TextView? = null
    private var tvTotalValue: TextView? = null
    private lateinit var usersCard: View
    private lateinit var requestsCard: View
    private lateinit var completedCard: View

    private var ivAdminPhoto: ImageView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_admin_home, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvGreeting = view.findViewById(R.id.greeting)
        tvUsersValue = view.findViewById(R.id.value_users)
        tvRequestsValue = view.findViewById(R.id.value_requests)
        tvTotalValue = view.findViewById(R.id.value_total)
        ivAdminPhoto = view.findViewById(R.id.avatar)
        usersCard = view.findViewById(R.id.users_card)
        usersCard.setOnClickListener {
            val navView = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                R.id.nav_view
            )
            // the ID MUST be the same id used in your admin nav graph destination and menu item.
            navView.selectedItemId = R.id.nav_admin_users
        }
        requestsCard = view.findViewById(R.id.requests_card)
        requestsCard.setOnClickListener {
            val intent = Intent(requireContext(), AdminRequests::class.java)
            startActivity(intent)
        }
        completedCard = view.findViewById(R.id.completed_card)
        completedCard.setOnClickListener {
            val intent = Intent(requireContext(), AdminCompletedRequest::class.java)
            startActivity(intent)
        }

        // Show a quick placeholder while loading
        tvUsersValue?.text = "…"
        tvRequestsValue?.text = "…"
        tvTotalValue?.text = "…"


        loadAdminNameAndPhoto()
        loadCounts()
        loadCompletedRequestsFromDonationHistory()
    }

    @OptIn(UnstableApi::class)
    private fun loadAdminNameAndPhoto() {
        val user = auth.currentUser
        if (user == null) {
            tvGreeting?.text = getString(R.string.hello_admin)
            loadProfileImageFallback(null)
            return
        }
        val uid = user.uid

        val tryAdminDoc: (String) -> Unit = { _ ->
            db.collection("Admin").document(uid).get()
                .addOnSuccessListener { doc ->
                    val nameFromAdmin = doc.getString("Name")
                    if (!nameFromAdmin.isNullOrBlank()) {
                        tvGreeting?.text = "Hello, $nameFromAdmin"
                    }
                    // try a few common field names for profile URL
                    val photoCandidates = listOf("avatarUrl")
                    var foundUrl: String? = null
                    for (f in photoCandidates) {
                        val v = doc.getString(f)
                        if (!v.isNullOrBlank()) {
                            foundUrl = v
                            break
                        }
                    }

                    if (foundUrl != null) {
                        loadProfileImage(foundUrl)
                    } else {
                        // fallback to users collection or to FirebaseAuth
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { userDoc ->
                                val nameFromUser = userDoc.getString("Name")
                                if (!nameFromUser.isNullOrBlank() && nameFromAdmin.isNullOrBlank()) {
                                    tvGreeting?.text = "Hello, $nameFromUser"
                                }
                                // try same candidate fields on users doc
                                var found2: String? = null
                                for (f2 in photoCandidates) {
                                    val vv = userDoc.getString(f2)
                                    if (!vv.isNullOrBlank()) {
                                        found2 = vv
                                        break
                                    }
                                }
                                if (found2 != null) {
                                    loadProfileImage(found2)
                                } else {
                                    // final fallback to FirebaseUser photoUrl or email placeholder
                                    val fallbackUrl = user.photoUrl?.toString()
                                    loadProfileImageFallback(fallbackUrl)
                                }
                            }
                            .addOnFailureListener { _ ->
                                val fallbackUrl = user.photoUrl?.toString()
                                loadProfileImageFallback(fallbackUrl)
                            }
                    }
                }
                .addOnFailureListener {
                    // fallback to users collection / auth
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { userDoc ->
                            val nameFromUser = userDoc.getString("Name")
                            if (!nameFromUser.isNullOrBlank()) {
                                tvGreeting?.text = "Hello, $nameFromUser"
                            } else {
                                val fallback = user.displayName ?: user.email ?: "Admin"
                                tvGreeting?.text = "Hello, $fallback"
                            }
                            val possible = userDoc.getString("photoUrl") ?: user.photoUrl?.toString()
                            loadProfileImageFallback(possible)
                        }
                        .addOnFailureListener {
                            val fallback = user.displayName ?: user.email ?: "Admin"
                            tvGreeting?.text = "Hello, $fallback"
                            loadProfileImageFallback(user.photoUrl?.toString())
                        }
                }
        }

        // start by trying Admin doc
        tryAdminDoc(uid)
    }

    private fun loadProfileImage(url: String) {
        // prefer a secure url from Cloudinary (https). Glide will handle caching.
        if (url.isBlank()) {
            loadProfileImageFallback(null)
            return
        }
        // if url looks like a Cloudinary (or any remote URL), load with Glide
        ivAdminPhoto?.let { iv ->
            Glide.with(this)
                .load(url)
                .circleCrop()
                .into(iv)
        }
    }

    private fun loadProfileImageFallback(maybeUrl: String?) {
        if (!maybeUrl.isNullOrBlank()) {
            loadProfileImage(maybeUrl)
        } else {
            // use placeholder drawable
            ivAdminPhoto?.setImageResource(R.drawable.ic_personn)
        }
    }

    @OptIn(UnstableApi::class)
    private fun loadCounts() {
        val adminUid = "vTg0tRNCwlMU8xwcZjLIo3y5vA12"
        // 1) total users
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                val countUsers = snapshot.documents.count { it.id != adminUid }
                tvUsersValue?.text = formatNumber(countUsers)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to fetch users count: ${e.message}")
                tvUsersValue?.text = "0"
            }

        // 2) total requests (all)
        db.collection("Blood Requests").get()
            .addOnSuccessListener { snapshot ->
                val totalRequests = snapshot.size()
                // Now fetch completed count
                db.collection("Blood Requests")
                    .whereEqualTo("Status", "completed")
                    .get()
                    .addOnSuccessListener { completedSnap ->
                        val completed = completedSnap.size()
                        // Display as e.g. "320 (120 completed)"
                        tvRequestsValue?.text = "${formatNumber(totalRequests)}  "
                    }
                    .addOnFailureListener { e2 ->
                        Log.w(TAG, "Failed to fetch completed requests: ${e2.message}")
                        tvRequestsValue?.text = formatNumber(totalRequests)
                    }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to fetch requests: ${e.message}")
                tvRequestsValue?.text = "0"
            }
    }

    @OptIn(UnstableApi::class)
    private fun loadCompletedRequestsFromDonationHistory() {
        val candidateCollections = listOf(
            "DonationHistory"
        )
        val candidateFields = listOf("Status", "completed")

        // recursive helpers
        fun tryFieldOnCollection(collection: String, fieldIndex: Int, onFail: () -> Unit) {
            if (fieldIndex >= candidateFields.size) {
                onFail()
                return
            }
            val field = candidateFields[fieldIndex]
            // try value "Completed" first (capital C), then lowercase if needed - we'll try exact "Completed"
            db.collection(collection).whereEqualTo(field, "completed").get()
                .addOnSuccessListener { snap ->
                    tvTotalValue?.text = formatNumber(snap.size())
                    Log.d(TAG, "Completed count from '$collection' where $field='Completed' = ${snap.size()}")
                }
                .addOnFailureListener { e ->
                    // try next field
                    Log.w(TAG, "Query failed on $collection.$field : ${e.message} — trying next field")
                    tryFieldOnCollection(collection, fieldIndex + 1, onFail)
                }
        }

        fun tryCollectionAt(index: Int) {
            if (index >= candidateCollections.size) {
                // nothing worked — fallback to 0
                tvTotalValue?.text = "0"
                Log.w(TAG, "All candidate collections failed to return completed count; defaulting to 0")
                return
            }
            val col = candidateCollections[index]
            // attempt quick existence/read to see if collection exists (simple get -> then try fields)
            db.collection(col).limit(1).get()
                .addOnSuccessListener { _ ->
                    // collection appears accessible — try the fields on it
                    tryFieldOnCollection(col, 0) {
                        // if all fields failed in this collection, try next collection
                        tryCollectionAt(index + 1)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Could not read sample from collection '$col': ${e.message} — trying next collection")
                    tryCollectionAt(index + 1)
                }
        }

        // start attempts
        tryCollectionAt(0)
    }

    private fun formatNumber(n: Int): String {
        // simple formatting (1_285 -> "1,285")
        return String.format("%,d", n)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvGreeting = null
        tvUsersValue = null
        tvRequestsValue = null
        tvTotalValue = null
    }
}