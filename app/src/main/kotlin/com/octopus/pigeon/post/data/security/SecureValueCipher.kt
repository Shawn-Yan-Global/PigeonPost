package com.octopus.pigeon.post.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Field level encryption for the values that identify the user, backed by the
 * Android Keystore.
 *
 * Everything else in the app is configuration: a port, a boolean, a keyword
 * count. Those are not sensitive. The mail password, the addresses, and the
 * keywords are the whole reason this class exists, because DataStore is a plain
 * file in the app's private directory. That directory is not readable by other
 * apps, but it is readable from a rooted device, from an `adb backup`, and from
 * anyone who gets a copy of the data directory. For the mail password in
 * particular, a user who has installed this app to forward a bank OTP should not
 * have to assume it is safe on disk.
 *
 * The key never leaves the Keystore. It is generated on hardware where the
 * platform supports it, and DataStore only ever holds ciphertext, so the file on
 * disk is not enough to recover the values.
 *
 * Encrypted values carry a [PREFIX]. A value without it was written before this
 * existed and is passed through unchanged, which is what makes the upgrade
 * non-destructive: existing configuration keeps working and gets rewritten in
 * encrypted form the next time it is saved.
 */
class SecureValueCipher(
    private val keyStoreName: String = ANDROID_KEY_STORE,
    private val keyAlias: String = KEY_ALIAS,
) {
    private val tag = "SecureValueCipher"

    /**
     * Encrypts [plaintext], returning a value safe to write to DataStore.
     *
     * Returns null when the value cannot be encrypted, so the caller can decide
     * what to do rather than silently persisting plaintext under an encrypted
     * key's name.
     */
    fun encrypt(plaintext: String): String? {
        if (plaintext.isEmpty()) {
            return plaintext
        }
        return runCatching {
            if (plaintext.startsWith(PREFIX)) {
                // Already encrypted. Re-encrypting would double-wrap it, so an
                // unchanged value keeps whatever it was stored as.
                return plaintext
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
            // GCM needs the IV to decrypt, and doFinal returns only the ciphertext
            // and tag. The Keystore generates a fresh IV here, so it has to be
            // stored alongside the ciphertext rather than derived.
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
        }.onFailure {
            Log.e(tag, "Failed to encrypt a stored value", it)
        }.getOrNull()
    }

    /**
     * Decrypts a value produced by [encrypt].
     *
     * A value without [PREFIX] is returned as-is, which covers both values
     * written by an older build and the empty string.
     */
    fun decrypt(stored: String): String {
        if (stored.isEmpty() || !stored.startsWith(PREFIX)) {
            return stored
        }
        return runCatching {
            val payload = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                loadOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.take(IV_LENGTH_BYTES).toByteArray()),
            )
            String(
                cipher.doFinal(payload.drop(IV_LENGTH_BYTES).toByteArray()),
                Charsets.UTF_8,
            )
        }.onFailure {
            Log.e(tag, "Failed to decrypt a stored value", it)
        }.getOrDefault("")
    }

    /**
     * Whether a stored value is already in encrypted form.
     *
     * Used by the repository to decide whether an upgrade step is needed, and by
     * tests to assert that plaintext never reaches disk.
     */
    fun isEncrypted(stored: String): Boolean = stored.isEmpty() || stored.startsWith(PREFIX)

    /**
     * A deterministic, non-reversible index for [value], under a separate key.
     *
     * Randomised encryption is what keeps equal values from looking equal on disk,
     * but that is exactly what SQL cannot work with: the record table has a unique
     * index on the sender, body and timestamp, and duplicate suppression compares
     * those columns in SQL. Ciphertext never compares equal to itself, so encrypting
     * those columns alone would silently disable both, and every incoming SMS would
     * be stored again.
     *
     * So the searchable columns are stored as a blind index and the real value is
     * stored encrypted beside it. This restores the exact matching semantics, at the
     * cost of one thing worth stating plainly: equal senders, or equal message
     * bodies, still produce equal indexes. An attacker holding the database can
     * learn that two records share a sender or a body, and nothing more; recovering
     * either value from the index is not possible. Determinism cannot be avoided
     * here without giving up the unique index, and losing that would reintroduce
     * unbounded duplicate growth.
     */
    fun blindIndex(value: String): String {
        if (value.isEmpty()) {
            return ""
        }
        return runCatching {
            val mac = Mac.getInstance(HMAC)
            mac.init(loadOrCreateHmacKey())
            Base64.encodeToString(mac.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }.onFailure {
            Log.e(tag, "Failed to index a stored value", it)
        }.getOrDefault("")
    }

    /**
     * A blind index over several values at once, for a composite unique key.
     *
     * The separator cannot appear in an SMS body in a way that would let two
     * different field sets collide into the same index.
     */
    fun blindIndexOf(vararg values: String): String = if (values.all { it.isEmpty() }) "" else blindIndex(values.joinToString(FIELD_SEPARATOR))

    private fun loadOrCreateHmacKey(): SecretKey {
        val keyStore = KeyStore.getInstance(keyStoreName).apply { load(null) }
        (keyStore.getEntry(HMAC_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance("HmacSHA256", keyStoreName)
        generator.init(
            KeyGenParameterSpec
                .Builder(
                    HMAC_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                ).setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(keyStoreName).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreName)
        generator.init(
            KeyGenParameterSpec
                .Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // No user authentication requirement on purpose. The forwarding
                // service reads these values on a background thread with no UI in
                // front of it, so demanding a credential here would break
                // forwarding whenever the screen is locked.
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        /**
         * Marks a stored value as ciphertext.
         *
         * Chosen so it cannot collide with a real value: an address or a password
         * beginning with this would have to contain a character that cannot appear
         * in the start of base64.
         */
        const val PREFIX = "\u0001enc:"

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pigeon_post_field_key"

        /**
         * A separate key from [KEY_ALIAS] so that an index can never be used to
         * cross-check an encrypted value, and so the two operations have
         * independent purposes.
         */
        private const val HMAC_KEY_ALIAS = "pigeon_post_index_key"
        private const val HMAC = "HmacSHA256"
        private const val FIELD_SEPARATOR = "\u0000"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
