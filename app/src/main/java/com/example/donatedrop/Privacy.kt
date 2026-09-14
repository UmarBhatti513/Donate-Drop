package com.example.donatedrop

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class Privacy : AppCompatActivity() {

    // private lateinit var switchProfile: SwitchMaterial
    // private lateinit var switchHistory: SwitchMaterial
    // private lateinit var switchLocation: SwitchMaterial
    // private lateinit var btnPrivacyPolicy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)

        /*
        // Toolbar back arrow
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarPrivacy)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Switches
        switchProfile  = findViewById(R.id.switchProfile)
        switchHistory  = findViewById(R.id.switchHistory)
        switchLocation = findViewById(R.id.switchLocation)
        btnPrivacyPolicy = findViewById(R.id.btnPrivacyPolicy)

        // Load saved prefs
        val prefs = getSharedPreferences("privacy_prefs", MODE_PRIVATE)
        switchProfile.isChecked  = prefs.getBoolean("profile_visible", true)
        switchHistory.isChecked  = prefs.getBoolean("history_visible", false)
        switchLocation.isChecked = prefs.getBoolean("location_enabled", true)

        // Save on toggle
        switchProfile.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("profile_visible", checked).apply()
        }
        switchHistory.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("history_visible", checked).apply()
        }
        switchLocation.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("location_enabled", checked).apply()
        }

        // Privacy Policy button
        btnPrivacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.donatedrop.app/privacy"))
            startActivity(intent)
        }
        */
    }
}