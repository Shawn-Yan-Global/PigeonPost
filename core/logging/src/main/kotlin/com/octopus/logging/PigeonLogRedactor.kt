package com.octopus.logging

import java.security.MessageDigest

/**
 * Keeps sensitive values out of the log files.
 *
 * The log files are user visible, shareable as a diagnostic bundle, and survive
 * in plain text inside the app's private storage. Anything written to them
 * should be treated as something the user could read, hand to support, or lose
 * along with a lost phone. So phone numbers, message bodies, and mail addresses
 * do not go in.
 *
 * That leaves a problem: logs lose most of their value if you cannot tell
 * whether two events came from the same sender. Masking to `138****8000` still
 * leaks the operator and the exact subscriber digits, and truncating to the last
 * four is no better, because those four are often enough to identify the
 * sender in a small group.
 *
 * So instead of masking, values are replaced with a short digest. The digest is
 * stable, so the same sender produces the same tag on every message and can
 * still be correlated, but it cannot be reversed, and it is not the number.
 */
object PigeonLogRedactor {
    /**
     * Constant per install, not per build, so a digest stays valid across app
     * restarts and log rotations. A salt is not a secret and does not need to
     * be, its job is to keep the digest from being matched against a table of
     * every possible phone number.
     */
    private val SALT = "pigeonpost.logredact.v1".toByteArray()

    /** Placeholder for a value that is not worth logging at all. */
    const val REDACTED = "<redacted>"

    /**
     * A short, stable, non-reversible tag for [value], or [REDACTED] when there
     * is nothing to identify.
     */
    fun tag(value: String?): String {
        if (value.isNullOrEmpty()) return REDACTED
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(SALT)
        digest.update(value.trim().toByteArray())
        val hex = digest.digest().take(TAG_BYTES).joinToString("") { "%02x".format(it) }
        return "id:$hex"
    }

    /**
     * A digest for a message body.
     *
     * Only ever a digest, never the body. Included so the same message can be
     * recognised as a duplicate across retries, which the old code was doing by
     * comparing content.
     */
    fun contentTag(content: String?): String = tag(content)

    /**
     * Describes a value without revealing it: its length only, which is enough
     * to tell "empty" from "long" when debugging a parse.
     */
    fun size(value: String?): Int = value?.length ?: 0

    /**
     * Whether message bodies may be written verbatim.
     *
     * Off in release builds, on in debug builds, so a developer reproducing a
     * keyword problem can see exactly what arrived. A release build never logs a
     * body, whatever this is set to: the flag only widens what a *development*
     * build shows, and it cannot be turned on by a caller in a shipped APK.
     */
    @Volatile
    private var verboseBodiesAllowed = false

    fun allowVerboseBodies(allowed: Boolean) {
        verboseBodiesAllowed = allowed
    }

    fun verboseBodiesAllowed(): Boolean = verboseBodiesAllowed

    /**
     * A body, or nothing.
     *
     * Returns the body only where [verboseBodiesAllowed] says it is safe to, and
     * otherwise the shape below.
     */
    fun body(value: String?): String {
        val text = value.orEmpty()
        if (verboseBodiesAllowed) return text
        return describe(value)
    }

    /**
     * A shape summary of a message body: enough to tell what kind of message it
     * was and whether it could plausibly contain a code, without being the
     * message.
     *
     * This exists because "the keyword did not match" is the single most common
     * reason for a support request, and a digest plus a length cannot answer it.
     * What usually matters is the character mix and whether the digits are
     * grouped the way a one-time passcode is: `【银行】验证码 493812` and an
     * English sentence of the same length produce different summaries, which is
     * enough to tell a code apart from prose and to notice an unexpected prefix
     * or a message that arrived truncated.
     *
     * The counts are character-class counts, so they say how many digits there
     * were without saying what they were.
     */
    fun describe(content: String?): String {
        val text = content.orEmpty()
        if (text.isEmpty()) return "empty"
        val digits = text.count { it.isDigit() }
        val asciiLetters = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        val cjk = text.count { it.code in 0x4E00..0x9FFF }
        val longestDigitRun = text.longestRunOf { it.isDigit() }
        val parts =
            buildList {
                add("len=${text.length}")
                if (digits > 0) add("digits=$digits")
                if (asciiLetters > 0) add("latin=$asciiLetters")
                if (cjk > 0) add("cjk=$cjk")
                if (longestDigitRun >= MIN_DIGIT_RUN_FOR_CODE) add("digit_run=$longestDigitRun")
                if (text.any { it.isWhitespace() }) add("spaced")
            }
        return parts.joinToString(separator = ",")
    }

    private fun String.longestRunOf(predicate: (Char) -> Boolean): Int {
        var longest = 0
        var current = 0
        for (char in this) {
            current = if (predicate(char)) current + 1 else 0
            if (current > longest) longest = current
        }
        return longest
    }

    private const val TAG_BYTES = 4

    /**
     * Four digits in a row is the shortest run worth reporting, since it is the
     * usual width of a verification code.
     */
    private const val MIN_DIGIT_RUN_FOR_CODE = 4
}
