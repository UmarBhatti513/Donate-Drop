package com.example.donatedrop.ui.requests

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.donatedrop.R
import com.example.donatedrop.databinding.DialogUserDetailsBinding
import com.example.donatedrop.databinding.FragmentAdminUsersBinding
import com.example.donatedrop.models.User
import com.example.donatedrop.ui.adapter.UserAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AdminUsers : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!
    private val TAG = "AdminUsers"

    private lateinit var adapter: UserAdapter
    private val allUsers = mutableListOf<User>()
    private val db: FirebaseFirestore by lazy {
        // ensure FirebaseApp initialized
        if (FirebaseApp.getApps(requireContext()).isEmpty()) {
            FirebaseApp.initializeApp(requireContext())
        }
        FirebaseFirestore.getInstance()
    }

    private var listenerReg: ListenerRegistration? = null

    private lateinit var rvUsers: RecyclerView
    private lateinit var btnAddUser: MaterialButton
    private lateinit var searchView: androidx.appcompat.widget.SearchView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val searchView = view.findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView)
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        searchEditText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.gray))

        adapter = UserAdapter(mutableListOf(),
            onItemClick = { user ->
                showUserDetails(user)
            },
            onMenuClick = { view, user ->
                showDeleteConfirm(user)
            }
        )
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = adapter


        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterList(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })
        

        // start listening
        startListeningUsers()
    }

    private fun filterList(q: String?) {
        val query = q?.trim()?.lowercase() ?: ""
        val filtered = if (query.isEmpty()) {
            allUsers
        } else {
            allUsers.filter { u ->
                u.name.lowercase().contains(query) || u.email.lowercase().contains(query) || u.role.lowercase().contains(query)
            }
        }
        adapter.updateList(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    @OptIn(UnstableApi::class)
    private fun startListeningUsers() {
        Log.d(TAG, "startListeningUsers() - attaching snapshot listener")
        // Real-time snapshot listener (logs heavily)
        listenerReg = db.collection("users")
            .orderBy("Name")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Snapshot listener error: ${error.message}", error)
                    Toast.makeText(requireContext(), "Error loading users: ${error.message}", Toast.LENGTH_SHORT).show()
                    // fallback to single fetch
                    fetchUsersOnce()
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    Log.w(TAG, "Snapshot is null — falling back to single fetch")
                    fetchUsersOnce()
                    return@addSnapshotListener
                }

                Log.d(TAG, "Snapshot received: docs=${snapshot.size()}")
                allUsers.clear()
                for (doc in snapshot.documents) {
                    val rawRole = doc.getString("Role")            // role value in Firestore (may be null)
                    val normalizedRole = if (rawRole?.equals("Admin", ignoreCase = true) == true) {
                        "Admin"
                    } else {
                        "User"
                    }

                    val user = User(
                        id = doc.id,
                        name = doc.getString("Name") ?: "",
                        email = doc.getString("Email") ?: "",
                        role = normalizedRole,
                        avatarUrl = doc.getString("avatarUrl"),
                        phone = doc.getString("Phone")
                    )
                    allUsers.add(user)
                }
                adapter.updateList(allUsers)
                updateEmptyState(allUsers.isEmpty())
            }
    }

    @OptIn(UnstableApi::class)
    private fun fetchUsersOnce() {
        Log.d(TAG, "fetchUsersOnce() - performing single get()")
        db.collection("users")
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "Single get success: docs=${snapshot.size()}")
                allUsers.clear()
                for (doc in snapshot.documents) {
                    val u = User(
                        id = doc.id,
                        name = doc.getString("Name") ?: "",
                        email = doc.getString("Email") ?: "",
                        role = doc.getString("Role") ?: "User",
                        avatarUrl = doc.getString("avatarUrl"),
                        phone = doc.getString("Phone")
                    )
                    allUsers.add(u)
                }
                adapter.updateList(allUsers)
                updateEmptyState(allUsers.isEmpty())
            }
            .addOnFailureListener { ex ->
                Log.e(TAG, "fetchUsersOnce failed: ${ex.message}", ex)
                Toast.makeText(requireContext(), "Failed to load users: ${ex.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showDeleteConfirm(user: User) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete user")
            .setMessage("Are you sure you want to delete ${user.name}?")
            .setPositiveButton("Delete") { _, _ -> deleteUser(user) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @OptIn(UnstableApi::class)
    private fun deleteUser(user: User) {
        Log.d(TAG, "deleteUser: id=${user.id}, Name=${user.name}")
        db.collection("users").document(user.id)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Deleted user ${user.id}")
                adapter.removeUserById(user.id)
                Toast.makeText(requireContext(), "${user.name} deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { ex ->
                Log.e(TAG, "Failed to delete user: ${ex.message}", ex)
                Toast.makeText(requireContext(), "Delete failed: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showUserDetails(user: User) {
        val dialog = BottomSheetDialog(requireContext())
        val binding = DialogUserDetailsBinding.inflate(LayoutInflater.from(requireContext()))
        dialog.setContentView(binding.root)

        // Populate fields
        binding.dialogTvName.text = user.name
        val displayRole = if (user.role.equals("admin", ignoreCase = true)) "Admin" else "User"
        binding.dialogTvRole.text = displayRole
        binding.dialogTvEmail.text = user.email
        binding.dialogTvId.text = user.id

        // If you store phone or other fields, show them; if not, hide or show default:
        // Suppose you have optional phone in User data class as phone: String?
        // binding.dialogTvPhone.text = user.phone ?: "(not provided)"

        // Avatar with Glide
        if (!user.avatarUrl.isNullOrBlank()) {
            Glide.with(requireContext())
                .load(user.avatarUrl)
                .circleCrop()
                .into(binding.dialogIvAvatar)
        } else {
            Glide.with(requireContext())
                .load(R.drawable.user)
                .circleCrop()
                .into(binding.dialogIvAvatar)
        }

        // Button actions
        binding.btnClose.setOnClickListener { dialog.dismiss() }
        binding.btnDelete.setOnClickListener {
            // show confirmation then delete via existing deleteUser(user) method
            dialog.dismiss()
            showDeleteConfirm(user) // reuse your confirmDelete/deleteUser flow in fragment
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerReg?.remove()
        _binding = null
    }
}