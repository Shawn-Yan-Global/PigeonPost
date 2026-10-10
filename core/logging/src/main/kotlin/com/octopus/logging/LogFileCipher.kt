package com.octopus.logging

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts log files at rest, line by line, under a Keystore key.
 *
 * Why this exists rather than pointing Log4j at an encrypted path: the Log4j
 * file appender takes a file name and writes bytes to it directly, so there is
 * nothing to hook. Encrypting the file after the fact would leave plaintext on
 * disk in between, which is the state this is meant to eliminate, and encrypting
 * a whole file means every append rewrites it.
 *
 * So each line is sealed on its own with AES/GCM and appended as base64. That
 * gives append-only writes, no rewrite on rotation, and a corrupted line costs
 * one line rather than the file.
 *
 * A separate key from the one protecting stored credentials. Sharing a key would
 * let anyone holding a log file test guesses against the mail password, and the
 * two protect different things on different schedules.
 */
class LogFileCipher(
    private val keyAlias: String = KEY_ALIAS,
) {
    private val tag = "LogFileCipher"

    /**
     * Seals one log line.
     *
     * Returns the line unchanged when encryption fails. Losing a line of
     * diagnostics is better than losing the thread that was writing them, and a
     * build that cannot encrypt its logs should still be debuggable.
     */
    fun sealLine(line: String): String =
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
            val payload = cipher.iv + cipher.doFinal(line.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        }.onFailure {
            Log.e(tag, "Failed to encrypt a log line; writing it in the clear", it)
        }.getOrDefault(line)

    /**
     * Opens one sealed line.
     *
     * A line without [PREFIX] is a debug build's plaintext log, or a line that
     * failed to seal. Both are returned as-is, so a mixed directory still reads.
     */
    fun openLine(stored: String): String {
        if (!stored.startsWith(PREFIX)) {
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
            Log.e(tag, "Failed to decrypt a log line", it)
        }.getOrDefault(stored)
    }

    /** Decrypts a whole file's lines. Used by the viewer and by export. */
    fun openFile(file: File): String =
        runCatching {
            file.readLines().joinToString(separator = "\n") { openLine(it) }
        }.getOrElse {
            Log.e(tag, "Failed to read a log file: ${file.name}", it)
            ""
        }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        /**
         * Marks a sealed line.
         *
         * Not a character that can begin a real log line, which starts with a
         * timestamp, so a plaintext line can never be mistaken for a sealed one.
         */
        const val PREFIX = "@enc:"

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pigeon_post_log_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
