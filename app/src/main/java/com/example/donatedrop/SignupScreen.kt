package com.example.donatedrop

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import org.imperiumlabs.geofirestore.GeoFirestore


class SignupScreen : AppCompatActivity() {

    companion object{
        private const val TAG = "SignupScreen"
    }
    private val REQUEST_LOCATION_PERMISSION = 2001
    private val ADMIN_EMAIL = "developerbhatti24@gmail.com"
    // UI references
    private lateinit var tilFullName: TextInputLayout
    private lateinit var etFullName: TextInputEditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etPassword: TextInputEditText
    private lateinit var tilPhone: TextInputLayout
    private lateinit var etPhone: TextInputEditText
    private lateinit var spinnerBloodGroup: MaterialAutoCompleteTextView
    private lateinit var cbTerms: CheckBox
    private lateinit var toggleGroupGender: MaterialButtonToggleGroup
    private lateinit var btnSignUp: MaterialButton
    private lateinit var tvSignIn: TextView
    private lateinit var progressbar: View

    private lateinit var fauth: FirebaseAuth // Initialize Firebase Auth
    private lateinit var fstore: FirebaseFirestore

    private lateinit var fusedClient: com.google.android.gms.location.FusedLocationProviderClient
    private lateinit var geoFirestoreUsers: GeoFirestore
    private lateinit var GeoFirestoreAdmins: GeoFirestore

    private var lastCreatedUserId: String? = null


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1) Inflate the XML
        setContentView(R.layout.activity_signup_screen)

        // 2) Find everything by ID
        tilFullName      = findViewById(R.id.tilFullName)
        etFullName       = findViewById(R.id.etFullName)
        tilEmail         = findViewById(R.id.tilEmail)
        etEmail          = findViewById(R.id.etEmail)
        tilPassword      = findViewById(R.id.tilPassword)
        etPassword       = findViewById(R.id.etPassword)
        tilPhone         = findViewById(R.id.tilPhone)
        etPhone          = findViewById(R.id.etPhone)
        spinnerBloodGroup    = findViewById(R.id.spinnerBloodGroup)
        cbTerms              = findViewById(R.id.cbTerms)
        toggleGroupGender    = findViewById(R.id.toggleGroupGender)
        btnSignUp        = findViewById(R.id.btnSignUp)
        tvSignIn         = findViewById(R.id.tvSignIn)
        progressbar = findViewById(R.id.progressBar)
        // 3) Initialize views
        setupBloodGroupSpinner()
        setupTermsClickable()
        setupGenderToggleListener()

        fauth = FirebaseAuth.getInstance() // Initialize Firebase Auth
        fstore = FirebaseFirestore.getInstance()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        geoFirestoreUsers = GeoFirestore(fstore.collection("users"))
        GeoFirestoreAdmins = GeoFirestore(fstore.collection("Admin"))

        if (!hasLocationPermission()) requestLocationPermission()

        val currentUser = fauth.currentUser
        if (currentUser != null) {
            navigateBasedOnAdminEmail(currentUser.email)
            return
        }

        btnSignUp.setOnClickListener { attemptSignup() }


        tvSignIn.setOnClickListener {
            startActivity(Intent(this, LoginScreen::class.java))
            finish()
        }
    }

    private fun setupBloodGroupSpinner() {
        val auto = findViewById<MaterialAutoCompleteTextView>(R.id.spinnerBloodGroup)

        // Use the same array you already have (but ideally remove a "Select..." hint entry when using exposed dropdown)
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.blood_groups,              // remove "Select Blood Group" if present
            android.R.layout.simple_list_item_1
        )
        auto.setAdapter(adapter)

        auto.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val selected = parent.getItemAtPosition(position).toString()
                Log.d("SignupScreen", "Blood group: $selected")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupTermsClickable() {
        val fullText = getString(R.string.agree_terms)
        val ss = SpannableString(fullText)
        val phrase = "Terms & Conditions"
        val start = fullText.indexOf(phrase)
        if (start >= 0) {
            ss.setSpan(object: ClickableSpan() {
                override fun onClick(widget: View) {
                    val url = getString(R.string.terms_url)
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }, start, start + phrase.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            cbTerms.text = ss
            cbTerms.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun setupGenderToggleListener() {
        toggleGroupGender.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val gender = when (checkedId) {
                    R.id.btnMale   -> getString(R.string.gender_male)
                    R.id.btnFemale -> getString(R.string.gender_female)
                    R.id.btnOther  -> getString(R.string.gender_other)
                    else           -> ""
                }
                Log.d("SignupActivity", "Gender: $gender")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun attemptSignup() {
        // Gather inputs
        val name      = etFullName.text?.toString()?.trim().orEmpty()
        val email     = etEmail.text?.toString()?.trim().orEmpty()
        val password  = etPassword.text?.toString().orEmpty()
        val phone     = etPhone.text?.toString()?.trim().orEmpty()
        val bloodGrp  = spinnerBloodGroup.text?.toString() ?: ""
        val agreed    = cbTerms.isChecked
        val genderId  = toggleGroupGender.checkedButtonId
        val gender    = when (genderId) {
            R.id.btnMale   -> getString(R.string.gender_male)
            R.id.btnFemale -> getString(R.string.gender_female)
            R.id.btnOther  -> getString(R.string.gender_other)
            else           -> ""
        }

        // Simple validation
        if (name.isEmpty()) {
            tilFullName.error = "Name is Required"
            etFullName.requestFocus()
            return
        } else tilFullName.error = null

        if (email.isEmpty()) {
            tilEmail.error = "Email is Required"
            etEmail.requestFocus()
            return
        } else tilEmail.error = null

        if (password.isEmpty()) {
            tilPassword.error = "Password is Required"
            etPassword.requestFocus()
            return
        } else tilPassword.error = null

        if (password.length < 6) {
            tilPassword.error = "Password must be 6 digits long"
            etPassword.requestFocus()
            return
        } else tilPassword.error = null

        if (bloodGrp.isEmpty()) {
            Toast.makeText(this, "Select your Blood Group", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.isEmpty()) {
            tilPhone.error = "Phone is Required"
            etPhone.requestFocus()
            return
        } else tilPhone.error = null

        if (gender.isEmpty()) {
            Toast.makeText(this, "Select your Gender", Toast.LENGTH_SHORT).show()
            return
        }

        if (!agreed) {
            Toast.makeText(this, "You must agree to Terms & Conditions", Toast.LENGTH_SHORT).show()
            return
        }
        progressbar.setVisibility(View.VISIBLE)


        fauth.createUserWithEmailAndPassword(email, password).addOnCompleteListener {
            progressbar.setVisibility(View.GONE)
            if (!it.isSuccessful) {
                Toast.makeText(this@SignupScreen, "Error creating account: ${it.exception?.message ?: "Unknown"}", Toast.LENGTH_SHORT).show()
                return@addOnCompleteListener
            }

            // Auth user created successfully
            val userId = fauth.currentUser!!.uid

            // If admin email -> write only to Admin collection, then save location & navigate to Home(admin)
            if (email.equals(ADMIN_EMAIL, ignoreCase = true)) {
                val adminData = hashMapOf(
                    "uid" to userId,
                    "Name" to name,
                    "Email" to email,
                    "Phone" to phone,
                    "Blood Group" to bloodGrp,
                    "Gender" to gender,
                    "CreatedAt" to FieldValue.serverTimestamp()
                )

                val adminDocRef = fstore.collection("Admin").document(userId)
                adminDocRef.set(adminData)
                    .addOnSuccessListener {
                        Toast.makeText(this@SignupScreen, "Admin account created", Toast.LENGTH_SHORT).show()

                        // if have permission, capture location and save; otherwise request permission (user can update later)
                        if (hasLocationPermission()) {
                            captureAndSaveLocationForAdmin(userId, adminDocRef)
                        } else {
                            requestLocationPermission()
                        }

                        // navigate to Home and tell it to open admin home
                        val intent = Intent(this@SignupScreen, Home::class.java).apply {
                            putExtra("open_admin_home", true)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this@SignupScreen, "Admin doc creation failed: ${e.message}", Toast.LENGTH_LONG).show()
                        // still attempt to navigate for admin to continue
                        val intent = Intent(this@SignupScreen, Home::class.java).apply {
                            putExtra("open_admin_home", true)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finish()
                    }

            } else {
                // Normal user - write to users collection (no password)
                val user = hashMapOf(
                    "Name" to name,
                    "Email" to email,
                    "Phone" to phone,
                    "Blood Group" to bloodGrp,
                    "Gender" to gender,
                    "Available" to true,
                    "CreatedAt" to FieldValue.serverTimestamp()
                )

                val documentReference = fstore.collection("users").document(userId)
                documentReference.set(user)
                    .addOnSuccessListener {
                        Toast.makeText(this@SignupScreen, "User Created", Toast.LENGTH_SHORT).show()

                        // Save location if permission available (does not block navigation)
                        if (hasLocationPermission()) {
                            captureAndSaveLocation(userId, documentReference)
                        } else {
                            requestLocationPermission()
                        }

                        // Navigate to Home (regular user)
                        val intent = Intent(this@SignupScreen, Home::class.java)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this@SignupScreen, "Error saving user: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION_PERMISSION)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun captureAndSaveLocation(userId: String, userDocRef: DocumentReference) {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    saveLocationToUserDocAndGeo(userId, userDocRef, location.latitude, location.longitude)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            saveLocationToUserDocAndGeo(userId, userDocRef, last.latitude, last.longitude)
                        } else {
                            Toast.makeText(this, "Could not get location now. You can update it from profile.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun captureAndSaveLocationForAdmin(userId: String, adminDocRef: DocumentReference) {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    saveLocationToAdminDocAndGeo(userId, adminDocRef, location.latitude, location.longitude)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            saveLocationToAdminDocAndGeo(userId, adminDocRef, last.latitude, last.longitude)
                        } else {
                            Toast.makeText(this, "Could not get location now. You can update it from profile.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun saveLocationToUserDocAndGeo(userId: String, docRef: DocumentReference, lat: Double, lng: Double) {
        // 1) Save plain fields on the user doc (so you can debug easily)
        val loc = mapOf("Latitude" to lat, "Longitude" to lng)
        docRef.set(loc, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Saved lat/lng to user doc")
            }.addOnFailureListener { e ->
                // try update as fallback
                docRef.update(loc).addOnFailureListener { /* log */ }
            }

        // 2) Save to GeoFirestore index (support both callback-style and Task-style APIs)
        val gp = GeoPoint(lat, lng)
        try {
            geoFirestoreUsers.setLocation(userId, gp, object : GeoFirestore.CompletionCallback {
                override fun onComplete(exception: Exception?) {
                    if (exception != null) {
                        Log.e(TAG, "GeoFirestore setLocation failed for users: ${exception.message}")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating GeoFirestore users: ${e.message}")
        }
    }

    private fun saveLocationToAdminDocAndGeo(userId: String, docRef: DocumentReference, lat: Double, lng: Double) {
        // 1) Save plain fields on the admin doc
        val loc = mapOf("Latitude" to lat, "Longitude" to lng)
        docRef.set(loc, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Saved lat/lng to admin doc")
            }.addOnFailureListener { e ->
                // try update as fallback
                docRef.update(loc).addOnFailureListener { inner -> Log.e(TAG, "Failed to update admin location: ${inner.message}") }
            }

        // 2) Update GeoFirestore index for Admin collection
        val gp = GeoPoint(lat, lng)
        try {
            GeoFirestoreAdmins.setLocation(userId, gp, object : GeoFirestore.CompletionCallback {
                override fun onComplete(exception: Exception?) {
                    if (exception != null) {
                        Log.e(TAG, "GeoFirestore setLocation failed for Admins: ${exception.message}")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating GeoFirestore admins: ${e.message}")
        }
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



}
