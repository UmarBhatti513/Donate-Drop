package com.example.donatedrop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import org.imperiumlabs.geofirestore.GeoFirestore

class SplashScreen : AppCompatActivity() {

    companion object{
        private const val TAG = "SplashScreen"
        private const val THRESHOLD_METERS = 100
    }

    private val ADMIN_EMAIL = "developerbhatti24@gmail.com"

    private val firestore = FirebaseFirestore.getInstance()
    private var REQUEST_LOCATION_PERMISSION = 1001

    private lateinit var auth: FirebaseAuth
    private lateinit var geoFirestore: GeoFirestore
    private lateinit var fusedClient: com.google.android.gms.location.FusedLocationProviderClient

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DonateDrop_Splash)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        geoFirestore = GeoFirestore(FirebaseFirestore.getInstance().collection("users"))
        auth = FirebaseAuth.getInstance()
        var user = auth.currentUser


        Handler(Looper.getMainLooper()).postDelayed(
            {
                if (user != null) {
                    // Try to update location if permission available (optional)
                    if (hasLocationPermission()) {
                        try {
                            updateCurrentUserLocationIfNeeded()
                        } catch (ex: SecurityException) {
                            Log.w(TAG, "Location update skipped: ${ex.message}")
                        }
                    } else {
                        // Request permission; onRequestPermissionsResult will handle update
                        requestLocationPermission()
                    }

                    // Navigate based on admin email (admin -> Admin screen, otherwise Home)
                    navigateBasedOnAdminEmail(user.email)
                } else {
                    startActivity(Intent(this, OnBoarding::class.java))
                    finish()
                }
            },
        2500)
    }

    private fun navigateBasedOnAdminEmail(email: String?) {
        val intent = Intent(this, Home::class.java)
        if (email != null && email.equals(ADMIN_EMAIL, ignoreCase = true)) {
            intent.putExtra("open_admin_home", true)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }

    // call this in onRequestPermissionsResult to retry if user granted
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    @OptIn(UnstableApi::class)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateCurrentUserLocationIfNeeded()
            } else {
                Log.w(TAG, "Location permission denied — cannot auto-update location")
            }
        }
    }

    /**
     * Public entry: will obtain device location and update Firestore & GeoFirestore only if changed enough.
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    @OptIn(UnstableApi::class)
    private fun updateCurrentUserLocationIfNeeded() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "No signed-in user, skipping location update")
            return
        }

        if (!hasLocationPermission()) {
            // ask permission and return — user can be prompted
            requestLocationPermission()
            return
        }

        // Try to get fresh current location (may be null). Fallback to lastLocation.
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    compareAndUpdateLocationIfNeeded(uid, location)
                } else {
                    // fallback
                    fusedClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                        if (lastLoc != null) {
                            compareAndUpdateLocationIfNeeded(uid, lastLoc)
                        } else {
                            Log.w(TAG, "No location available to update")
                        }
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "lastLocation failed: ${e.message}")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "getCurrentLocation failed: ${e.message}")
            }
    }

    /**
     * Compare the device's current coordinates with stored ones; update only if moved > THRESHOLD_METERS.
     */
    @OptIn(UnstableApi::class)
    private fun compareAndUpdateLocationIfNeeded(uid: String, deviceLocation: Location) {
        val userDocRef = firestore.collection("users").document(uid)
        userDocRef.get()
            .addOnSuccessListener { doc ->
                val oldLat = doc.getDouble("latitude")
                val oldLng = doc.getDouble("longitude")

                val shouldUpdate = if (oldLat == null || oldLng == null) {
                    true // no previous location stored -> update
                } else {
                    val dist = FloatArray(1)
                    Location.distanceBetween(oldLat, oldLng, deviceLocation.latitude, deviceLocation.longitude, dist)
                    dist[0] > THRESHOLD_METERS
                }

                if (shouldUpdate) {
                    Log.d(TAG, "Location changed sufficiently — updating Firestore/GeoFirestore for $uid")
                    saveLocationToUserDocAndGeo(uid, userDocRef, deviceLocation.latitude, deviceLocation.longitude)
                } else {
                    Log.d(TAG, "Location change below threshold — no update needed")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to read user doc before updating location: ${e.message}")
                // Optionally still try to write (safe) — do conservative update:
                saveLocationToUserDocAndGeo(uid, userDocRef, deviceLocation.latitude, deviceLocation.longitude)
            }
    }

    /**
     * Writes plain latitude/longitude fields to the user's Firestore document and updates GeoFirestore index.
     * Uses callback-style GeoFirestore API to support library versions.
     */
    @OptIn(UnstableApi::class)
    private fun saveLocationToUserDocAndGeo(userId: String, docRef: DocumentReference, lat: Double, lng: Double) {
        // a) write plain fields so you can debug in console
        val locMap = mapOf(
            "latitude" to lat,
            "longitude" to lng,
            "locationUpdatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(locMap, SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "Wrote lat/lng to users/$userId") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to write lat/lng: ${e.message}") }

        // b) update GeoFirestore index (callback-style)
        try {
            val gp = GeoPoint(lat, lng)
            geoFirestore.setLocation(userId, gp, object : GeoFirestore.CompletionCallback {
                override fun onComplete(exception: Exception?) {
                    if (exception != null) {
                        Log.e(TAG, "GeoFirestore.setLocation failed: ${exception.message}")
                    } else {
                        Log.d(TAG, "GeoFirestore.setLocation succeeded for $userId")
                    }
                }
            })
        } catch (ex: Exception) {
            Log.e(TAG, "Exception updating GeoFirestore: ${ex.message}")
        }
    }

}