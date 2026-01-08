package com.example.multifactorauthapp

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.math.sqrt

class QIFNN {

    companion object {

        // Stored keystroke profile after enrollment
        var userProfile: DoubleArray? = null

        // Encrypted model file
        private const val MODEL_FILE = "qifnn_model.enc"

        // ---------- MASTER KEY ----------
        private fun getMasterKey(context: Context): MasterKey {
            return MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }

        // ---------- SAVE MODEL (ENCRYPTED) ----------
        fun saveModel(context: Context) {
            val profile = userProfile ?: return

            try {
                val masterKey = getMasterKey(context)
                val file = File(context.filesDir, MODEL_FILE)

                val encryptedFile = EncryptedFile.Builder(
                    context,
                    file,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()

                encryptedFile.openFileOutput().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(profile)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ---------- LOAD MODEL (DECRYPTED) ----------
        fun loadModel(context: Context) {
            try {
                val file = File(context.filesDir, MODEL_FILE)
                if (!file.exists()) {
                    userProfile = null
                    return
                }

                val masterKey = getMasterKey(context)

                val encryptedFile = EncryptedFile.Builder(
                    context,
                    file,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()

                encryptedFile.openFileInput().use { fis ->
                    ObjectInputStream(fis).use { ois ->
                        userProfile = ois.readObject() as DoubleArray
                    }
                }

            } catch (e: Exception) {
                userProfile = null
            }
        }

        // ---------- CLEAR MODEL ----------
        fun clearModel(context: Context) {
            userProfile = null
            context.deleteFile(MODEL_FILE)
        }
    }

    // Threshold for accepting user (mobile keystroke tuned)
    private val threshold = 0.6

    // ---------- EUCLIDEAN DISTANCE ----------
    private fun euclideanDistance(
        input: DoubleArray,
        reference: DoubleArray
    ): Double {
        var sum = 0.0
        for (i in input.indices) {
            val diff = input[i] - reference[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    // ---------- AUTHENTICATION ----------
    fun authenticate(inputFeatures: DoubleArray): Boolean {

        val profile = userProfile ?: return false

        if (inputFeatures.size != profile.size) return false

        val distance = euclideanDistance(inputFeatures, profile)
        return distance <= threshold
    }
}
