package com.example.donatedrop.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.example.donatedrop.AdminChangePassword
import com.example.donatedrop.AdminEditProfile
import com.example.donatedrop.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminSettings : Fragment() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_admin_settings, container, false)

        val tvName = view.findViewById<TextView>(R.id.tvName)
        val tvRole = view.findViewById<TextView?>(R.id.tvRole) // optional
        val profileImage = view.findViewById<ImageView?>(R.id.profileImage)
        val tvEmail = view.findViewById<TextView?>(R.id.iv_email_text)
        // The outer include row has id row_logout in your XML
        val rowLogout = view.findViewById<View>(R.id.row_logout)
        val rowChangePassword = view.findViewById<View>(R.id.row_change_password)

        populateFromFirestoreOrFallback(tvName, tvEmail, profileImage)

        val rowEditProfile = view.findViewById<View>(R.id.row_edit_profile) // replace id if different
        rowEditProfile.setOnClickListener {
            val intent = Intent(requireContext(), AdminEditProfile::class.java)
            startActivity(intent)
        }

        rowChangePassword.setOnClickListener {
            val intent = Intent(requireContext(), AdminChangePassword::class.java)
            startActivity(intent)
        }


        rowLogout.setOnClickListener {
            showLogoutDialog()
        }

        return view
    }

    @OptIn(UnstableApi::class)
    private fun populateFromFirestoreOrFallback(
        tvName: TextView,
        tvEmail: TextView?,
        profileImage: ImageView?
    ) {
        val user = auth.currentUser
        if (user == null) {
            // No logged-in user
            tvName.text = "Admin"
            tvEmail?.text = ""
            return
        }

        val uid = user.uid

        // Try common collection names. First try "admins" with doc id = uid
        val tryCollections = listOf("Admin")

        fun setFallback() {
            // fallback to FirebaseAuth info if Firestore doc not found
            tvName.text = user.displayName ?: user.email?.substringBefore("@") ?: "Admin"
            tvEmail?.text = user.email ?: ""
            // optional: load photoUrl from user
            user.photoUrl?.let {
                Glide.with(this)
                    .load(it)
                    .circleCrop()
                    .into(profileImage!!)
            }


        }

        // Helper to try next collection name if doc doesn't exist
        fun tryCollectionAt(index: Int) {
            if (index >= tryCollections.size) {
                // no doc found in collections -> fallback
                setFallback()
                return
            }

            val col = tryCollections[index]
            db.collection(col).document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        // Expecting fields like "name", "email", "photoUrl"
                        val name = doc.getString("Name")
                        val email = doc.getString("Email")
                        val photoUrl = doc.getString("avatarUrl") // or doc.get("photoUrl") as String?

                        if (!name.isNullOrBlank()) {
                            tvName.text = name
                        } else {
                            // if name field missing, still try fallback to auth
                            tvName.text = user.displayName ?: user.email?.substringBefore("@") ?: "Admin"
                        }

                        tvEmail?.text = email ?: user.email ?: ""

                        if (!photoUrl.isNullOrBlank() && profileImage != null) {
                            Glide.with(this)
                                .load(photoUrl)
                                .circleCrop()
                                .into(profileImage)
                        }
                    } else {
                        // document not found — try next collection name
                        tryCollectionAt(index + 1)
                    }
                }
                .addOnFailureListener { ex ->
                    Log.w("AdminSettings", "Firestore read failed for $col/$uid", ex)
                    Toast.makeText(requireContext(), "Failed loading admin data", Toast.LENGTH_SHORT).show()
                    // On error, fallback to auth info
                    setFallback()
                }
        }

        // Kick off attempts
        tryCollectionAt(0)
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Log out")
            .setMessage("Are you sure you want to log out?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                performLogout()
            }
            .show()
    }

    private fun performLogout() {
        // Firebase sign out
        auth.signOut()

        // If you also use GoogleSignIn (or other providers), sign them out too:
        // GoogleSignIn.getClient(requireContext(), GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()

        // Send user to LoginActivity and clear back stack
        val intent = Intent(requireContext(), com.example.donatedrop.LoginScreen::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}