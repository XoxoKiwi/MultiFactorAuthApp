package com.example.multifactorauthapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
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

    private val enrolledSamples = mutableListOf<DoubleArray>()
    private val requiredSamples = 5
    private var isEnrollmentComplete = false

    private val maxAttempts = 10
    private val lockDurationMs = 30_000L
    private var lockTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keystroke_auth)

        typingField = findViewById(R.id.etTyping)
        statusText = findViewById(R.id.tvStatus)
        metricsText = findViewById(R.id.tvMetrics)

        prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        isEnrollmentComplete = prefs.getBoolean("ENROLLED", false)

        QIFNN.loadModel(this)
        updateLockState()

        statusText.text =
            if (!isEnrollmentComplete)
                "Enrollment: Enter SAME password ${requiredSamples} times"
            else
                "Authentication: Enter your password"

        // ---- Collect keystroke timing ONLY ----
        typingField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isLocked()) return

                val now = SystemClock.elapsedRealtime()
                if (startTypingTime == 0L) startTypingTime = now
                if (lastKeyTime != 0L) flightTimes.add(now - lastKeyTime)
                lastKeyTime = now
            }
        })

        // ---- Process only when DONE pressed ----
        typingField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {

                if (isLocked()) return@setOnEditorActionListener true

                val password = typingField.text.toString()
                if (password.isEmpty()) return@setOnEditorActionListener true

                endTypingTime = SystemClock.elapsedRealtime()

                if (!isEnrollmentComplete) {
                    processEnrollmentSample(password)
                } else {
                    authenticateUser(password)
                }
                true
            } else false
        }
    }

    // ---------- LOCK ----------
    private fun isLocked(): Boolean =
        SystemClock.elapsedRealtime() < prefs.getLong("LOCK_UNTIL", 0L)

    private fun updateLockState() {
        val lockUntil = prefs.getLong("LOCK_UNTIL", 0L)
        val remaining = lockUntil - SystemClock.elapsedRealtime()

        if (remaining > 0) {
            typingField.isEnabled = false
            startLockCountdown(remaining)
        } else unlock()
    }

    private fun startLockCountdown(ms: Long) {
        lockTimer?.cancel()
        lockTimer = object : CountDownTimer(ms, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                statusText.text =
                    "Locked. Try again in ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                unlock()
            }
        }.start()
    }

    private fun unlock() {
        lockTimer?.cancel()
        prefs.edit()
            .putLong("LOCK_UNTIL", 0L)
            .putInt("FAIL_COUNT", 0)
            .apply()

        typingField.isEnabled = true
    }

    // ---------- ENROLLMENT ----------
    private fun processEnrollmentSample(password: String) {

        if (password.length !in 6..10) {
            statusText.text = "Password must be 6–10 characters"
            reset()
            return
        }

        if (enrolledSamples.isEmpty()) {
            val salt = generateSalt()
            val hash = hashPassword(password, salt)
            prefs.edit()
                .putString("PWD_SALT", salt)
                .putString("PWD_HASH", hash)
                .apply()
        } else {
            val salt = prefs.getString("PWD_SALT", null) ?: return
            val storedHash = prefs.getString("PWD_HASH", null) ?: return
            if (hashPassword(password, salt) != storedHash) {
                statusText.text = "Password mismatch. Restart enrollment."
                enrolledSamples.clear()
                prefs.edit().remove("PWD_SALT").remove("PWD_HASH").apply()
                reset()
                return
            }
        }

        val meanFlight = flightTimes.average()
        val stdFlight = calculateStdDev(flightTimes, meanFlight)
        val totalTime = endTypingTime - startTypingTime
        val speed = password.length / (totalTime / 1000.0)

        // ✅ NORMALIZED FEATURE VECTOR
        enrolledSamples.add(
            doubleArrayOf(
                meanFlight / 200.0,
                stdFlight / 100.0,
                totalTime / 4000.0,
                speed / 10.0
            )
        )

        reset()
        statusText.text = "Enrollment ${enrolledSamples.size}/$requiredSamples"

        if (enrolledSamples.size == requiredSamples) enrollUser()
    }

    private fun enrollUser() {
        val profile = DoubleArray(4)
        for (i in 0..3) {
            profile[i] = enrolledSamples.map { it[i] }.average()
        }

        QIFNN.userProfile = profile
        QIFNN.saveModel(this)

        prefs.edit().putBoolean("ENROLLED", true).apply()
        isEnrollmentComplete = true

        enrolledSamples.clear()
        statusText.text = "Enrollment complete. Authenticate now"
    }

    // ---------- AUTH ----------
    private fun authenticateUser(password: String) {

        if (password.length !in 6..10) {
            statusText.text = "Invalid password length"
            reset()
            return
        }

        val salt = prefs.getString("PWD_SALT", null) ?: return
        val storedHash = prefs.getString("PWD_HASH", null) ?: return

        if (hashPassword(password, salt) != storedHash) {
            handleFailure()
            reset()
            return
        }

        val meanFlight = flightTimes.average()
        val stdFlight = calculateStdDev(flightTimes, meanFlight)
        val totalTime = endTypingTime - startTypingTime
        val speed = password.length / (totalTime / 1000.0)

        // ✅ NORMALIZED FEATURE VECTOR
        val success = QIFNN().authenticate(
            doubleArrayOf(
                meanFlight / 200.0,
                stdFlight / 100.0,
                totalTime / 4000.0,
                speed / 10.0
            )
        )

        if (success) {
            prefs.edit().putInt("FAIL_COUNT", 0).apply()
            startActivity(Intent(this, AccessGrantedActivity::class.java))
            finish()
        } else {
            handleFailure()
        }

        reset()
    }

    // ---------- FAILURE ----------
    private fun handleFailure() {
        val fails = prefs.getInt("FAIL_COUNT", 0) + 1
        prefs.edit().putInt("FAIL_COUNT", fails).apply()

        if (fails >= maxAttempts) {
            prefs.edit()
                .putLong("LOCK_UNTIL",
                    SystemClock.elapsedRealtime() + lockDurationMs)
                .apply()
            updateLockState()
        } else {
            statusText.text =
                "Access denied (${maxAttempts - fails} attempts left)"
        }
    }

    // ---------- PASSWORD HASH ----------
    private fun generateSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    private fun hashPassword(password: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.encodeToString(
            factory.generateSecret(spec).encoded,
            Base64.NO_WRAP
        )
    }

    // ---------- UTIL ----------
    private fun reset() {
        flightTimes.clear()
        lastKeyTime = 0L
        startTypingTime = 0L
        endTypingTime = 0L
        typingField.text.clear()
    }

    private fun calculateStdDev(values: List<Long>, mean: Double): Double {
        var sum = 0.0
        for (v in values) sum += (v - mean) * (v - mean)
        return sqrt(sum / values.size)
    }

    override fun onDestroy() {
        super.onDestroy()
        lockTimer?.cancel()
    }
}
