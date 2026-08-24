package com.crystalolympus.crystalolympusgame.almanac

import com.crystalolympus.crystalolympusgame.BuildConfig

/**
 * Turns the arrays the build encoded back into strings. Callers only ever hand
 * it a BuildConfig field, never a literal, which leaves this object with no
 * idea what it is decoding and nothing to give away on its own.
 *
 * Three mixing schemes are implemented and a build picks one through
 * [BuildConfig.CIPHER_VARIANT], fed from `gray.codecVariant` in
 * gray.properties. Give that a different number than the previous project so
 * the algorithm shape rotates alongside its parameters; the reasoning behind
 * that lives in the kotlin_fingerprint rule.
 *
 * Both this class name and the folder holding it are per-project;
 * tools/rebrand.py rotates them.
 */
internal object Cloak {

    fun reveal(obscured: IntArray): String {
        if (obscured.isEmpty()) return ""
        val seed = BuildConfig.CIPHER_SEED
        val mult = BuildConfig.CIPHER_MULT
        val add  = BuildConfig.CIPHER_ADD
        val n = seed.size
        val out = ByteArray(obscured.size)
        for (i in obscured.indices) {
            val s = seed[i % n] and 0xFF
            val mix = when (BuildConfig.CIPHER_VARIANT) {
                1    -> (i * mult + add) and 0xFF
                2    -> ((i + 1) * mult xor add) and 0xFF
                else -> (((i * mult) and 0xFF) + add + (i shr 3)) and 0xFF
            }
            out[i] = ((obscured[i] and 0xFF) xor s xor mix).toByte()
        }
        return String(out, Charsets.UTF_8)
    }
}
