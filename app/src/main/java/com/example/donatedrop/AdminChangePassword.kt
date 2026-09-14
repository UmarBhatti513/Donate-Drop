package com.example.donatedrop

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException

class AdminChangePassword : AppCompatActivity() {

    private lateinit var etCurrentPassword: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnChangePassword: Button
    private lateinit var progressBar: ProgressBar

    private val auth = FirebaseAuth.getInstance()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin_change_password)

        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        progressBar = findViewById(R.id.progressBar9)

        btnChangePassword.setOnClickListener { attemptChangePassword() }
    }

    private fun attemptChangePassword() {
        val currentPwd = etCurrentPassword.text.toString()
        val newPwd = etNewPassword.text.toString()
        val confirmPwd = etConfirmPassword.text.toString()

        if (currentPwd.isBlank() || newPwd.isBlank() || confirmPwd.isBlank()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPwd.length < 6) {
            Toast.makeText(this, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPwd != confirmPwd) {
            Toast.makeText(this, "New password and confirmation do not match", Toast.LENGTH_SHORT).show()
            return
        }

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "No signed in user", Toast.LENGTH_SHORT).show()
            return
        }

        // Check whether the user is signed in via email/password provider.
        val hasPasswordProvider = user.providerData.any { it.providerId == "password" }
        if (!hasPasswordProvider) {
            // If admin signed in via Google/GitHub, they cannot change password here.
            Toast.makeText(this,
                "Password change isn't supported for accounts signed-in with Google or other providers. Use account provider to change password or use Reset Password email.",
                Toast.LENGTH_LONG).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnChangePassword.isEnabled = false

        // Reauthenticate with current password (required by Firebase)
        val email = user.email
        if (email.isNullOrBlank()) {
            progressBar.visibility = View.GONE
            btnChangePassword.isEnabled = true
            Toast.makeText(this, "No email associated with account", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = EmailAuthProvider.getCredential(email, currentPwd)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                // Re-auth succeeded — update password
                user.updatePassword(newPwd)
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        btnChangePassword.isEnabled = true
                        Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        btnChangePassword.isEnabled = true
                        // Common reason: recent login requirement (shouldn't happen directly after reauth) or weak password
                        val msg = e.localizedMessage ?: "Failed to update password"
                        Toast.makeText(this, "Update failed: $msg", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnChangePassword.isEnabled = true
                // If reauth failed because of recent login, show helpful message; otherwise show error
                if (e is FirebaseAuthRecentLoginRequiredException) {
                    Toast.makeText(this, "Please sign in again and try changing password.", Toast.LENGTH_LONG).show()
                } else {
                    val msg = e.localizedMessage ?: "Reauthentication failed"
                    Toast.makeText(this, "Reauthentication failed: $msg", Toast.LENGTH_LONG).show()
                }
            }
    }

}