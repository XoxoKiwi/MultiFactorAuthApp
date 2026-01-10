package com.example.multifactorauthapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.sqrt

class KeystrokeAuthActivity : AppCompatActivity() {

    private lateinit var typingField: EditText
    private lateinit var statusText: TextView
    private lateinit var metricsText: TextView
    private lateinit var prefs: SharedPreferences

    private val flightTimes = mutableListOf<Long>()
    private var lastKeyTime = 0L
    private var startTypingTime = 0L
    private var endTypingTime = 0L

    private val samples = mutableListOf<DoubleArray>()

    private var isEnrolling = true
    private var attempts = 0
    private var locked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keystroke_auth)

        typingField = findViewById(R.id.etTyping)
        statusText = findViewById(R.id.tvStatus)
        metricsText = findViewById(R.id.tvMetrics)
        prefs = getSharedPreferences("keystroke_auth", MODE_PRIVATE)

        QIFNN.loadModel(this)

        isEnrolling = !prefs.getBoolean("ENROLLED", false)
        statusText.text =
            if (isEnrolling)
                "Enrollment: type SAME password 3 times"
            else
                "Authentication: type your password"

        typingField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}

            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val now = SystemClock.elapsedRealtime()
                if (startTypingTime == 0L) startTypingTime = now
                if (lastKeyTime != 0L) flightTimes.add(now - lastKeyTime)
                lastKeyTime = now
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        typingField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && !locked) {
                endTypingTime = SystemClock.elapsedRealtime()
                val pwd = typingField.text.toString()
                if (isEnrolling) enroll(pwd) else authenticate(pwd)
                true
            } else false
        }
    }

    private fun enroll(password: String) {
        if (password.length !in 6..10 || flightTimes.size < password.length - 2) {
            statusText.text = "Type naturally"
            reset()
            return
        }

        if (samples.isEmpty()) {
            val salt = generateSalt()
            prefs.edit()
                .putString("SALT", salt)
                .putString("HASH", hash(password, salt))
                .apply()
        } else {
            val salt = prefs.getString("SALT", null)!!
            val stored = prefs.getString("HASH", null)!!
            if (hash(password, salt) != stored) {
                samples.clear()
                prefs.edit().clear().apply()
                statusText.text = "Password mismatch"
                reset()
                return
            }
        }

        val f = extractFeatures(password)
        samples.add(f)

        metricsText.text =
            "Mean:${f[0]}\nStd:${f[1]}\nTotal:${f[2]}\nSpeed:${f[3]}"

        statusText.text = "Enrollment ${samples.size}/3"
        reset()

        if (samples.size == 3) {
            val profile = DoubleArray(4) { i ->
                samples.map { it[i] }.average()
            }
            QIFNN.saveProfile(this, profile)
            prefs.edit().putBoolean("ENROLLED", true).apply()
            samples.clear()
            isEnrolling = false
            metricsText.text = ""
            statusText.text = "Authenticate now"
        }
    }

    private fun authenticate(password: String) {
        metricsText.text = ""

        val salt = prefs.getString("SALT", null) ?: return
        val stored = prefs.getString("HASH", null) ?: return

        if (hash(password, salt) != stored) {
            fail()
            reset()
            return
        }

        val success = QIFNN.authenticate(extractFeatures(password))
        if (success) {
            attempts = 0
            startActivity(Intent(this, AccessGrantedActivity::class.java))
            finish()
        } else {
            fail()
        }
        reset()
    }

    private fun fail() {
        attempts++
        if (attempts >= 3) startLockout()
        else statusText.text = "Access denied (${3 - attempts} left)"
    }

    private fun startLockout() {
        locked = true
        typingField.isEnabled = false
        object : CountDownTimer(30_000, 1_000) {
            override fun onTick(ms: Long) {
                statusText.text = "Locked ${ms / 1000}s"
            }

            override fun onFinish() {
                locked = false
                attempts = 0
                typingField.isEnabled = true
                statusText.text = "Try again"
            }
        }.start()
    }

    private fun extractFeatures(password: String): DoubleArray {
        val mean = flightTimes.average()
        val std = sqrt(flightTimes.map { (it - mean) * (it - mean) }.average())
        val total = (endTypingTime - startTypingTime).coerceAtLeast(400)
        val speed = password.length / (total / 1000.0)

        return doubleArrayOf(
            mean / 240.0,
            std / 120.0,
            total / 5200.0,
            speed / 11.5
        )
    }

    private fun reset() {
        flightTimes.clear()
        lastKeyTime = 0
        startTypingTime = 0
        endTypingTime = 0
        typingField.text.clear()
    }

    private fun generateSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    private fun hash(p: String, s: String): String {
        val spec = PBEKeySpec(p.toCharArray(), Base64.decode(s, Base64.NO_WRAP), 10000, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.encodeToString(f.generateSecret(spec).encoded, Base64.NO_WRAP)
    }
}
