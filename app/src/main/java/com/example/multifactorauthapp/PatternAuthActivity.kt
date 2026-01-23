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

    // Security Vars
    private var failedAttempts = 0
    private var isSuccess = false
    private var lockoutTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pattern_auth)

        tvStatus = findViewById(R.id.tvPatternStatus)
        patternView = findViewById(R.id.patternView)
        prefs = getSharedPreferences("MFA_PREFS", Context.MODE_PRIVATE)

        checkExistingLockout() // CHECK LOCKOUT ON STARTUP

        patternView.setOnPatternListener(object : OnPatternListener {
            override fun onPatternDetected(ids: List<Int>, timestamps: List<Long>) {
                if (!patternView.isEnabled) return

                val hash = ids.joinToString("-")
                val diffs = ArrayList<Long>()
                for (i in 0 until timestamps.size - 1) {
                    diffs.add(timestamps[i+1] - timestamps[i])
                }

                val meanSpeed = diffs.average()

                if (prefs.contains("PATTERN_HASH")) {
                    verifyPattern(hash, meanSpeed)
                } else {
                    prefs.edit().putString("PATTERN_HASH", hash)
                        .putFloat("PATTERN_SPEED", meanSpeed.toFloat()).apply()
                    tvStatus.text = "Pattern Saved! Draw again to verify."
                    patternView.clearPattern()
                    recreate()
                }
            }
        })
    }

    private fun checkExistingLockout() {
        val unlockTime = prefs.getLong("LOCKOUT_END_TIME", 0L)
        val remaining = unlockTime - System.currentTimeMillis()

        if (remaining > 0) {
            startLockoutTimer(remaining)
        }
    }

    private fun verifyPattern(hash: String, inputSpeed: Double) {
        val storedHash = prefs.getString("PATTERN_HASH", "")
        val storedSpeed = prefs.getFloat("PATTERN_SPEED", 0f).toDouble()

        var isValidShape = (hash == storedHash)
        var isValidRhythm = false

        if (isValidShape) {
            val speedDiff = inputSpeed - storedSpeed
            isValidRhythm = if (speedDiff > 0) {
                speedDiff < (storedSpeed * 0.15)
            } else {
                abs(speedDiff) < (storedSpeed * 0.40)
            }
        }

        if (isValidShape && isValidRhythm) {
            isSuccess = true
            startActivity(Intent(this, KeystrokeAuthActivity::class.java))
            finish()
        } else {
            handleFailure(if (!isValidShape) "Wrong Shape!" else "Rhythm Mismatch!")
        }
    }

    private fun handleFailure(reason: String) {
        failedAttempts++
        patternView.clearPattern()

        if (failedAttempts >= 3) {
            // Save Future Unlock Time
            val lockoutDuration = 30000L
            val unlockTime = System.currentTimeMillis() + lockoutDuration
            prefs.edit().putLong("LOCKOUT_END_TIME", unlockTime).apply()

            startLockoutTimer(lockoutDuration)
        } else {
            tvStatus.text = "$reason (Attempts: $failedAttempts/3)"
        }
    }

    private fun startLockoutTimer(durationMillis: Long) {
        patternView.isEnabled = false // Disable input

        lockoutTimer?.cancel()
        lockoutTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvStatus.text = "LOCKED OUT: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                prefs.edit().remove("LOCKOUT_END_TIME").apply() // Clear stored lockout
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