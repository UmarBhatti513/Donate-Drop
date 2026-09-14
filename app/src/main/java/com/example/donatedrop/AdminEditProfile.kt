package com.example.donatedrop

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch

class AdminEditProfile : AppCompatActivity() {

    companion object {
        private const val REQUEST_PICK_IMAGE = 101
    }

    private lateinit var avatar: ShapeableImageView
    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar

    private var avatarUri: Uri? = null

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // The uid we're editing (either passed by intent or current user)
    private var targetUid: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin_edit_profile)

        avatar = findViewById(R.id.iv_Profile)
        etFullName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        btnSave = findViewById(R.id.btnSave)
        progressBar = findViewById(R.id.progressBar8)

        val current = auth.currentUser
        if (current == null) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadProfileForCurrentUser()

        avatar.setOnClickListener {
            val pick = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pick.type = "image/*"
            startActivityForResult(pick, REQUEST_PICK_IMAGE)
        }

        btnSave.setOnClickListener { onSaveClicked() }


    }

    private fun loadProfileForCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("Admin").document(uid)
            .get()
            .addOnSuccessListener { snap ->
                if (snap != null && snap.exists()) {
                    etFullName.setText(snap.getString("Name") ?: "")
                    etEmail.setText(snap.getString("Email") ?: "")
                    etPhone.setText(snap.getString("Phone") ?: "")
                    val avatarUrl = snap.getString("avatarUrl")
                    if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(this).load(avatarUrl).circleCrop().into(avatar)
                    } else {
                        avatar.setImageResource(R.drawable.bg_profile_circle)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun onSaveClicked() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "No signed-in user", Toast.LENGTH_SHORT).show()
            return
        }

        val name = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty()) {
            etFullName.error = "Enter name"
            return
        }
        if (email.isEmpty()) {
            etEmail.error = "Enter email"
            return
        }

        setUiEnabled(false)
        progressBar.visibility = View.VISIBLE

        if (avatarUri != null) {
            // upload first, then save with returned avatarUrl
            lifecycleScope.launch {
                try {
                    val secureUrl = CloudinaryUploader.uploadUnsigned(this@AdminEditProfile, avatarUri!!)
                    if (!secureUrl.isNullOrEmpty()) {
                        saveProfileFirestore(uid, secureUrl)
                    } else {
                        progressBar.visibility = View.GONE
                        setUiEnabled(true)
                        Toast.makeText(this@AdminEditProfile, "Image upload failed. Check Cloudinary settings.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    setUiEnabled(true)
                    Toast.makeText(this@AdminEditProfile, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // no avatar change
            saveProfileFirestore(uid, avatarUrl = null)
        }
    }

    private fun saveProfileFirestore(uid: String, avatarUrl: String?) {
        val data = HashMap<String, Any>()
        data["Name"] = etFullName.text.toString().trim()
        data["Email"] = etEmail.text.toString().trim()
        if (etPhone.text.toString().trim().isNotEmpty()) data["Phone"] = etPhone.text.toString().trim()
        if (!avatarUrl.isNullOrEmpty()) data["avatarUrl"] = avatarUrl

        db.collection("Admin").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                setUiEnabled(true)
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                setUiEnabled(true)
                Toast.makeText(this, "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setUiEnabled(enabled: Boolean) {
        btnSave.isEnabled = enabled
        etFullName.isEnabled = enabled
        etEmail.isEnabled = enabled
        etPhone.isEnabled = enabled
        avatar.isEnabled = enabled
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                avatarUri = uri
                avatar.setImageURI(uri)
            }
        }
    }
}