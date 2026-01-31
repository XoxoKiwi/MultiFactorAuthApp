package com.example.multifactorauthapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.multifactorauthapp.PatternView.OnPatternListener
import kotlin.math.*

class PatternAuthActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var patternView: PatternView
    private lateinit var prefs: SharedPreferences

    private var failedAttempts = 0
    private var isSuccess = false
    private var lockoutTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pattern_auth)

        tvStatus = findViewById(R.id.tvPatternStatus)
        patternView = findViewById(R.id.patternView)
        prefs = getSharedPreferences("MFA_PREFS", Context.MODE_PRIVATE)

        checkExistingLockout()

        patternView.setOnPatternListener(object : OnPatternListener {
            override fun onPatternDetected(ids: List<Int>, timestamps: List<Long>) {
                if (!patternView.isEnabled) return

                // 1. Hash the shape
                val rawPattern = ids.joinToString("-")
                val hashedPattern = SecurityUtils.hash(rawPattern)

                // 2. Calculate Rhythm Profile (Mean + StdDev)
                val diffs = ArrayList<Long>()
                for (i in 0 until timestamps.size - 1) {
                    diffs.add(timestamps[i+1] - timestamps[i])
                }

                val meanSpeed = if(diffs.isNotEmpty()) diffs.average() else 0.0
                val stdDev = QIFNN.calculateStdDev(diffs, meanSpeed)
                val currentProfile = QIFNN.KeystrokeProfile(meanSpeed, stdDev)

                if (prefs.contains("PATTERN_HASH")) {
                    verifyPattern(hashedPattern, currentProfile)
                } else {
                    enrollPattern(hashedPattern, currentProfile)
                }
            }
        })
    }

    private fun enrollPattern(hash: String, profile: QIFNN.KeystrokeProfile) {
        prefs.edit()
            .putString("PATTERN_HASH", hash)
            .putFloat("PATTERN_MEAN", profile.meanFlight.toFloat())
            .putFloat("PATTERN_STD", profile.stdFlight.toFloat())
            .apply()

        tvStatus.text = "Pattern Enrolled! Verify now."
        patternView.clearPattern()
        recreate()
    }

    private fun verifyPattern(inputHash: String, inputProfile: QIFNN.KeystrokeProfile) {
        val storedHash = prefs.getString("PATTERN_HASH", "")

        // 1. Check Shape
        if (inputHash != storedHash) {
            handleFailure("Wrong Shape!")
            return
        }

        // 2. Check Rhythm using QIFNN Brain
        val storedProfile = QIFNN.KeystrokeProfile(
            prefs.getFloat("PATTERN_MEAN", 0f).toDouble(),
            prefs.getFloat("PATTERN_STD", 0f).toDouble()
        )

        if (QIFNN.verify(inputProfile, storedProfile, failedAttempts)) {
            isSuccess = true

            // 3. Adaptive Learning: Update the profile on success
            val updated = QIFNN.updateProfile(storedProfile, inputProfile)
            prefs.edit()
                .putFloat("PATTERN_MEAN", updated.meanFlight.toFloat())
                .putFloat("PATTERN_STD", updated.stdFlight.toFloat())
                .apply()

            startActivity(Intent(this, KeystrokeAuthActivity::class.java))
            finish()
        } else {
            handleFailure("Rhythm Mismatch! (Too shaky or slow)")
        }
    }

    private fun handleFailure(reason: String) {
        failedAttempts++
        patternView.clearPattern()

        if (failedAttempts >= 3) {
            val lockoutDuration = 30000L
            val unlockTime = System.currentTimeMillis() + lockoutDuration
            prefs.edit().putLong("LOCKOUT_END_TIME", unlockTime).apply()
            startLockoutTimer(lockoutDuration)
        } else {
            tvStatus.text = "$reason (Attempts: $failedAttempts/3)"
        }
    }

    private fun checkExistingLockout() {
        val unlockTime = prefs.getLong("LOCKOUT_END_TIME", 0L)
        val remaining = unlockTime - System.currentTimeMillis()
        if (remaining > 0) startLockoutTimer(remaining)
    }

    private fun startLockoutTimer(durationMillis: Long) {
        patternView.isEnabled = false
        lockoutTimer?.cancel()
        lockoutTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvStatus.text = "LOCKED OUT: ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                prefs.edit().remove("LOCKOUT_END_TIME").apply()
                failedAttempts = 0
                patternView.isEnabled = true
                tvStatus.text = "Try Again"
            }
        }.start()
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