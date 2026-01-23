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

        // FIX 1: Prevent Android from restoring old password text after recreate()
        etPassword.isSaveEnabled = false

        checkExistingLockout()

        updateUI()
        setupTracking()

        btnSubmit.setOnClickListener {
            if (!etPassword.isEnabled) return@setOnClickListener

            val password = etPassword.text.toString()
            if (password.length < 4) return@setOnClickListener

            val mean = if(flightTimes.isNotEmpty()) flightTimes.average() else 0.0
            val std = QIFNN.calculateStdDev(flightTimes, mean)
            val currentProfile = QIFNN.KeystrokeProfile(mean, std)

            if (prefs.contains("KEYSTROKE_MEAN_FLIGHT")) {
                handleVerification(password, currentProfile)
            } else {
                enrollUser(password, currentProfile)
            }
        }
    }

    private fun checkExistingLockout() {
        val unlockTime = prefs.getLong("LOCKOUT_END_TIME_KEY", 0L) // Different key for Keystroke
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
        val storedPass = prefs.getString("KEYSTROKE_PASSWORD", "")
        val storedProfile = QIFNN.KeystrokeProfile(
            prefs.getFloat("KEYSTROKE_MEAN_FLIGHT", 0f).toDouble(),
            prefs.getFloat("KEYSTROKE_STD_FLIGHT", 0f).toDouble()
        )

        if (inputPass != storedPass) {
            handleFailure("Wrong Password Text!")
            return
        }

        if (QIFNN.verify(inputProfile, storedProfile, failAttempts)) {
            isSuccess = true
            val updated = QIFNN.updateProfile(storedProfile, inputProfile)
            saveProfile(inputPass, updated)
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
        saveProfile(password, profile)
        // Ensure text is cleared logic handles by recreate + isSaveEnabled=false
        recreate()
    }

    private fun saveProfile(password: String, p: QIFNN.KeystrokeProfile) {
        prefs.edit()
            .putString("KEYSTROKE_PASSWORD", password)
            .putFloat("KEYSTROKE_MEAN_FLIGHT", p.meanFlight.toFloat())
            .putFloat("KEYSTROKE_STD_FLIGHT", p.stdFlight.toFloat())
            .apply()
    }

    private fun updateUI() {
        tvStatus.text = if (prefs.contains("KEYSTROKE_MEAN_FLIGHT")) "Verify Rhythm" else "Enroll Password & Rhythm"
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