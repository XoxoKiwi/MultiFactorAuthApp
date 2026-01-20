package com.example.multifactorauthapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AlertDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keystroke_auth)

        etPassword = findViewById(R.id.etPassword)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvStatus = findViewById(R.id.tvKeystrokeStatus)
        prefs = getSharedPreferences("MFA_PREFS", Context.MODE_PRIVATE)

        updateUI()
        setupTracking()

        btnSubmit.setOnClickListener {
            val password = etPassword.text.toString()
            if (password.length < 4) return@setOnClickListener

            val mean = if(flightTimes.isNotEmpty()) flightTimes.average() else 0.0
            val std = QIFNN.calculateStdDev(flightTimes, mean)
            val currentProfile = QIFNN.KeystrokeProfile(mean, std)

            if (prefs.contains("KEYSTROKE_MEAN_FLIGHT")) {
                handleVerification(currentProfile)
            } else {
                enrollUser(currentProfile)
            }
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

    private fun handleVerification(input: QIFNN.KeystrokeProfile) {
        val stored = QIFNN.KeystrokeProfile(
            prefs.getFloat("KEYSTROKE_MEAN_FLIGHT", 0f).toDouble(),
            prefs.getFloat("KEYSTROKE_STD_FLIGHT", 0f).toDouble()
        )

        if (QIFNN.verify(input, stored, failAttempts)) {
            // SUCCESS: Learn only now
            val updated = QIFNN.updateProfile(stored, input)
            saveProfile(updated)
            startActivity(Intent(this, AccessGrantedActivity::class.java))
            finish()
        } else {
            failAttempts++
            tvStatus.text = "Security Tightened! Attempt $failAttempts"
            resetData()
        }
    }

    private fun enrollUser(profile: QIFNN.KeystrokeProfile) {
        saveProfile(profile)
        recreate()
    }

    private fun saveProfile(p: QIFNN.KeystrokeProfile) {
        prefs.edit().putFloat("KEYSTROKE_MEAN_FLIGHT", p.meanFlight.toFloat())
            .putFloat("KEYSTROKE_STD_FLIGHT", p.stdFlight.toFloat()).apply()
    }

    private fun updateUI() {
        tvStatus.text = if (prefs.contains("KEYSTROKE_MEAN_FLIGHT")) "Verify Rhythm" else "Enroll Rhythm"
    }

    private fun resetData() {
        flightTimes.clear()
        lastKeyDownTime = 0
        etPassword.setText("")
    }
}