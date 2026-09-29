package com.octopus.pigeon.post.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Debug
import com.octopus.logging.PigeonLogger
import java.security.MessageDigest

/**
 * Security utilities for anti-reverse engineering
 * Basic runtime protection mechanisms
 */
object SecurityUtils {
    private const val TAG = "SecurityUtils"

    /**
     * Basic anti-debugging detection
     * Returns true if debugging is detected
     */
    fun isDebuggingDetected(): Boolean =
        Debug.isDebuggerConnected() ||
            Debug.waitingForDebugger() ||
            isEmulatorDetected()

    /**
     * Basic emulator detection
     * Returns true if running on emulator
     */
    private fun isEmulatorDetected(): Boolean =
        (
            android.os.Build.FINGERPRINT
                .startsWith("generic") ||
                android.os.Build.FINGERPRINT
                    .lowercase()
                    .contains("vbox") ||
                android.os.Build.FINGERPRINT
                    .lowercase()
                    .contains("test-keys") ||
                android.os.Build.MODEL
                    .contains("google_sdk") ||
                android.os.Build.MODEL
                    .contains("Emulator") ||
                android.os.Build.MODEL
                    .contains("Android SDK built for x86") ||
                android.os.Build.MANUFACTURER
                    .contains("Genymotion") ||
                android.os.Build.HARDWARE == "goldfish" ||
                android.os.Build.HARDWARE == "vbox86" ||
                android.os.Build.PRODUCT == "sdk" ||
                android.os.Build.PRODUCT == "google_sdk" ||
                android.os.Build.PRODUCT == "sdk_x86" ||
                android.os.Build.PRODUCT == "vbox86p" ||
                android.os.Build.BOARD
                    .lowercase()
                    .contains("nox") ||
                android.os.Build.BOOTLOADER
                    .lowercase()
                    .contains("nox") ||
                android.os.Build.HARDWARE
                    .lowercase()
                    .contains("nox") ||
                android.os.Build.PRODUCT
                    .lowercase()
                    .contains("nox")
        )

    /**
     * Simple integrity check for app package
     */
    fun verifyAppIntegrity(context: Context): Boolean {
        return try {
            val packageInfo =
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES, // Use the new flag instead of GET_SIGNATURES
                )

            val signingInfo = packageInfo.signingInfo
            val signatures =
                signingInfo?.let {
                    if (it.hasMultipleSigners()) {
                        // If the app is signed by multiple signers, return all signers
                        it.apkContentsSigners
                    } else {
                        // If single signer or rotated certificates, return signing history
                        signingInfo.signingCertificateHistory
                    }
                }
            if (signatures == null) {
                PigeonLogger.warn(
                    TAG,
                    "App integrity check failed: No signingInfo",
                )
                return false
            }

            val md = MessageDigest.getInstance("SHA-1")
            val signature = signatures[0] // Order is not guaranteed in multiple signers
            md.update(signature.toByteArray())

            val digest = md.digest().joinToString(":") { "%02X".format(it) }

            // TODO: In production, compare the digest with your known valid signature hash
            // Example: return digest == "AB:CD:EF:..."

            true
        } catch (e: Exception) {
            PigeonLogger.warn(
                TAG,
                "App integrity check failed: ${e.message}",
            )
            false
        }
    }

    /**
     * Obfuscated string decryption (simple XOR)
     * In production, use more sophisticated encryption
     */
    fun decryptString(
        encrypted: ByteArray,
        key: Int,
    ): String = encrypted.map { (it.toInt() xor key).toChar() }.joinToString("")

    /**
     * Simple string obfuscation helper
     */
    fun obfuscateString(
        input: String,
        key: Int,
    ): ByteArray = input.map { (it.code xor key).toByte() }.toByteArray()

    /**
     * Runtime application protection check
     * Call this in critical application paths
     */
    fun performSecurityCheck(context: Context): Boolean {
        if (isDebuggingDetected()) {
            PigeonLogger.warn(
                TAG,
                "Debug environment detected",
            )
            return false
        }

        if (!verifyAppIntegrity(context)) {
            PigeonLogger.warn(
                TAG,
                "App integrity verification failed",
            )
            return false
        }

        return true
    }
}
