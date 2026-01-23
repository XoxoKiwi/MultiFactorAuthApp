package com.example.multifactorauthapp

import java.security.MessageDigest

object SecurityUtils {

    // Converts any string (Pattern or Password) into a SHA-256 Hash
    fun hash(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}