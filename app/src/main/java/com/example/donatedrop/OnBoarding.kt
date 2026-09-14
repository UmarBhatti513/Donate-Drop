package com.example.donatedrop

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import android.content.Context

class OnBoarding : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_on_boarding)

        
        val getStartedButton = findViewById<MaterialButton>(R.id.btnGetStarted)
        getStartedButton.setOnClickListener {
            startActivity(Intent(this, SignupScreen::class.java))
            finish()
        }

    }
    // In your OnboardingActivity, when onboarding is complete:
    private fun completeOnboarding() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean("onboarding_completed", true)
            apply() // or commit()
        }
        // Now navigate to LoginScreen or HomeScreen as appropriate
        navigateToNextScreen()
    }

    private fun navigateToNextScreen() {
        // Check login state here or simply go to LoginScreen first
        // For now, let's assume LoginScreen is next
        startActivity(Intent(this, LoginScreen::class.java))
        finish() // Finish OnboardingActivity so user can't go back to it
    }


}