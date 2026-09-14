package com.example.donatedrop

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ProfileScreen : AppCompatActivity() {

    private lateinit var fauth: FirebaseAuth
    private lateinit var fstore: FirebaseFirestore
    private var userDocListener: ListenerRegistration? = null


    @SuppressLint("MissingInflatedId", "ResourceType")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_screen)


        // Toolbar back button
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        // User info
        val email = findViewById<TextView>(R.id.email_et)
        val phone = findViewById<TextView>(R.id.phone_et)
        val ivAvatar = findViewById<ImageView>(R.id.ivAvatar)
        val tvName = findViewById<TextView>(R.id.tvName)
        val tvBlood = findViewById<TextView>(R.id.tvBloodType)
        val tvGender = findViewById<TextView>(R.id.tv_gender)
        val tvCity = findViewById<TextView>(R.id.tv_city)

        fauth = FirebaseAuth.getInstance()
        fstore = FirebaseFirestore.getInstance()

        val currentUser = fauth.currentUser
        if (currentUser == null) {
            // Not signed in — redirect or finish
            finish()
            return
        }

        val userId = fauth.currentUser!!.uid
        val documentReference = fstore.collection("users").document(userId)

        userDocListener = documentReference.addSnapshotListener { snapshot, exception ->

            exception?.let {
                return@addSnapshotListener

            }
            snapshot?.let {
                tvName.text = it.getString("Name")
                email.text = it.getString("Email")
                phone.text = it.getString("Phone")
                tvBlood.text = it.getString("Blood Group")
                tvGender.text = it.getString("Gender")
                tvCity.text = it.getString("City")

                val imageUrl = it.getString("avatarUrl")
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this).load(imageUrl).circleCrop().into(ivAvatar)
                }

            }
        }

        // Buttons
        findViewById<Button>(R.id.btnEdit).setOnClickListener {
            // launch edit‑profile flow:
            startActivity(Intent(this, EditProfile::class.java))
        }
        /*
        findViewById<Button>(R.id.btnVolunteer).setOnClickListener {
            // launch volunteer signup:
            // startActivity(Intent(this, VolunteerSignupActivity::class.java))
        }
         */

    }
}
