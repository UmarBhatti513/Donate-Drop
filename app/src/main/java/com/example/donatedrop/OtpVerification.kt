package com.example.donatedrop

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class OtpVerification : AppCompatActivity() {

    /*private lateinit var etOtp1: EditText
    private lateinit var etOtp2: EditText
    private lateinit var etOtp3: EditText
    private lateinit var etOtp4: EditText
    private lateinit var etOtp5: EditText
    private lateinit var etOtp6: EditText

    private lateinit var tvPhoneNumber: TextView
    private lateinit var tvChangeNumber: TextView
    private lateinit var tvResend: TextView
    private lateinit var btnVerify: Button
    private lateinit var tvBackToLogin: TextView

    private var countDownTimer: CountDownTimer? = null
    private val RESEND_COUNTDOWN_MS = 30_000L  // 30 seconds

     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp_verification)

    /*    // Toolbar setup
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // Handle back arrow
        toolbar.setNavigationOnClickListener {
            finish() // or navigate up
        }

        // Find views
        etOtp1 = findViewById(R.id.etOtp1)
        etOtp2 = findViewById(R.id.etOtp2)
        etOtp3 = findViewById(R.id.etOtp3)
        etOtp4 = findViewById(R.id.etOtp4)
        etOtp5 = findViewById(R.id.etOtp5)
        etOtp6 = findViewById(R.id.etOtp6)

        tvPhoneNumber = findViewById(R.id.tvPhoneNumber)
        tvChangeNumber = findViewById(R.id.tvChangeNumber)
        tvResend = findViewById(R.id.tvResend)
        btnVerify = findViewById(R.id.btnVerify)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)

        // Get phone number from Intent extras (passed from previous screen)
        val phoneNumber = intent.getStringExtra("phone_number") ?: ""
        if (phoneNumber.isNotBlank()) {
            tvPhoneNumber.text = phoneNumber
        }

        // Setup Change number click
        tvChangeNumber.setOnClickListener {
            // e.g., finish this activity so user returns to phone input screen
            finish()
        }

        // Setup Back to Login
        tvBackToLogin.setOnClickListener {
            finish()
        }

        // Setup OTP fields behavior
        setupOtpInputs()

        // Start initial resend countdown
        startResendCountdown()

        // Setup clicking resend (only after timer finishes)
        tvResend.setOnClickListener {
            if (tvResend.isEnabled) {
                // Trigger sending OTP again
                sendOtpToPhone(phoneNumber)
                // Restart timer
                startResendCountdown()
            }
        }

        // Setup Verify button
        btnVerify.setOnClickListener {
            val otpCode = getOtpCode()
            if (otpCode.length == 6) {
                verifyOtp(otpCode)
                Toast.makeText(this, "Successfully Registered!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeScreen::class.java))
            } else {
                // show error: incomplete OTP
                // You can show a Toast or set error style
                // e.g.:
                Toast.makeText(this, "Please enter the 6-digit code", Toast.LENGTH_SHORT).show()
                indicateIncompleteOtp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    private fun setupOtpInputs() {
        val editTexts = arrayOf(etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6)
        for (i in editTexts.indices) {
            val current = editTexts[i]
            // Move to next when a digit is entered
            current.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s != null && s.length == 1) {
                        // Move to next
                        if (i < editTexts.size - 1) {
                            editTexts[i + 1].requestFocus()
                        } else {
                            // last digit entered, hide keyboard or do nothing
                            current.clearFocus()
                        }
                    }
                }
            })

            // Handle delete (backspace) to move back
            current.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                    if (current.text.isEmpty() && i > 0) {
                        editTexts[i - 1].apply {
                            requestFocus()
                            setText("") // clear previous if desired
                        }
                    }
                }
                false
            }
        }
    }

    private fun getOtpCode(): String {
        // Concatenate digits
        return listOf(
            etOtp1,
            etOtp2,
            etOtp3,
            etOtp4,
            etOtp5,
            etOtp6
        ).joinToString(separator = "") { it.text.toString().trim() }
    }

    private fun startResendCountdown() {
        tvResend.isEnabled = false
        tvResend.setTextColor(Color.DKGRAY)
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(RESEND_COUNTDOWN_MS, 1000) {
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                tvResend.text = "Didn't receive the code? Resend in ${secondsLeft}s"
            }

            @SuppressLint("SetTextI18n")
            override fun onFinish() {
                tvResend.text = "Didn't receive the code? Resend"
                tvResend.isEnabled = true
                tvResend.setTextColor(resources.getColor(R.color.colorPrimary, theme))
            }
        }.start()
    }

    private fun sendOtpToPhone(phone: String) {
        // TODO: implement sending OTP logic (e.g. call your backend / Firebase / etc.)
        // For now, you can just show a Toast or log.
        // Example:
        // Toast.makeText(this, "Sending OTP to $phone", Toast.LENGTH_SHORT).show()
    }

    private fun verifyOtp(otp: String) {
        // TODO: implement OTP verification logic (e.g. call backend)
        // If success: navigate to next screen / home
        // If failure: show error, clear fields or let user re-enter
    }

    private fun indicateIncompleteOtp() {
        // TODO: e.g., shake animation or red border on empty fields
        // For simplicity, we can just set error on first empty
        val editTexts = listOf(etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6)
        for (et in editTexts) {
            if (et.text.toString().trim().isEmpty()) {
                et.error = ""
                break
            }
        }

     */
    }
}
