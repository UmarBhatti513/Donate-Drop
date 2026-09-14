package com.example.donatedrop

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DeleteAccount : AppCompatActivity() {

    /*
    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnDeleteAccount: MaterialButton
    private lateinit var btnCancel: MaterialButton

     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_account)

        /*
        // find views
        toolbar           = findViewById(R.id.toolbar)
        btnDeleteAccount  = findViewById(R.id.btnDeleteAccount)
        btnCancel         = findViewById(R.id.btnCancel)

        // Toolbar back arrow
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        btnDeleteAccount.setOnClickListener {
            // TODO: call your API / perform deletion
            Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show()
            // e.g. navigate to login/onboarding
            finishAffinity()
        }

        btnCancel.setOnClickListener {
            finish()
        }
        */
    }
}