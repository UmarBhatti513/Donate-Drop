package com.example.donatedrop

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore


class CreateRequestForm : AppCompatActivity() {


    private lateinit var topAppBar: MaterialToolbar
    private lateinit var bloodTypeLayout: TextInputLayout
    private lateinit var autoCompleteBloodType: AutoCompleteTextView
    private lateinit var quantityLayout: TextInputLayout
    private lateinit var etQuantity: EditText
    private lateinit var urgencyLayout: TextInputLayout
    private lateinit var autoCompleteUrgency: AutoCompleteTextView
    private lateinit var addressLayout: TextInputLayout
    private lateinit var etAddress: EditText
    private lateinit var receiverNameLayout: TextInputLayout
    private lateinit var etReceiverName: EditText
    private lateinit var contactLayout: TextInputLayout
    private lateinit var etContact: EditText
    private lateinit var btnSubmit: MaterialButton
    private lateinit var etnotes: EditText
    private lateinit var progressBar3: ProgressBar

    private lateinit var auth: FirebaseAuth
    private lateinit var fstore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_request_form)


        // 1) Find all views by ID
        topAppBar               = findViewById(R.id.topAppBar)
        bloodTypeLayout         = findViewById(R.id.bloodTypeLayout)
        autoCompleteBloodType   = findViewById(R.id.autoCompleteBloodType)
        quantityLayout          = findViewById(R.id.quantityLayout)
        etQuantity              = findViewById(R.id.etQuantity)
        urgencyLayout           = findViewById(R.id.urgencyLayout)
        autoCompleteUrgency     = findViewById(R.id.autoCompleteUrgency)
        addressLayout           = findViewById(R.id.addressLayout)
        etAddress               = findViewById(R.id.etAddress)
        receiverNameLayout      = findViewById(R.id.receiverNameLayout)
        etReceiverName          = findViewById(R.id.etReceiverName)
        contactLayout           = findViewById(R.id.contactLayout)
        etContact               = findViewById(R.id.etContact)
        btnSubmit               = findViewById(R.id.btnSubmit)
        etnotes                 = findViewById(R.id.etnotes)
        progressBar3            = findViewById(R.id.progressBar3)

        auth = FirebaseAuth.getInstance()
        fstore = FirebaseFirestore.getInstance()

        // 2) Toolbar setup
        setSupportActionBar(topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        topAppBar.setNavigationOnClickListener { finish() }

        // 3) Populate dropdowns
        setupDropdowns()

        // 4) Submit handler
        btnSubmit.setOnClickListener { submitForm() }

    }

    private fun setupDropdowns() {
        val bloodTypes = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        val bloodAdapter = ArrayAdapter(this, R.layout.simple_list_item, bloodTypes)
        autoCompleteBloodType.setAdapter(bloodAdapter)

        val urgencyLevels = listOf("Low", "Medium", "High", "Critical")
        val urgencyAdapter = ArrayAdapter(this, R.layout.simple_list_item, urgencyLevels)
        autoCompleteUrgency.setAdapter(urgencyAdapter)
    }

    private fun submitForm() {
        // Clear errors
        bloodTypeLayout.error = null
        quantityLayout.error = null
        urgencyLayout.error = null
        addressLayout.error = null
        receiverNameLayout.error = null
        contactLayout.error = null

        // Read values
        val bloodType   = autoCompleteBloodType.text.toString().trim()
        val quantityStr = etQuantity.text.toString().trim()
        val urgency     = autoCompleteUrgency.text.toString().trim()
        val address     = etAddress.text.toString().trim()
        val name        = etReceiverName.text.toString().trim()
        val contact     = etContact.text.toString().trim()
        val notes       = etnotes.text.toString().trim()
        val progressBar = progressBar3

        var valid = true

        if (bloodType.isEmpty()) {
            bloodTypeLayout.error = "Please select blood type"
            valid = false
        }
        if (quantityStr.isEmpty()) {
            quantityLayout.error = "Please enter quantity"
            valid = false
        } else {
            val qty = quantityStr.toIntOrNull()
            if (qty == null || qty <= 0) {
                quantityLayout.error = "Enter a valid number"
                valid = false
            }
        }
        if (urgency.isEmpty()) {
            urgencyLayout.error = "Please select urgency"
            valid = false
        }
        if (address.isEmpty()) {
            addressLayout.error = "Please enter hospital address"
            valid = false
        }
        if (name.isEmpty()) {
            receiverNameLayout.error = "Please enter receiver’s name"
            valid = false
        }
        if (contact.isEmpty()) {
            contactLayout.error = "Please enter contact number"
            valid = false
        } else if (!isValidPhone(contact)) {
            contactLayout.error = "Enter a valid phone number"
            valid = false
        }

        if (!valid) return

        progressBar.visibility = ProgressBar.VISIBLE

        saveRequestToFirestore(name, contact, urgency, bloodType, quantityStr, address, notes)
        finish()

    }

    private fun isValidPhone(phone: String): Boolean {
        val digitsOnly = phone.replace("[^0-9+]".toRegex(), "")
        return digitsOnly.length >= 7 && digitsOnly.matches("^\\+?[0-9]+$".toRegex())
    }
    private fun saveRequestToFirestore(
        name: String,
        contact: String,
        urgency: String,
        bloodType: String,
        quantityStr: String,
        address: String,
        notes: String
    ) {

        // Create a document reference with an auto-generated ID, but keep the ID available:
        val requestsCol = fstore.collection("Blood Requests")
        val newDocRef = requestsCol.document() // generates a new document reference (with random id)
        val uniqueId = newDocRef.id
        val uid = auth.currentUser?.uid

        val email = auth.currentUser?.email

        // Prepare data map (use server timestamp for createdAt)
        val data = hashMapOf<String, Any?>(
            "Req_id" to uniqueId,
            "Receiver's Name" to name,
            "Receiver's Phone" to contact,
            "Hospital Address" to address,
            "Blood Type" to bloodType,
            "Quantity" to quantityStr.toInt(),
            "Urgency Level" to urgency,
            "Additional Note" to notes,
            "Created At" to FieldValue.serverTimestamp(),
            "Status" to "open",
            "Created By" to uid,
            "Requester Email" to email
        )

        // Save the document
        newDocRef.set(data)
            .addOnSuccessListener {
                progressBar3.visibility = View.GONE
                Toast.makeText(this, "Request submitted", Toast.LENGTH_SHORT).show()

                // If urgent (critical) create notifications for nearby users
                if (urgency.equals("Critical", ignoreCase = true)) {
                    // radius in kilometers — change as needed
                    val radiusKm = 10.0
                    createNearbyNotificationsForCriticalRequest(
                        requestId = uniqueId,
                        bloodType = bloodType,
                        address = address,
                        requestCreatorId = uid,
                        requestCreatorName = auth.currentUser?.displayName ?: name,
                        radiusKm = radiusKm
                    )
                }

                finish() // close activity
            }
            .addOnFailureListener { e ->
                progressBar3.visibility = View.GONE
                Toast.makeText(this, "Failed to submit: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    @OptIn(UnstableApi::class)
    private fun createNearbyNotificationsForCriticalRequest(
        requestId: String,
        bloodType: String,
        address: String,
        requestCreatorId: String?,
        requestCreatorName: String?,
        radiusKm: Double = 10.0
    ) {
        Log.d("CreateRequestForm", "createNearbyNotifications: start geocoding for address='$address'")

        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try {
                val geocoder = android.location.Geocoder(this@CreateRequestForm)
                val results = try {
                    geocoder.getFromLocationName(address, 1)
                } catch (gex: Exception) {
                    Log.w("CreateRequestForm", "Geocoder call threw: ${gex.message}", gex)
                    null
                }

                if (results == null || results.isEmpty()) {
                    Log.w("CreateRequestForm", "Geocoding returned no results for address: $address")
                    return@execute
                }

                val found = results[0]
                val reqLat = found.latitude
                val reqLon = found.longitude
                Log.d("CreateRequestForm", "Geocode success lat=$reqLat lon=$reqLon for address='$address'")

                // Fetch users and build a list of nearby UIDs (client-side filter)
                fstore.collection("users").get()
                    .addOnSuccessListener { usersSnap ->
                        Log.d("CreateRequestForm", "Fetched ${usersSnap.size()} user docs to evaluate nearby")
                        val nearbyIds = mutableSetOf<String>()

                        for (userDoc in usersSnap.documents) {
                            try {
                                val userId = userDoc.id
                                if (!requestCreatorId.isNullOrBlank() && userId == requestCreatorId) continue

                                // Read GeoPoint from common fields
                                val gp = (userDoc.get("l") as? com.google.firebase.firestore.GeoPoint)
                                    ?: (userDoc.get("g") as? com.google.firebase.firestore.GeoPoint)
                                    ?: (userDoc.get("userLocation") as? com.google.firebase.firestore.GeoPoint)

                                if (gp == null) {
                                    Log.d("CreateRequestForm", "User ${userId} has no GeoPoint, skipping")
                                    continue
                                }

                                val userLat = gp.latitude
                                val userLon = gp.longitude
                                val distanceKm = haversineDistanceKm(reqLat, reqLon, userLat, userLon)

                                if (distanceKm <= radiusKm) {
                                    nearbyIds.add(userId)
                                    Log.d("CreateRequestForm", "User $userId is within $distanceKm km (added)")
                                }
                            } catch (ex: Exception) {
                                Log.w("CreateRequestForm", "Error processing user doc ${userDoc.id}: ${ex.message}", ex)
                            }
                        }

                        if (nearbyIds.isEmpty()) {
                            Log.d("CreateRequestForm", "No nearby users found within $radiusKm km")
                            return@addOnSuccessListener
                        }

                        // Create a single notification document with the targets array
                        val notifRef = fstore.collection("Notifications").document()
                        val notif = hashMapOf<String, Any?>(
                            "targets" to nearbyIds.toList(),               // array of UIDs
                            "readBy" to listOf<String>(),                 // list of UIDs who marked read
                            "fromUserId" to requestCreatorId,
                            "fromUserName" to requestCreatorName,
                            "title" to "Urgent: $bloodType needed nearby",
                            "body" to "$bloodType needed at $address",
                            "requestId" to requestId,
                            "bloodType" to bloodType,
                            "address" to address,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "type" to "request_critical"
                        )

                        notifRef.set(notif)
                            .addOnSuccessListener {
                                Log.d("CreateRequestForm", "Single notification created id=${notifRef.id} targetsCount=${nearbyIds.size}")
                            }
                            .addOnFailureListener { e ->
                                Log.e("CreateRequestForm", "Failed to create single notification: ${e.message}", e)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("CreateRequestForm", "Failed to fetch users collection: ${e.message}", e)
                    }

            } catch (e: Exception) {
                Log.e("CreateRequestForm", "createNearbyNotifications failed: ${e.message}", e)
            }
        }
    }


    private fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth radius km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

}
