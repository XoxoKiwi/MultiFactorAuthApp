package com.example.multifactorauthapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt
import java.security.MessageDigest

class PatternAuthActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var patternView: PatternView
    private lateinit var prefs: SharedPreferences

    private val enrollSamples = mutableListOf<DoubleArray>()
    private var attempts = 0
    private var locked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pattern_auth)

        statusText = findViewById(R.id.tvPatternStatus)
        patternView = findViewById(R.id.patternView)
        prefs = getSharedPreferences("pattern_auth", MODE_PRIVATE)

        updateStatus()

        patternView.onPatternComplete = { pattern, times ->

            if (locked) {
                reset()
            } else if (pattern.size < 4 || times.size < 2) {
                statusText.text = "Draw naturally"
                reset()
            } else {
                val hash = hash(pattern.joinToString("-"))
                val features = extractFeatures(times)

                val enrolled = prefs.getBoolean("PATTERN_ENROLLED", false)

                if (!enrolled) {
                    handleEnrollment(hash, features)
                } else {
                    handleVerification(hash, features)
                }
            }
        }
    }

    // ---------------- ENROLLMENT ----------------

    private fun handleEnrollment(hash: String, features: DoubleArray) {
        val storedHash = prefs.getString("PATTERN_HASH", null)

        if (enrollSamples.isEmpty()) {
            prefs.edit().putString("PATTERN_HASH", hash).apply()
        } else if (hash != storedHash) {
            enrollSamples.clear()
            statusText.text = "Pattern mismatch. Restart enrollment"
            reset()
            return
        }

        enrollSamples.add(features)
        statusText.text = "Enroll pattern (${enrollSamples.size}/3)"
        reset()

        if (enrollSamples.size == 3) {
            val avg = DoubleArray(3) { i ->
                enrollSamples.map { it[i] }.average()
            }

            prefs.edit()
                .putString("PATTERN_PROFILE", avg.joinToString(","))
                .putBoolean("PATTERN_ENROLLED", true)
                .apply()

            enrollSamples.clear()
            statusText.text = "Verify your pattern"
        }
    }

    // ---------------- VERIFICATION ----------------

    private fun handleVerification(hash: String, features: DoubleArray) {
        val storedHash = prefs.getString("PATTERN_HASH", null)
        val profileStr = prefs.getString("PATTERN_PROFILE", null) ?: return

        if (hash != storedHash) {
            failAttempt()
            return
        }

        val ref = profileStr.split(",").map { it.toDouble() }.toDoubleArray()

        var sum = 0.0
        for (i in features.indices) {
            val d = features[i] - ref[i]
            sum += d * d
        }

        if (sqrt(sum) < 1.0) {
            attempts = 0
            startActivity(Intent(this, KeystrokeAuthActivity::class.java))
            finish()
        } else {
            failAttempt()
        }
    }

    // ---------------- LOCKOUT ----------------

    private fun failAttempt() {
        attempts++
        if (attempts >= 3) {
            locked = true
            object : CountDownTimer(30_000, 1_000) {
                override fun onTick(ms: Long) {
                    statusText.text = "Locked. Try again in ${ms / 1000}s"
                }

                override fun onFinish() {
                    locked = false
                    attempts = 0
                    statusText.text = "Verify your pattern"
                }
            }.start()
        } else {
            statusText.text = "Wrong pattern (${3 - attempts} attempts left)"
            reset()
        }
    }

    // ---------------- UTILS ----------------

    private fun extractFeatures(times: List<Long>): DoubleArray {
        val gaps = times.zipWithNext { a, b -> b - a }
        val mean = gaps.average()
        val std = sqrt(gaps.map { (it - mean) * (it - mean) }.average())
        val total = times.last() - times.first()

        return doubleArrayOf(
            mean / 300.0,
            std / 200.0,
            total / 3000.0
        )
    }

    private fun reset() {
        patternView.postDelayed({ patternView.clearPattern() }, 400)
    }

    private fun updateStatus() {
        val enrolled = prefs.getBoolean("PATTERN_ENROLLED", false)
        statusText.text =
            if (enrolled) "Verify your pattern"
            else "Enroll pattern (1/3)"
    }

    private fun hash(s: String): String {
        val d = MessageDigest.getInstance("SHA-256")
        return d.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
