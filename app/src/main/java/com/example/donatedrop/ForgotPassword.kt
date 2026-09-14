package com.example.donatedrop

import android.os.Bundle
import android.util.Patterns
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class ForgotPassword : AppCompatActivity() {

    private lateinit var emailEt: TextInputEditText
    private lateinit var resetBtn: MaterialButton
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        // If you have a top app bar
        findViewById<MaterialToolbar>(R.id.topAppBar).setOnClickListener {
            finish()
        }

        // init views (make sure the IDs match your layout)
        emailEt = findViewById(R.id.emailEt)
        resetBtn = findViewById(R.id.sendResetBtn)

        // init FirebaseAuth
        auth = FirebaseAuth.getInstance()

        resetBtn.setOnClickListener {
            val email = emailEt.text?.toString()?.trim() ?: ""
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendPasswordReset(email)
        }

        findViewById<TextView>(R.id.backToLogin).setOnClickListener {
            finish()
        }
    }

    private fun sendPasswordReset(email: String) {
        // UI: show progress and disable button
        resetBtn.isEnabled = false

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                // restore UI
                resetBtn.isEnabled = true

                if (task.isSuccessful) {
                    // Show confirmation
                    AlertDialog.Builder(this)
                        .setTitle("Reset Link Sent")
                        .setMessage("A password reset link has been sent to:\n\n$email\n\nCheck your inbox (and spam) and follow the instructions.")
                        .setPositiveButton("Open Email") { _, _ ->
                            // optional: open an email app
                            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                            intent.addCategory(android.content.Intent.CATEGORY_APP_EMAIL)
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            try {
                                startActivity(intent)
                            } catch (e: Exception) {
                                // no email app found or failed to open
                                Toast.makeText(this, "Couldn't open mail app", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("OK", null)
                        .show()

                    // Optionally finish activity and return to login
                    // finish()
                } else {
                    val msg = task.exception?.localizedMessage ?: "Failed to send reset link. Try again later."
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
    }
}
