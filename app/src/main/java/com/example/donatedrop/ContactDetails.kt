package com.example.donatedrop

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ContactDetails : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_details)

        /*
        // toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // views
        val imgAvatar    = findViewById<ImageView>(R.id.imgAvatar)
        val tvName       = findViewById<TextView>(R.id.tvName)
        val tvBloodGrp   = findViewById<TextView>(R.id.tvBloodGroup)
        val tvLocation   = findViewById<TextView>(R.id.tvLocation)
        val tvLastDon    = findViewById<TextView>(R.id.tvLastDonated)
        val tvPhone      = findViewById<TextView>(R.id.tvPhone)
        val tvEmail      = findViewById<TextView>(R.id.tvEmail)
        val tvWhatsapp   = findViewById<TextView>(R.id.tvWhatsapp)
        val btnCall      = findViewById<Button>(R.id.btnCall)
        val btnEmail     = findViewById<Button>(R.id.btnEmail)
        val btnChat      = findViewById<Button>(R.id.btnChat)

        // get data
        val name      = intent.getStringExtra("name")     ?: "Alex Johnson"
        val bloodGrp  = intent.getStringExtra("bloodGrp") ?: "O+"
        val location  = intent.getStringExtra("location") ?: "Brooklyn, New York"
        val lastDon   = intent.getStringExtra("lastDon")  ?: "2 months ago"
        val phone     = intent.getStringExtra("phone")    ?: "+15550191234"
        val email     = intent.getStringExtra("email")    ?: "alex.johnson@email.com"
        val whatsapp  = intent.getStringExtra("whatsapp") ?: "+15550191234"

        // populate
        tvName.text         = name
        tvBloodGrp.text     = bloodGrp
        tvLocation.text     = location
        tvLastDon.text      = "Last donated $lastDon"
        tvPhone.text        = phone
        tvEmail.text        = email
        tvWhatsapp.text     = whatsapp

        // button actions
        btnCall.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        }
        btnEmail.setOnClickListener {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
        }
        btnChat.setOnClickListener {
            val uri = "https://wa.me/${whatsapp.replace("+","")}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        }
        */
    }


    /*override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }*/
}