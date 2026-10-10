package com.octopus.pigeon.post.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.octopus.pigeon.post.data.model.EmailConfig
import com.octopus.pigeon.post.data.model.TemplateConfig
import com.octopus.pigeon.post.data.security.SecureValueCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that identifying values are encrypted on disk and still readable
 * through the repository.
 *
 * The important assertion is the second one in each test: not just that a round
 * trip works, but that the plaintext does not appear in the DataStore file. A
 * round trip would pass just as happily with encryption switched off, which is
 * the failure mode that would otherwise go unnoticed.
 */
@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryEncryptionTest {
    private lateinit var repository: PreferencesRepository

    @Before
    fun setUp() {
        repository = PreferencesRepository(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun mailPasswordSurvivesARoundTripAndIsNotStoredInPlaintext() =
        runBlocking {
            val password = "mail-password-must-not-be-plaintext"
            repository.updateEmailConfig(
                EmailConfig(
                    smtpServer = "smtp.example.com",
                    smtpPort = 465,
                    senderEmail = "sender@example.com",
                    password = password,
                    recipientEmail = "recipient@example.com",
                    emailSubject = "subject",
                ),
            )

            assertEquals(password, repository.emailConfig.first().password)
            assertFalse(storedBytes().contains(password))
        }

    @Test
    fun addressesAreNotStoredInPlaintext() =
        runBlocking {
            repository.updateEmailConfig(
                EmailConfig(
                    smtpServer = "smtp.example.com",
                    smtpPort = 465,
                    senderEmail = "sender-plaintext-probe@example.com",
                    password = "pw",
                    recipientEmail = "recipient-plaintext-probe@example.com",
                    emailSubject = "subject",
                ),
            )

            val stored = storedBytes()
            assertFalse(stored.contains("sender-plaintext-probe@example.com"))
            assertFalse(stored.contains("recipient-plaintext-probe@example.com"))
            assertEquals("sender-plaintext-probe@example.com", repository.emailConfig.first().senderEmail)
            assertEquals("recipient-plaintext-probe@example.com", repository.emailConfig.first().recipientEmail)
        }

    @Test
    fun keywordsAreNotStoredInPlaintext() =
        runBlocking {
            repository.updateTemplateConfig(
                TemplateConfig(
                    keywords = listOf("plaintext-keyword-probe"),
                    caseSensitive = false,
                    matchLogic = com.octopus.pigeon.post.data.model.MatchLogic.ANY,
                    emailTemplate = "",
                    useTemplate = false,
                ),
            )

            assertFalse(storedBytes().contains("plaintext-keyword-probe"))
            assertEquals(listOf("plaintext-keyword-probe"), repository.templateConfig.first().keywords)
        }

    @Test
    fun severalKeywordsEachSurviveTheRoundTrip() =
        runBlocking {
            val keywords = listOf("银行验证码", "verification code", "1234")
            repository.updateTemplateConfig(
                TemplateConfig(
                    keywords = keywords,
                    caseSensitive = false,
                    matchLogic = com.octopus.pigeon.post.data.model.MatchLogic.ANY,
                    emailTemplate = "",
                    useTemplate = false,
                ),
            )

            assertEquals(
                keywords.toSet(),
                repository.templateConfig
                    .first()
                    .keywords
                    .toSet(),
            )
            val stored = storedBytes()
            for (keyword in keywords) {
                assertFalse("keyword stored in plaintext: $keyword", stored.contains(keyword))
            }
        }

    /**
     * Reading a value written before encryption existed.
     *
     * DataStore exposes no API to write a raw plaintext value into the app's own
     * store, and building a second DataStore over the same file is not supported,
     * so the "old build wrote plaintext" case is covered where it can be tested
     * honestly: [SecureValueCipherTest.anUnprefixedLegacyValueIsReadAsIs] pins the
     * prefix rule, and [migrateLegacyValueThroughTheRepository] pins that the
     * repository neither mangles nor drops such a value.
     */
    @Test
    fun migrateLegacyValueThroughTheRepository() =
        runBlocking {
            // What a 1.0.0 install looks like on disk: unprefixed plaintext.
            val legacy = "written-by-an-older-build"
            assertEquals(legacy, SecureValueCipher().decrypt(legacy))

            // The repository has to write it encrypted on the next save, which is
            // the same code path the migration uses.
            repository.updateEmailConfig(
                EmailConfig("smtp.example.com", 465, "s@example.com", legacy, "r@example.com", "subj"),
            )

            assertEquals(legacy, repository.emailConfig.first().password)
            assertFalse(storedBytes().contains(legacy))
        }

    @Test
    fun migrationLeavesAlreadyEncryptedValuesAlone() =
        runBlocking {
            val password = "already-encrypted-value"
            repository.updateEmailConfig(
                EmailConfig("smtp.example.com", 465, "s@example.com", password, "r@example.com", "subj"),
            )
            val before = repository.emailConfig.first().password

            repository.migratePlaintextToEncrypted()

            assertEquals(before, repository.emailConfig.first().password)
            assertFalse(storedBytes().contains(password))
        }

    /** Non-sensitive configuration stays readable, because it is not a secret. */
    @Test
    fun portAndServerAreStillStoredDirectly() =
        runBlocking {
            repository.updateEmailConfig(
                EmailConfig("smtp.example.com", 465, "s@example.com", "pw", "r@example.com", "subj"),
            )

            assertTrue(storedBytes().contains("smtp.example.com"))
        }

    private fun storedBytes(): String {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File(context.filesDir, "datastore/pigeon_post_preferences.preferences_pb")
        if (!file.exists()) {
            val alt = java.io.File(context.filesDir, "datastore")
            val found = alt.listFiles()?.firstOrNull { it.name.contains("preferences") }
            return found?.readBytes()?.toString(Charsets.ISO_8859_1).orEmpty()
        }
        return file.readBytes().toString(Charsets.ISO_8859_1)
    }
}
