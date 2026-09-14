package com.example.donatedrop

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth


class LoginScreen : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: TextInputEditText
    private lateinit var passwordEt: TextInputEditText
    private lateinit var progressBar: View

    private val admin_email = "developerbhatti24@gmail.com"

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_screen)

        auth = FirebaseAuth.getInstance() // Initialize Firebase Auth
        etEmail = findViewById<TextInputEditText>(R.id.emailEt)
        passwordEt = findViewById<TextInputEditText>(R.id.etPassword)
        progressBar = findViewById<View>(R.id.progressBar2)

        // val currentUser = auth.currentUser
        // updateUI(currentUser)
        val currentUser = auth.currentUser
        if (currentUser != null) {
            navigateBasedOnAdminEmail(currentUser.email)
            return
        }

        findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass  = passwordEt.text.toString().trim()
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Enter Details", Toast.LENGTH_SHORT).show()
            }
            progressBar.visibility = View.VISIBLE
            // Sign in user

            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // go to main
                        val user = auth.currentUser
                        navigateBasedOnAdminEmail(user?.email)
                        finish()
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }


        findViewById<TextView>(R.id.tvSignup).setOnClickListener {
            startActivity(Intent(this, SignupScreen::class.java))
            finish()
        }

        findViewById<TextView>(R.id.tvForgot).setOnClickListener {
            startActivity(Intent(this, ForgotPassword::class.java))
        }
    }

    private fun navigateBasedOnAdminEmail(email: String?) {
        val intent = Intent(this, Home::class.java)
        if (email != null && email.equals(admin_email, ignoreCase = true)) {
            intent.putExtra("open_admin_home", true)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    // private fun updateUI(user: FirebaseUser?) {}
}
