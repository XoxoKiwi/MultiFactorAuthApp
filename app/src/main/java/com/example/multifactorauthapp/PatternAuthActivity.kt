package com.example.multifactorauthapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.multifactorauthapp.PatternView.OnPatternListener
import kotlin.math.*

class PatternAuthActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var patternView: PatternView
    private lateinit var prefs: SharedPreferences
    private var attempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pattern_auth)

        tvStatus = findViewById(R.id.tvPatternStatus)
        patternView = findViewById(R.id.patternView)
        prefs = getSharedPreferences("MFA_PREFS", Context.MODE_PRIVATE)

        patternView.setOnPatternListener(object : OnPatternListener {
            override fun onPatternDetected(ids: List<Int>, timestamps: List<Long>) {
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
                    recreate()
                }
            }
        })
    }

    private fun verifyPattern(hash: String, inputSpeed: Double) {
        val storedHash = prefs.getString("PATTERN_HASH", "")
        val storedSpeed = prefs.getFloat("PATTERN_SPEED", 0f).toDouble()

        if (hash != storedHash) {
            tvStatus.text = "Wrong Shape!"
            return
        }

        // STRICT ASYMMETRIC BIAS for Pattern
        val speedDiff = inputSpeed - storedSpeed
        val isVerified = if (speedDiff > 0) {
            speedDiff < (storedSpeed * 0.15) // VERY STRICT SLOW LIMIT (15%)
        } else {
            abs(speedDiff) < (storedSpeed * 0.40) // LENIENT FAST LIMIT (40%)
        }

        if (isVerified) {
            startActivity(Intent(this, KeystrokeAuthActivity::class.java))
            finish()
        } else {
            attempts++
            tvStatus.text = "Rhythm Mismatch! (Too slow/hesitant)"
            patternView.clearPattern()
        }
    }
}