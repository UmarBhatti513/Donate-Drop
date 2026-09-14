package com.example.donatedrop

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.donatedrop.databinding.ActivityHomeBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class Home : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val TAG = "Home"

    private val ADMIN_EMAIL = "developerbhatti24@gmail.com"

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.bringToFront()

        val navController = findNavController(R.id.nav_host_fragment)
        val navInflater = navController.navInflater

        val intentSaysAdmin = intent?.getBooleanExtra("open_admin_home", false) ?: false
        val authEmail = FirebaseAuth.getInstance().currentUser?.email
        val authSaysAdmin = authEmail?.equals(ADMIN_EMAIL, ignoreCase = true) == true
        val isAdmin = intentSaysAdmin || authSaysAdmin

        val graphRes = if(isAdmin) R.navigation.mobile_navigation_admin else R.navigation.mobile_navigation
        val menuRes = if (isAdmin) R.menu.bottom_nav_menu_admin else R.menu.bottom_nav_menu

        val navGraph = navInflater.inflate(graphRes)
        navController.setGraph(navGraph, intent?.extras)

        val navView: BottomNavigationView = binding.navView
        navView.menu.clear()
        navView.inflateMenu(menuRes)

        // Choose start destination (prefer explicit admin dest if openAdmin and it exists)
        val topLevelIds = mutableSetOf<Int>()
        for (i in 0 until navView.menu.size()) {
            topLevelIds.add(navView.menu.getItem(i).itemId)
        }

        val appBarConfiguration = AppBarConfiguration(topLevelIds)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.title = destination.label?.toString() ?: getString(R.string.app_name)
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent ?: return
        setIntent(intent)
        // Recreate re-runs onCreate and applies the proper graph/menu for the new intent/user.
        recreate()
    }

}