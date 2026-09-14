@file:Suppress("DEPRECATION")

package com.example.donatedrop.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.donatedrop.CreateRequestForm
import com.example.donatedrop.NearbySearch
import com.example.donatedrop.NotificationScreen
import com.example.donatedrop.R
import com.example.donatedrop.adapters.DonationHistoryAdapter
import com.example.donatedrop.adapters.HomeRequestsAdapter
import com.example.donatedrop.databinding.FragmentHomeBinding
import com.example.donatedrop.models.CreateRequest
import com.example.donatedrop.models.DonationHistoryItem
import com.example.donatedrop.ui.history.HistoryFragment
import com.example.donatedrop.ui.requests.RequestsFragment
import com.example.donatedrop.ui.settings.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val TAG = "HomeFragment"
    private lateinit var bloodRequestAdapter: HomeRequestsAdapter
    private lateinit var db: FirebaseFirestore
    private var listenerRegistration: ListenerRegistration? = null
    private var donationsListener: ListenerRegistration? = null
    private lateinit var donationsAdapter: DonationHistoryAdapter

    private val items = mutableListOf<CreateRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // tell Fragment to receive menu callbacks
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar title: if your Activity toolbar should show "DonateDrop", you can set it here
        (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.app_name) // or "DonateDrop"

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
                        startActivity(Intent(requireContext(), NotificationScreen::class.java))
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)


        db = FirebaseFirestore.getInstance()

        binding.rvBloodRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBloodRequests.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        //binding.rvDonationHistory.layoutManager = LinearLayoutManager(requireContext())
        //binding.rvDonationHistory.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))

        bloodRequestAdapter = HomeRequestsAdapter(
            items,
            object : HomeRequestsAdapter.RequestActionListener {
                override fun onRespond(request: CreateRequest) {
                    showContactDialog(request)
                    // TODO: respond to request
                }

            }
        )

        binding.rvBloodRequests.adapter = bloodRequestAdapter
        binding.rvBloodRequests.isNestedScrollingEnabled = false

        donationsAdapter = DonationHistoryAdapter(mutableListOf())
        binding.rvDonationHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDonationHistory.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        binding.rvDonationHistory.adapter = donationsAdapter
        binding.rvDonationHistory.isNestedScrollingEnabled = false

        // Button / click listeners (same behavior as your Activity)
        binding.searchNearby.setOnClickListener {
            startActivity(Intent(requireContext(), NearbySearch::class.java))
        }

        binding.btnRefreshRequests.setOnClickListener {
            restartListening()
        }

        binding.cardCreateRequest.setOnClickListener {
            startActivity(Intent(requireContext(), CreateRequestForm::class.java))
        }
        binding.btnCreateRequest.setOnClickListener {
            startActivity(Intent(requireContext(), CreateRequestForm::class.java))
        }

        binding.tvSeeAllHistory.setOnClickListener {
            val navView = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                R.id.nav_view // replace with your BottomNavigationView id (e.g. binding.navView.id or R.id.nav_view)
            )
            navView.selectedItemId = R.id.navigation_history
        }

        val maybeBottomNav = binding.root.findViewById<BottomNavigationView?>(R.id.bottom_navigation)
        // Note: this findViewById call returns null if the view doesn't exist in the fragment's layout.
        maybeBottomNav?.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> true // already here
                R.id.nav_requests -> {
                    // prefer NavController navigation; but keep Activity-based for now:

                    startActivity(Intent(requireContext(), RequestsFragment::class.java))
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(requireContext(), HistoryFragment::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(requireContext(), SettingsFragment::class.java))
                    true
                }
                else -> false
            }
        }
    }

    override fun onStart(){
        super.onStart()
        startListeningOtherUsers()
        startListeningDonations()

        debugFetchAnyHistory()
    }

    override fun onStop() {
        super.onStop()
        listenerRegistration?.remove()
        listenerRegistration = null
        donationsListener?.remove()
        donationsListener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
        listenerRegistration = null
        donationsListener?.remove()
        donationsListener = null
        _binding = null
    }

    fun restartListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
        startListeningOtherUsers()

    }

    @OptIn(UnstableApi::class)
    private fun startListeningOtherUsers() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            bloodRequestAdapter.setItems(emptyList())
            return
        }
        val uid = currentUser.uid

        // remove previous listener if present
        listenerRegistration?.remove()
        listenerRegistration = null

        listenerRegistration = db.collection("Blood Requests")
            .orderBy("Created At", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed: ${error.message}", error)
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) {
                    bloodRequestAdapter.setItems(emptyList())
                    return@addSnapshotListener
                }

                val list = mutableListOf<CreateRequest>()
                for (doc in snapshots.documents) {
                    try {
                        // createdBy field name might be "Created By" or "createdBy" — try both
                        val createdBy =
                            doc.getString("Created By") ?: ""
                        if (createdBy == uid) continue // skip own requests

                        val id = doc.id
                        val name = doc.getString("Receiver's Name") ?: ""
                        val contact = doc.getString("Receiver's Phone") ?: ""
                        val address = doc.getString("Hospital Address") ?: ""
                        val bloodType = doc.getString("Blood Type") ?: ""
                        val notes = doc.getString("Additional Note") ?: ""
                        val createdAt = doc.getTimestamp("Created At")
                        val status = doc.getString("Status") ?: ""
                        val responderId = doc.getString("Responder ID") ?: ""
                        val responderName = doc.getString("Responder Name") ?: ""
                        val urgency = doc.getString("Urgency Level") ?: ""

                        val quantityValue = doc.get("Quantity")
                        val quantityStr = when (quantityValue) {
                            is Number -> quantityValue.toInt().toString()
                            is String -> try {
                                quantityValue.toInt().toString()
                            } catch (e: NumberFormatException) {
                                quantityValue
                            }

                            else -> ""
                        }

                        val req = CreateRequest(
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
                        list.add(req)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error parsing doc ${doc.id}: ${ex.message}", ex)
                    }
                }

                // update adapter on UI thread
                activity?.runOnUiThread {
                    bloodRequestAdapter.setItems(list)
                }
            }

    }

    private fun showContactDialog(request: CreateRequest) {
        try {
            val ctx = requireContext()
            val inflater = LayoutInflater.from(ctx)
            // inflate the custom dialog layout (see XML below)
            val view = inflater.inflate(R.layout.dialogue_contact, null)

            val tvName = view.findViewById<TextView>(R.id.dialog_tv_name)
            val tvPhone = view.findViewById<TextView>(R.id.dialog_tv_phone)
            val btnCall = view.findViewById<Button>(R.id.dialog_btn_call)
            val btnClose = view.findViewById<Button>(R.id.dialog_btn_close)
            // optional claim button if you want to claim directly from dialog:
            val btnClaim = view.findViewById<Button?>(R.id.dialog_btn_claim) // may be null if not present

            // set text
            tvName.text = request.name.takeIf { !it.isNullOrBlank() } ?: "Unknown"
            tvPhone.text = request.contact.takeIf { !it.isNullOrBlank() } ?: "No phone number"

            val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setView(view)
                .setCancelable(true)
                .create()

            // Call button: open phone dialer with the number
            btnCall.setOnClickListener {
                val rawNumber = request.contact ?: ""
                val sanitized = rawNumber.trim()
                if (sanitized.isBlank()) {
                    Toast.makeText(ctx, "No phone number available", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Use ACTION_DIAL — does NOT require CALL_PHONE permission
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                intent.data = android.net.Uri.parse("tel:${sanitized}")
                startActivity(intent)
                // optionally keep dialog open or close:
                // dialog.dismiss()
            }

            // Close button
            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            // Optional: Claim button (uncomment & provide claimRequest function)
            btnClaim?.setOnClickListener {
                // If you implemented claimRequest(requestId), call it here:
                // claimRequest(request.id)
                Toast.makeText(ctx, "Claiming request...", Toast.LENGTH_SHORT).show()
                // dialog.dismiss()
            }

            dialog.show()

        } catch (e: Exception) {
            android.util.Log.e("RequestsFragment", "showContactDialog failed", e)
            Toast.makeText(requireContext(), "Unable to show contact", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startListeningDonations() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            android.util.Log.d("HomeFragmentDebug", "no current user -> clearing donations list")
            donationsAdapter.setItems(emptyList())
            return
        }
        val uid = currentUser.uid

        // remove previous listener if present
        donationsListener?.remove()
        donationsListener = null

        android.util.Log.d("HomeFragmentDebug", "attaching donations listener for uid=$uid")

        // Primary: realtime listener (may require composite index)
        donationsListener = db.collection("DonationHistory")
            .whereEqualTo("CompletedBy", uid)
            .orderBy("CompletedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    // Full error log for diagnosis
                    android.util.Log.e("HomeFragmentDebug", "donations listener ERROR: ${error.message}", error)

                    // Try fallback fetch (no orderBy) so we can still display data during debugging
                    android.util.Log.w("HomeFragmentDebug", "falling back to client fetch (no orderBy) to avoid index requirement")
                    fallbackFetchDonations(uid)
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    android.util.Log.w("HomeFragmentDebug", "snapshots == null")
                    donationsAdapter.setItems(emptyList())
                    return@addSnapshotListener
                }

                if (snapshots.isEmpty) {
                    android.util.Log.d("HomeFragmentDebug", "snapshot empty (no matching donation docs)")
                    donationsAdapter.setItems(emptyList())
                    return@addSnapshotListener
                }

                val list = mutableListOf<DonationHistoryItem>()
                android.util.Log.d("HomeFragmentDebug", "snapshot received docs=${snapshots.size()}")
                for (doc in snapshots.documents) {
                    android.util.Log.d("HomeFragmentDebug", "doc ${doc.id} => ${doc.data}")
                    try {
                        list.add(parseHistoryDoc(doc))
                    } catch (ex: Exception) {
                        android.util.Log.e("HomeFragmentDebug", "parseHistoryDoc failed for ${doc.id}: ${ex.message}", ex)
                    }
                }

                activity?.runOnUiThread {
                    donationsAdapter.setItems(list)
                }
            }
    }

    /** Fallback: fetch documents without orderBy and filter on client (works without composite index). */
    private fun fallbackFetchDonations(uid: String) {
        android.util.Log.d("HomeFragmentFallback", "fallbackFetchDonations: starting for uid=$uid")
        db.collection("DonationHistory")
            .whereEqualTo("CompletedBy", uid) // keep equality so server still filters; this shouldn't require composite index alone
            .get()
            .addOnSuccessListener { snap ->
                android.util.Log.d("HomeFragmentFallback", "fallbackFetchDonations: got ${snap.size()} docs")
                val list = snap.documents.mapNotNull { doc ->
                    try {
                        android.util.Log.d("HomeFragmentFallback", "doc ${doc.id} => ${doc.data}")
                        parseHistoryDoc(doc)
                    } catch (e: Exception) {
                        android.util.Log.w("HomeFragmentFallback", "parseHistoryDoc failed for ${doc.id}: ${e.message}")
                        null
                    }
                }.sortedByDescending { it.completedAt?.toDate() } // local sort
                activity?.runOnUiThread { donationsAdapter.setItems(list) }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("HomeFragmentFallback", "fallback fetch failed: ${e.message}", e)
                // If permission denied, show a toast
                if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Cannot load donation history: permission denied", Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    /** Debug helper: fetch any recent docs from the collection (ignore CompletedBy). */
    private fun debugFetchAnyHistory() {
        android.util.Log.d("HomeFragmentDebug", "debugFetchAnyHistory: scanning DonationHistory top 10")
        db.collection("DonationHistory").limit(10)
            .get()
            .addOnSuccessListener { snap ->
                android.util.Log.d("HomeFragmentDebug", "debugFetchAnyHistory: got ${snap.size()} docs")
                for (doc in snap.documents) {
                    android.util.Log.d("HomeFragmentDebug", "doc ${doc.id} keys=${doc.data?.keys} data=${doc.data}")
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("HomeFragmentDebug", "debugFetchAnyHistory failed: ${e.message}", e)
            }
    }


    private fun parseHistoryDoc(doc: DocumentSnapshot): DonationHistoryItem {
        val id = doc.id
        val bloodType = doc.getString("Blood Type") ?: ""
        val receiverName = doc.getString("Receiver's Name") ?: ""
        val responderName = doc.getString("CompletedByName")
            ?: doc.getString("Responder Name") ?: ""
        val contact = doc.getString("Receiver's Phone") ?: ""
        val address = doc.getString("Hospital Address") ?: ""
        val createdAt = doc.getTimestamp("Created At")
        val completedAt = doc.getTimestamp("CompletedAt")
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
            createdAt = createdAt,
            completedAt = completedAt,
            status = status,
            createdBy = createdBy,
            completedBy = completedBy
        )
    }

}
