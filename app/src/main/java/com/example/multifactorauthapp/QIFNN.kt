package com.example.multifactorauthapp

import android.content.Context
import java.io.*
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.sqrt

object QIFNN {

    private const val MODEL_FILE = "qifnn_model.aes"
    private const val AES_MODE = "AES/CBC/PKCS5Padding"
    private const val SECRET_PASSPHRASE = "IEEE_MFA_QIFNN_2026"

    private var profile: DoubleArray? = null

    private fun getKey(): SecretKeySpec {
        val sha = MessageDigest.getInstance("SHA-256")
        return SecretKeySpec(sha.digest(SECRET_PASSPHRASE.toByteArray()), "AES")
    }

    private fun getIV(): IvParameterSpec =
        IvParameterSpec(ByteArray(16)) // deterministic (academic safe)

    fun saveProfile(context: Context, data: DoubleArray) {
        try {
            val file = File(context.filesDir, MODEL_FILE)

            val bos = ByteArrayOutputStream()
            ObjectOutputStream(bos).use { it.writeObject(data) }

            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), getIV())
            val encrypted = cipher.doFinal(bos.toByteArray())

            FileOutputStream(file).use { it.write(encrypted) }
            profile = data
        } catch (e: Exception) {
            e.printStackTrace()
            profile = null
        }
    }

    fun loadModel(context: Context) {
        try {
            val file = File(context.filesDir, MODEL_FILE)
            if (!file.exists()) return

            val encrypted = file.readBytes()
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.DECRYPT_MODE, getKey(), getIV())
            val decrypted = cipher.doFinal(encrypted)

            ObjectInputStream(ByteArrayInputStream(decrypted)).use {
                profile = it.readObject() as DoubleArray
            }
        } catch (e: Exception) {
            e.printStackTrace()
            File(context.filesDir, MODEL_FILE).delete()
            profile = null
        }
    }

    // 🔑 FINAL BALANCED DECISION
    fun authenticate(input: DoubleArray): Boolean {
        val ref = profile ?: return false

        var sum = 0.0
        for (i in input.indices) {
            val d = input[i] - ref[i]
            sum += d * d
        }

        val distance = sqrt(sum)

        // ✅ BALANCED THRESHOLD
        // < 1.2  → too strict
        // > 2.2  → too loose
        return distance < 1.75
    }
}
