package com.example.multifactorauthapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class KeystrokeAuthActivity : AppCompatActivity() {
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSubmit: Button
    private lateinit var tvStatus: TextView
    private lateinit var prefs: SharedPreferences

    private val flightTimes = ArrayList<Long>()
    private var lastKeyDownTime: Long = 0

    private var failAttempts = 0
    private var isSuccess = false
    private var lockoutTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keystroke_auth)

        etPassword = findViewById(R.id.etPassword)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvStatus = findViewById(R.id.tvKeystrokeStatus)
        prefs = getSharedPreferences("MFA_PREFS", Context.MODE_PRIVATE)

        // Fix: Don't let Android auto-fill old password on recreate
        etPassword.isSaveEnabled = false

        checkExistingLockout()
        updateUI()
        setupTracking()

        btnSubmit.setOnClickListener {
            if (!etPassword.isEnabled) return@setOnClickListener

            val passwordRaw = etPassword.text.toString()
            if (passwordRaw.length < 4) return@setOnClickListener

            // Calculate Math (QIFNN)
            val mean = if(flightTimes.isNotEmpty()) flightTimes.average() else 0.0
            val std = QIFNN.calculateStdDev(flightTimes, mean)
            val currentProfile = QIFNN.KeystrokeProfile(mean, std)

            if (prefs.contains("KEYSTROKE_HASH")) {
                handleVerification(passwordRaw, currentProfile)
            } else {
                enrollUser(passwordRaw, currentProfile)
            }
        }
    }

    private fun checkExistingLockout() {
        val unlockTime = prefs.getLong("LOCKOUT_END_TIME_KEY", 0L)
        val remaining = unlockTime - System.currentTimeMillis()

        if (remaining > 0) {
            startLockoutTimer(remaining)
        }
    }

    private fun setupTracking() {
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentTime = System.currentTimeMillis()
                if (lastKeyDownTime != 0L) {
                    val latency = currentTime - lastKeyDownTime
                    if (latency < 1500) flightTimes.add(latency)
                }
                lastKeyDownTime = currentTime
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun handleVerification(inputPass: String, inputProfile: QIFNN.KeystrokeProfile) {
        val storedHash = prefs.getString("KEYSTROKE_HASH", "")

        // 1. Hash the Input Password
        val inputHash = SecurityUtils.hash(inputPass)

        // 2. Compare Hashes
        if (inputHash != storedHash) {
            handleFailure("Wrong Password Text!")
            return
        }

        // 3. Compare Rhythm (QIFNN)
        val storedProfile = QIFNN.KeystrokeProfile(
            prefs.getFloat("KEYSTROKE_MEAN_FLIGHT", 0f).toDouble(),
            prefs.getFloat("KEYSTROKE_STD_FLIGHT", 0f).toDouble()
        )

        if (QIFNN.verify(inputProfile, storedProfile, failAttempts)) {
            isSuccess = true
            // Update Profile (Learning)
            val updated = QIFNN.updateProfile(storedProfile, inputProfile)

            // Only update the math stats, don't need to re-save password hash if it's correct
            prefs.edit()
                .putFloat("KEYSTROKE_MEAN_FLIGHT", updated.meanFlight.toFloat())
                .putFloat("KEYSTROKE_STD_FLIGHT", updated.stdFlight.toFloat())
                .apply()

            startActivity(Intent(this, AccessGrantedActivity::class.java))
            finish()
        } else {
            handleFailure("Rhythm Mismatch! (Are you the owner?)")
        }
    }

    private fun handleFailure(reason: String) {
        failAttempts++
        resetData()

        if (failAttempts >= 3) {
            val lockoutDuration = 30000L
            val unlockTime = System.currentTimeMillis() + lockoutDuration
            prefs.edit().putLong("LOCKOUT_END_TIME_KEY", unlockTime).apply()

            startLockoutTimer(lockoutDuration)
        } else {
            tvStatus.text = "$reason (Attempts: $failAttempts/3)"
        }
    }

    private fun startLockoutTimer(durationMillis: Long) {
        etPassword.isEnabled = false
        btnSubmit.isEnabled = false

        lockoutTimer?.cancel()
        lockoutTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvStatus.text = "LOCKED OUT: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                prefs.edit().remove("LOCKOUT_END_TIME_KEY").apply()
                failAttempts = 0
                etPassword.isEnabled = true
                btnSubmit.isEnabled = true
                tvStatus.text = "Try Again"
            }
        }.start()
    }

    private fun enrollUser(password: String, profile: QIFNN.KeystrokeProfile) {
        // Hash the password before saving
        val passHash = SecurityUtils.hash(password)

        prefs.edit()
            .putString("KEYSTROKE_HASH", passHash)
            .putFloat("KEYSTROKE_MEAN_FLIGHT", profile.meanFlight.toFloat())
            .putFloat("KEYSTROKE_STD_FLIGHT", profile.stdFlight.toFloat())
            .apply()

        recreate()
    }

    private fun updateUI() {
        tvStatus.text = if (prefs.contains("KEYSTROKE_HASH")) "Verify Rhythm" else "Enroll Password & Rhythm"
    }

    private fun resetData() {
        flightTimes.clear()
        lastKeyDownTime = 0
        etPassword.setText("")
    }

    override fun onRestart() {
        super.onRestart()
        if (!isSuccess) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}