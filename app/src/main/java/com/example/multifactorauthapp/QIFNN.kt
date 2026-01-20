package com.example.multifactorauthapp

import kotlin.math.*
import kotlin.random.Random

object QIFNN {
    // Digraph weight is high, Dwell is ignored as requested
    private const val WEIGHT_FLIGHT = 6.0
    private const val ALPHA_LEARNING_RATE = 0.15 // Slower, more stable learning

    data class KeystrokeProfile(
        val meanFlight: Double,
        val stdFlight: Double,
        val meanTrigraph: Double = 0.0
    )

    fun verify(input: KeystrokeProfile, stored: KeystrokeProfile, attempts: Int): Boolean {
        // ASYMMETRIC BIAS LOGIC
        // If input is slower than stored, the "distance" is magnified
        val rawDiff = input.meanFlight - stored.meanFlight
        val biasDiff = if (rawDiff > 0) {
            rawDiff * 3.5 // HEAVY PENALTY FOR SLOW (HESITATION)
        } else {
            abs(rawDiff) * 0.8 // LENIENT FOR FAST (MUSCLE MEMORY)
        }

        // Security Escalation: Threshold shrinks as attempts increase
        val penaltyMultiplier = when (attempts) {
            0 -> 1.0
            1 -> 0.7 // 30% stricter
            else -> 0.4 // 60% stricter
        }

        val dynamicThreshold = max(stored.stdFlight * penaltyMultiplier, 15.0)

        // Jitter Check: If user is 2x more inconsistent than usual, reject
        if (input.stdFlight > stored.stdFlight * 2.0) return false

        return biasDiff < dynamicThreshold
    }

    fun updateProfile(old: KeystrokeProfile, newArgs: KeystrokeProfile): KeystrokeProfile {
        // Only called on SUCCESS to prevent "Intruder Learning"
        val newMean = old.meanFlight + ALPHA_LEARNING_RATE * (newArgs.meanFlight - old.meanFlight)
        val newStd = old.stdFlight + ALPHA_LEARNING_RATE * (newArgs.stdFlight - old.stdFlight)
        return old.copy(meanFlight = newMean, stdFlight = newStd)
    }

    fun calculateStdDev(list: List<Long>, mean: Double): Double {
        if (list.isEmpty()) return 0.0
        return sqrt(list.map { (it - mean).pow(2) }.sum() / list.size)
    }
}