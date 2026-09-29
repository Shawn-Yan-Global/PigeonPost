package com.octopus.pigeon.post.data.repository

import com.octopus.pigeon.post.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Password gate for the debug menu.
 *
 * The gate keeps the debug menu, and the "hide icon" control inside it, out of
 * reach of ordinary users. It is deliberately a speed bump rather than a
 * security boundary: the expected hash is embedded in the binary, so anyone
 * holding the APK can brute force it offline. Rotating the password does not
 * change that, it only changes what has to be guessed.
 *
 * What it does buy:
 *  - the password is never stored in plaintext, so `strings` on the APK and a
 *    data backup do not reveal it
 *  - a shared device does not expose the menu by accident
 */
object DebugAuth {
    /**
     * Hex digits used by [toHex].
     *
     * Declared before anything that hashes. Object properties are initialised
     * top to bottom, so a constant used by the first property initializer has
     * to sit above it, or it is still null when that initializer runs.
     */
    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Per build salt, injected at compile time.
     *
     * Committed, and not secret, because a salt does not need to be. It is
     * injected rather than written here so the build and the app cannot drift
     * apart on how a password is hashed.
     */
    private val SALT: ByteArray = BuildConfig.DEBUG_PASSWORD_SALT.toByteArray()

    private val random = SecureRandom()

    /**
     * The default password's verifier, also injected at compile time.
     *
     * The build reads the plaintext from local.properties, which is
     * gitignored, hashes it with the same construction as [hash], and passes
     * only the result in here. Nothing in the source tree, and nothing in the
     * APK, holds the password itself. This is the fallback for an install that
     * has not chosen a password yet; DataStore takes over from there.
     */
    val DEFAULT_PASSWORD_HASH: String = BuildConfig.DEFAULT_DEBUG_PASSWORD_HASH

    /** Derives the stored verifier for [password]. */
    fun hash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(SALT)
        digest.update(password.toByteArray())
        return digest.digest().toHex()
    }

    /**
     * Compares [input] against [expectedHash] without leaking the position of
     * the first difference through timing.
     */
    fun matches(
        input: String,
        expectedHash: String,
    ): Boolean {
        val actual = hash(input)
        if (actual.length != expectedHash.length) return false
        var diff = 0
        for (i in actual.indices) {
            diff = diff or (actual[i].code xor expectedHash[i].code)
        }
        return diff == 0
    }

    /** Rejection sampling so every hex digit is uniform. */
    fun randomId(bytes: Int = 8): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return buffer.toHex()
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4])
            out.append(HEX[v and 0x0F])
        }
        return out.toString()
    }
}
