package com.example.donatedrop

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.Log
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch

class EditProfile : AppCompatActivity() {
    companion object {
        private const val REQUEST_PICK_IMAGE = 101
        private const val TAG = "EditProfile"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var avatar: ShapeableImageView
    private lateinit var btnChangePhoto: ImageView
    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCity: EditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var progressBar: ProgressBar

    private var avatarUri: Uri? = null

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        toolbar = findViewById(R.id.toolbar)
        avatar = findViewById(R.id.img_profile)
        btnChangePhoto = findViewById(R.id.img_camera)
        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etCity = findViewById(R.id.etCity)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btn_cancel)

        progressBar = ProgressBar(this).apply { visibility = View.GONE }

        toolbar.setNavigationOnClickListener { finish() }

        loadCurrentUserProfile()

        btnChangePhoto.setOnClickListener {
            val pick = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pick.type = "image/*"
            startActivityForResult(pick, REQUEST_PICK_IMAGE)
        }

        btnSave.setOnClickListener { saveProfile() }

        btnCancel.setOnClickListener { finish() }

    }

    private fun loadCurrentUserProfile() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val uid = user.uid

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { snap ->
                if (snap != null && snap.exists()) {
                    etFullName.setText(snap.getString("Name") ?: "")
                    etEmail.setText(snap.getString("Email") ?: "")
                    etPhone.setText(snap.getString("Phone") ?: "")
                    etCity.setText(snap.getString("City") ?: "")
                    val avatarUrl = snap.getString("avatarUrl")
                    if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(this).load(avatarUrl).circleCrop().into(avatar)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun saveProfile() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = user.uid

        val name = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val city = etCity.text.toString().trim()

        if (name.isEmpty()) {
            etFullName.error = "Enter name"
            return
        }
        if (email.isEmpty()) {
            etEmail.error = "Enter email"
            return
        }

        // disable UI while saving
        setUiEnabled(false)
        progressBar.visibility = View.VISIBLE

        if (avatarUri != null) {
            // Upload avatar to Cloudinary (unsigned) using the helper
            lifecycleScope.launch {
                try {
                    val secureUrl = CloudinaryUploader.uploadUnsigned(this@EditProfile, avatarUri!!)
                    if (!secureUrl.isNullOrEmpty()) {
                        Log.d(TAG, "Uploaded avatar secureUrl: $secureUrl")
                        saveProfileFirestore(uid, avatarUrl = secureUrl)
                    } else {
                        // upload failed
                        progressBar.visibility = View.GONE
                        setUiEnabled(true)
                        Toast.makeText(this@EditProfile, "Image upload failed. Check your preset/cloud name", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during upload: ${e.message}", e)
                    progressBar.visibility = View.GONE
                    setUiEnabled(true)
                    Toast.makeText(this@EditProfile, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // no new avatar -> update Firestore directly
            saveProfileFirestore(uid, avatarUrl = null)
        }
    }


    private fun saveProfileFirestore(uid: String, avatarUrl: String?) {
        val data = HashMap<String, Any>()
        data["Name"] = etFullName.text.toString().trim()
        data["Email"] = etEmail.text.toString().trim()
        if (etPhone.text.toString().trim().isNotEmpty()) data["Phone"] = etPhone.text.toString().trim()
        if (etCity.text.toString().trim().isNotEmpty()) data["City"] = etCity.text.toString().trim()
        if (!avatarUrl.isNullOrEmpty()) data["avatarUrl"] = avatarUrl

        db.collection("users")
            .document(uid)
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
        btnCancel.isEnabled = enabled
        btnChangePhoto.isEnabled = enabled
        etFullName.isEnabled = enabled
        etEmail.isEnabled = enabled
        etPhone.isEnabled = enabled
        etCity.isEnabled = enabled
        avatar.isEnabled = enabled
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                avatarUri = uri
                // Show local preview only — we are NOT uploading it to storage.
                avatar.setImageURI(uri)
            }
        }
    }


}