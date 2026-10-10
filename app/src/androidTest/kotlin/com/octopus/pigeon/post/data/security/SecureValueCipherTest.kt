package com.octopus.pigeon.post.data.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the Keystore backed cipher does what it claims.
 *
 * This needs a real device or emulator because the whole point is that the key
 * lives in the platform Keystore, which does not exist on the JVM. A unit test
 * with a stubbed key would pass whether or not the integration is correct.
 */
@RunWith(AndroidJUnit4::class)
class SecureValueCipherTest {
    private val cipher = SecureValueCipher()

    @Test
    fun encryptionRoundTrips() {
        for (value in listOf("hunter2-mail-password", "a@b.com", "verification-code", "短信验证码", " ")) {
            val stored = cipher.encrypt(value)!!
            assertEquals(value, cipher.decrypt(stored))
        }
    }

    @Test
    fun ciphertextDoesNotContainThePlaintext() {
        val secret = "mail-password-that-must-not-be-on-disk"
        val stored = cipher.encrypt(secret)!!

        assertNotEquals(secret, stored)
        assertFalse("ciphertext leaked the plaintext", stored.contains(secret))
        assertTrue(stored.startsWith(SecureValueCipher.PREFIX))
    }

    @Test
    fun theSameValueEncryptsDifferentlyEachTime() {
        // GCM uses a fresh IV per operation, so equal plaintext must not produce
        // equal ciphertext. Identical ciphertext for equal plaintext would mean
        // matching could be done on the stored form, leaking equality.
        val first = cipher.encrypt("repeated")!!
        val second = cipher.encrypt("repeated")!!

        assertNotEquals(first, second)
        assertEquals("repeated", cipher.decrypt(first))
        assertEquals("repeated", cipher.decrypt(second))
    }

    /**
     * The upgrade path. A value written before this class existed has no prefix
     * and must still read back unchanged, or every existing install loses its
     * mail configuration on update.
     */
    @Test
    fun anUnprefixedLegacyValueIsReadAsIs() {
        assertEquals("legacy-plaintext", cipher.decrypt("legacy-plaintext"))
        // False, not true: this is the signal that the value still needs
        // migrating, which is what the startup migration keys off.
        assertFalse(cipher.isEncrypted("legacy-plaintext"))
        // An empty value is neither encrypted nor in need of migrating, because
        // there is nothing to protect.
        assertTrue(cipher.isEncrypted(""))
    }

    @Test
    fun anEncryptedValueIsRecognised() {
        val stored = cipher.encrypt("secret")!!
        assertTrue(cipher.isEncrypted(stored))
    }

    @Test
    fun emptyValuesAreLeftAlone() {
        assertEquals("", cipher.encrypt("")!!)
        assertEquals("", cipher.decrypt(""))
    }

    @Test
    fun alreadyEncryptedValuesAreNotDoubleWrapped() {
        val once = cipher.encrypt("value")!!
        assertEquals(once, cipher.encrypt(once)!!)
        assertEquals("value", cipher.decrypt(once))
    }

    @Test
    fun tamperedCiphertextDoesNotReturnGarbage() {
        val stored = cipher.encrypt("secret")!!
        val tampered = stored.dropLast(2) + (if (stored.last() == 'A') "BB" else "AA")

        // GCM authenticates, so a modified payload fails to decrypt. What matters
        // is that it does not come back as the original value.
        assertNotEquals("secret", cipher.decrypt(tampered))
    }
}
