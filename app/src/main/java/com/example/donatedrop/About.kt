package com.example.donatedrop

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class About : AppCompatActivity() {

    companion object {
        private const val TAG = "About"
    }

    @OptIn(UnstableApi::class)
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val devImg = findViewById<ShapeableImageView>(R.id.dev_img)
        val imagedev = findViewById<ShapeableImageView>(R.id.dev_image)
        // set local placeholder while loading
        devImg.setImageResource(R.drawable.bg_circle_light)
        imagedev.setImageResource(R.drawable.bg_circle_light)


        // Firestore instance
        val db = Firebase.firestore

        // Option 1: try to use the currently logged in user's uid
        val currentUid = Firebase.auth.currentUser?.uid

        if (currentUid != null) {
            // assuming your admin documents are stored in "admins" collection keyed by uid
            db.collection("Admin").document(currentUid)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val avatarUrl = doc.getString("avatarUrl") ?: ""
                        if (!avatarUrl.isNullOrBlank()) {
                            loadImageIntoView(avatarUrl, devImg)
                        } else {
                            Log.w(TAG, "avatarUrl field is empty for admin doc: $currentUid")
                        }
                    } else {
                        Log.w(TAG, "Admin doc not found using uid: $currentUid")
                        // fallback: try a known admin doc id below
                        loadAdminByFixedId(db, devImg)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to fetch admin doc by uid", e)
                    // fallback: try known id
                    loadAdminByFixedId(db, devImg)
                }
        } else {
            // No logged-in user -> try fetch by a known document id (replace with your doc id)
            loadAdminByFixedId(db, devImg)
        }

        val knownUserId = "M4RLqgWKJyec2d9QLyJD7KSVs593" // <-- replace with real user doc id if you have one
        if (knownUserId.isNotBlank()) {
            fetchUserByIdFlexible(db, knownUserId, imagedev)
        } else {
            // Option C: query first user where role == "user"
            queryAnyUser(db, imagedev)
        }

    }

    @OptIn(UnstableApi::class)
    private fun loadAdminByFixedId(db: com.google.firebase.firestore.FirebaseFirestore, devImg: ShapeableImageView) {
        val adminDocId = "vTg0tRNCwlMU8xwcZjLIo3y5vA12" // <-- replace with real admin document id if you have one
        db.collection("Admin").document(adminDocId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val avatarUrl = doc.getString("avatarUrl") ?: doc.getString("photoUrl")
                    if (!avatarUrl.isNullOrBlank()) {
                        loadImageIntoView(avatarUrl, devImg)
                    } else {
                        Log.w(TAG, "avatarUrl missing for admin doc: $adminDocId")
                    }
                } else {
                    Log.w(TAG, "No admin document found for id: $adminDocId")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch admin doc by fixed id", e)
            }
    }

    @OptIn(UnstableApi::class)
    private fun fetchUserByIdFlexible(
        db: com.google.firebase.firestore.FirebaseFirestore,
        docId: String,
        imageView: ShapeableImageView
    ) {
        // try "Users" (capital) first
        db.collection("Users").document(docId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val avatar = doc.getString("avatarUrl") ?: doc.getString("photoUrl")
                    if (!avatar.isNullOrBlank()) {
                        loadImageIntoView(avatar, imageView)
                        return@addOnSuccessListener
                    }
                }
                // if not found or missing avatar, try lowercase "users"
                db.collection("users").document(docId)
                    .get()
                    .addOnSuccessListener { doc2 ->
                        if (doc2 != null && doc2.exists()) {
                            val avatar2 = doc2.getString("avatarUrl") ?: doc2.getString("photoUrl")
                            if (!avatar2.isNullOrBlank()) {
                                loadImageIntoView(avatar2, imageView)
                            } else {
                                Log.w(TAG, "User doc exists but no avatar field for id: $docId")
                            }
                        } else {
                            Log.w(TAG, "No user document found for id: $docId in either Users or users")
                        }
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Failed to fetch user doc (users) by id: $docId", e2)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch user doc (Users) by id: $docId", e)
                // try lowercase "users" as a last attempt
                db.collection("users").document(docId)
                    .get()
                    .addOnSuccessListener { doc2 ->
                        if (doc2 != null && doc2.exists()) {
                            val avatar2 = doc2.getString("avatarUrl") ?: doc2.getString("photoUrl")
                            if (!avatar2.isNullOrBlank()) {
                                loadImageIntoView(avatar2, imageView)
                            } else {
                                Log.w(TAG, "User doc exists but no avatar field for id: $docId")
                            }
                        } else {
                            Log.w(TAG, "No user document found (users) for id: $docId after failure")
                        }
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Failed to fetch user doc (users) by id after initial failure: $docId", e2)
                    }
            }
    }

    @OptIn(UnstableApi::class)
    private fun queryAnyUser(db: com.google.firebase.firestore.FirebaseFirestore, imageView: ShapeableImageView) {
        // try common field name "role" and value "user" — change if your schema differs
        db.collection("Users")
            .whereEqualTo("role", "user")
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    val doc = snap.documents[0]
                    val avatar = doc.getString("avatarUrl") ?: doc.getString("photoUrl")
                    if (!avatar.isNullOrBlank()) {
                        loadImageIntoView(avatar, imageView)
                        return@addOnSuccessListener
                    }
                }
                // try lowercase collection if previous attempt returned nothing
                db.collection("users")
                    .whereEqualTo("role", "user")
                    .limit(1)
                    .get()
                    .addOnSuccessListener { snap2 ->
                        if (!snap2.isEmpty) {
                            val doc2 = snap2.documents[0]
                            val avatar2 = doc2.getString("avatarUrl") ?: doc2.getString("photoUrl")
                            if (!avatar2.isNullOrBlank()) {
                                loadImageIntoView(avatar2, imageView)
                            } else {
                                Log.w(TAG, "Queried user doc has no avatar fields")
                            }
                        } else {
                            Log.w(TAG, "No user docs found by query(role == user)")
                        }
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Query to users collection failed", e2)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Query to Users collection failed", e)
            }
    }

    private fun loadImageIntoView(url: String, imageView: ShapeableImageView) {
        // Cloudinary URLs are usually accessible via HTTPS. Glide will handle caching.
        Glide.with(this)
            .load(url)
            .centerCrop()
            .into(imageView)
    }
}