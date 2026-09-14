package com.example.donatedrop

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.donatedrop.models.NearbyUser
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import org.imperiumlabs.geofirestore.GeoFirestore
import org.imperiumlabs.geofirestore.GeoQuery
import org.imperiumlabs.geofirestore.listeners.GeoQueryDataEventListener


class NearbySearch : AppCompatActivity() {

    companion object{
        private const val TAG = "NearbySearch"
        private const val ADMIN_UID = "vTg0tRNCwlMU8xwcZjLIo3y5vA12"
    }
    private val REQUEST_LOCATION_PERMISSION = 3001
    private val SEARCH_RADIUS_KM = 40.0
    private val THRESHOLD_METERS = 40.0

    private lateinit var fusedClient: com.google.android.gms.location.FusedLocationProviderClient
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var geoFirestore: GeoFirestore
    private var activeGeoQuery: GeoQuery? = null

    private var currentBloodFilter: String = ""
    private lateinit var progressBar4: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private val adapter = NearbyAdapter()


    private val geoResults = mutableMapOf<String, NearbyUser>()
    private lateinit var svBlood: SearchView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nearby_search) // your layout - must contain rvNearby

        recyclerView = findViewById(R.id.rvNearby)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.isNestedScrollingEnabled = false
        progressBar4 = findViewById(R.id.progressBar4)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        geoFirestore = GeoFirestore(firestore.collection("users"))
        svBlood = findViewById<SearchView>(R.id.searchView)



        if (hasLocationPermission()) {
            fetchLocationAndStartQuery()
        } else {
            requestLocationPermission()
        }


        svBlood.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentBloodFilter = query?.trim() ?: ""
                updateAdapterFromResults()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentBloodFilter = newText?.trim() ?: ""
                updateAdapterFromResults()
                return true
            }


        })
        svBlood.clearFocus()

    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStart(){
        super.onStart()
        updateCurrentUserLocationIfNeeded()
    }
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateCurrentUserLocationIfNeeded()
            } else {
                Toast.makeText(this, "Location permission required to find nearby users.", Toast.LENGTH_LONG).show()
            }
        }
    }

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

    @OptIn(UnstableApi::class)
    private fun compareAndUpdateLocationIfNeeded(uid: String, deviceLocation: Location) {
        val userDocRef = firestore.collection("users").document(uid)
        userDocRef.get()
            .addOnSuccessListener { doc ->
                val oldLat = doc.getDouble("Latitude")
                val oldLng = doc.getDouble("Longitude")

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

    @OptIn(UnstableApi::class)
    private fun saveLocationToUserDocAndGeo(userId: String, docRef: DocumentReference, lat: Double, lng: Double) {
        // a) write plain fields so you can debug in console
        val locMap = mapOf(
            "Latitude" to lat,
            "Longitude" to lng,
            "LocationUpdatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(locMap, SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "Wrote lat/lng to users/$userId") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to write lat/lng: ${e.message}") }

        // b) update GeoFirestore index (callback-style)
        try {
            val gp = GeoPoint(lat, lng)
            geoFirestore.setLocation(userId, gp, object : GeoFirestore.CompletionCallback {
                override fun onComplete( exception: Exception?) {
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
    @OptIn(UnstableApi::class)
    private fun fetchLocationAndStartQuery() {
        showLoading()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            hideLoading()
            return
        }

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    startGeoQuery(location.latitude, location.longitude, SEARCH_RADIUS_KM)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last: Location? ->
                        if (last != null) {
                            startGeoQuery(last.latitude, last.longitude, SEARCH_RADIUS_KM)
                        } else {
                            hideLoading()
                            Toast.makeText(this, "Unable to obtain location. Turn on GPS and try again.", Toast.LENGTH_LONG).show()
                        }
                    }.addOnFailureListener { ex ->
                        hideLoading()
                        Toast.makeText(this, "Unable to obtain location.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener { ex ->
                hideLoading()
                Toast.makeText(this, "Unable to get location: ${ex.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun startGeoQuery(centerLat: Double, centerLng: Double, radiusKm: Double) {

        activeGeoQuery?.removeAllListeners()
        geoResults.clear()
        updateAdapterFromResults()

        val center = GeoPoint(centerLat, centerLng)
        val geoQuery = geoFirestore.queryAtLocation(center, radiusKm)
        activeGeoQuery = geoQuery

        geoQuery.addGeoQueryDataEventListener(object : GeoQueryDataEventListener {
            override fun onDocumentEntered(documentSnapshot: com.google.firebase.firestore.DocumentSnapshot, location: GeoPoint) {
                val id = documentSnapshot.id
                if (id == FirebaseAuth.getInstance().currentUser?.uid || id == ADMIN_UID) return
                val name = documentSnapshot.getString("Name") ?: "Unknown"
                val blood = documentSnapshot.getString("Blood Group") ?: ""
                val available = documentSnapshot.getBoolean("Available") ?: true
                val phone = documentSnapshot.getString("Phone") ?: ""
                val lastupdated = documentSnapshot.getTimestamp("LocationUpdatedAt")
                val avatarUrl = documentSnapshot.getString("avatarUrl")

                val dist = FloatArray(1)
                Location.distanceBetween(centerLat, centerLng, location.latitude, location.longitude, dist)

                val user = NearbyUser(
                    id = id,
                    name = name,
                    bloodGroup = blood,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    distanceMeters = dist[0].toDouble(),
                    available = available,
                    phone = phone,
                    lastupdatedat = lastupdated?.toDate(),
                    image = avatarUrl
                )
                geoResults[id] = user
                updateAdapterFromResults()
            }

            override fun onDocumentExited(documentSnapshot: com.google.firebase.firestore.DocumentSnapshot) {
                geoResults.remove(documentSnapshot.id)
                updateAdapterFromResults()
            }

            override fun onDocumentMoved(documentSnapshot: com.google.firebase.firestore.DocumentSnapshot, location: GeoPoint) {
                val id = documentSnapshot.id
                val existing = geoResults[id]
                if (existing != null) {
                    val dist = FloatArray(1)
                    Location.distanceBetween(centerLat, centerLng, location.latitude, location.longitude, dist)
                    geoResults[id] = existing.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        distanceMeters = dist[0].toDouble()
                    )
                    updateAdapterFromResults()
                }
            }

            override fun onDocumentChanged(documentSnapshot: com.google.firebase.firestore.DocumentSnapshot, location: GeoPoint) {

                val id = documentSnapshot.id
                val existing = geoResults[id]
                if (existing != null) {
                    geoResults[id] = existing.copy(
                        name = documentSnapshot.getString("Name") ?: existing.name,
                        bloodGroup = documentSnapshot.getString("Blood Group") ?: existing.bloodGroup,
                        available = documentSnapshot.getBoolean("Available") ?: existing.available,
                        phone = documentSnapshot.getString("Phone") ?: existing.phone,
                        lastupdatedat = documentSnapshot.getTimestamp("LocationUpdatedAt")?.toDate(),
                        image = documentSnapshot.getString("avatarUrl")
                    )
                    updateAdapterFromResults()
                }
            }

            @OptIn(UnstableApi::class)
            override fun onGeoQueryReady() {

                hideLoading()
            }

            override fun onGeoQueryError(exception: Exception) {
                hideLoading()
                Toast.makeText(this@NearbySearch, "GeoQuery error: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
    private fun normalize(s: String?): String {
        return s?.replace("\\s+".toRegex(), "")?.lowercase()?.trim() ?: ""
    }

    private fun updateAdapterFromResults() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val filterNorm = normalize(currentBloodFilter)

        val filterSortedList = geoResults.values
            .filter { it.id != uid && it.id != ADMIN_UID}
            .filter { user ->
                if (filterNorm.isBlank()) return@filter true
                val bgNorm = normalize(user.bloodGroup)

                bgNorm.contains(filterNorm)
            }
            .sortedBy { it.distanceMeters }
            .toList()
        runOnUiThread {
            adapter.submitList(filterSortedList)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeGeoQuery?.removeAllListeners()
        hideLoading()
    }

    private fun showLoading() {
        runOnUiThread {
            progressBar4.visibility = View.VISIBLE
            recyclerView.visibility = View.INVISIBLE
        }
    }

    private fun hideLoading() {
        runOnUiThread {
            progressBar4.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

}

