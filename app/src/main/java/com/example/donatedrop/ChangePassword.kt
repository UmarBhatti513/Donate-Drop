package com.example.donatedrop

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ChangePassword : AppCompatActivity() {

    private lateinit var etCurrentPassword: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmNewPassword: TextInputEditText
    private lateinit var btnSavePassword: Button

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        // find views

        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword)
        btnSavePassword = findViewById(R.id.btnSavePassword)

        btnSavePassword.setOnClickListener { attemptChangePassword() }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun attemptChangePassword() {
        val current = etCurrentPassword.text.toString().trim()
        val newPwd = etNewPassword.text.toString().trim()
        val confirm = etConfirmNewPassword.text.toString().trim()

        if (current.isEmpty() || newPwd.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPwd != confirm) {
            Toast.makeText(this, "New password and confirmation do not match", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate rules: min 8, upper, lower, number or symbol
        if (!isValidPassword(newPwd)) {
            Toast.makeText(this,
                "Password must be at least 8 characters, include uppercase & lowercase and a number or symbol.",
                Toast.LENGTH_LONG).show()
            return
        }

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "No signed-in user", Toast.LENGTH_SHORT).show()
            return
        }

        // Check provider
        val hasPasswordProvider = user.providerData.any { it.providerId == "password" }
        if (!hasPasswordProvider) {
            // Offer the user a reset email since they probably signed in with Google/others
            AlertDialog.Builder(this)
                .setTitle("Password not available")
                .setMessage("This account is signed in with an external provider (Google/etc.). You can send a password reset email to create a password for this account.")
                .setPositiveButton("Send reset email") { _, _ ->
                    sendResetEmailFallback(user.email)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        // Disable UI while working
        btnSavePassword.isEnabled = false
        btnSavePassword.text = "Please wait..."

        val email = user.email
        if (email.isNullOrBlank()) {
            showFinishState("No email associated with account")
            return
        }

        val credential = EmailAuthProvider.getCredential(email, current)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                // Reauth succeeded -> update password
                user.updatePassword(newPwd)
                    .addOnSuccessListener {
                        // Update safe metadata in Firestore
                        val uid = user.uid
                        val updates = mapOf(
                            "passwordUpdated" to true,
                            "passwordChangedAt" to FieldValue.serverTimestamp()
                        )
                        db.collection("users").document(uid)
                            .update(updates)
                            .addOnSuccessListener {
                                showFinishState("Password updated")
                            }
                            .addOnFailureListener { e ->
                                // Password is changed in Auth already - metadata failed
                                showFinishState("Password changed but metadata save failed: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        // Failed to update password in Auth
                        btnSavePassword.isEnabled = true
                        btnSavePassword.text = "Save Password"
                        val msg = e.localizedMessage ?: "Failed to update password"
                        Toast.makeText(this, "Update failed: $msg", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                // Reauthentication failed
                btnSavePassword.isEnabled = true
                btnSavePassword.text = "Save Password"
                if (e is FirebaseAuthRecentLoginRequiredException) {
                    Toast.makeText(this, "Recent login required. Please sign in again.", Toast.LENGTH_LONG).show()
                } else {
                    val msg = e.localizedMessage ?: "Reauthentication failed"
                    Toast.makeText(this, "Reauthentication failed: $msg", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun sendResetEmailFallback(email: String?) {
        if (email.isNullOrBlank()) {
            Toast.makeText(this, "No email found to send reset", Toast.LENGTH_SHORT).show()
            return
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, "Reset email sent to $email", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send reset email: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showFinishState(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        // restore btn state and finish
        btnSavePassword.isEnabled = true
        btnSavePassword.text = "Save Password"
        finish()
    }

    private fun isValidPassword(pwd: String): Boolean {
        if (pwd.length < 8) return false
        val hasUpper = pwd.any { it.isUpperCase() }
        val hasLower = pwd.any { it.isLowerCase() }
        val hasDigitOrSymbol = pwd.any { !it.isLetter() } // digit or symbol
        return hasUpper && hasLower && hasDigitOrSymbol
    }
}