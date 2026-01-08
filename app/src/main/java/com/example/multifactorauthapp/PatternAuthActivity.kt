package com.example.multifactorauthapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import java.security.MessageDigest

class PatternAuthActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var patternView: PatternView
    private lateinit var prefs: SharedPreferences

    private var isProcessing = false

    private val MAX_ATTEMPTS = 5
    private val LOCK_TIME_MS = 30_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pattern_auth)

        statusText = findViewById(R.id.tvPatternStatus)
        patternView = findViewById(R.id.patternView)
        prefs = getSharedPreferences("pattern_auth", MODE_PRIVATE)

        patternView.onPatternComplete = { pattern ->

            if (!isProcessing) {
                isProcessing = true

                val now = System.currentTimeMillis()
                val lockUntil = prefs.getLong("lock_until", 0L)

                // 🚫 Locked state
                if (now < lockUntil) {
                    statusText.text = "Too many attempts. Try again later."
                    resetPattern()
                }

                // ❌ Minimum dots
                else if (pattern.size < 4) {
                    statusText.text = getString(R.string.pattern_min_dots)
                    resetPattern()
                }

                else {

                    val enteredPattern = pattern.joinToString("-")
                    val hashedEntered = hashPattern(enteredPattern)

                    val savedHash = prefs.getString("user_pattern", null)
                    val attempts = prefs.getInt("attempts", 0)

                    // 🆕 First-time setup
                    if (savedHash == null) {

                        prefs.edit {
                            putString("user_pattern", hashedEntered)
                            putInt("attempts", 0)
                        }

                        statusText.text = getString(R.string.pattern_saved)
                        resetPattern()

                    }

                    // ✅ Correct pattern
                    else if (savedHash == hashedEntered) {

                        prefs.edit {
                            putInt("attempts", 0)
                            putLong("lock_until", 0L)
                        }

                        // 🧠 QIFNN pattern score
                        val patternScore = calculatePatternScore(pattern.size)
                        // Example integration:
                        // qifnn.addFeature("pattern_score", patternScore)

                        statusText.text = getString(R.string.pattern_correct)
                        startActivity(
                            Intent(this, KeystrokeAuthActivity::class.java)
                        )
                        finish()

                    }

                    // ❌ Wrong pattern
                    else {

                        val newAttempts = attempts + 1

                        prefs.edit {
                            putInt("attempts", newAttempts)
                        }

                        if (newAttempts >= MAX_ATTEMPTS) {
                            prefs.edit {
                                putLong("lock_until", now + LOCK_TIME_MS)
                            }
                            statusText.text = "Too many attempts. Locked for 30 seconds."
                        } else {
                            statusText.text = getString(R.string.pattern_wrong)
                        }

                        resetPattern()
                    }
                }
            }
        }
    }

    private fun resetPattern() {
        patternView.postDelayed({
            patternView.clearPattern()
            isProcessing = false
        }, 500)
    }

    // 🔐 Hash pattern using SHA-256
    private fun hashPattern(pattern: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pattern.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // 🧠 Pattern → QIFNN score
    private fun calculatePatternScore(size: Int): Double {
        return when {
            size >= 7 -> 0.9
            size >= 5 -> 0.7
            else -> 0.5
        }
    }
}
