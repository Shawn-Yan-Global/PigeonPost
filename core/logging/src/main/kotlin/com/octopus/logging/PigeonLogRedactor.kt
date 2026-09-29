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

    private const val TAG_BYTES = 4
}
