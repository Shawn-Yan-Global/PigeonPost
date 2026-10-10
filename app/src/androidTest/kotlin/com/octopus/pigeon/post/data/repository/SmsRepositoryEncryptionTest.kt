package com.octopus.pigeon.post.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.octopus.pigeon.post.data.database.AppDatabase
import com.octopus.pigeon.post.data.model.SmsRecord
import com.octopus.pigeon.post.data.security.SecureValueCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

/**
 * Checks the SMS store holds ciphertext and still behaves like the plaintext
 * version did.
 *
 * Two properties matter and they pull in opposite directions. The sender and
 * body must not be recoverable from the database file, and duplicate suppression
 * and the unique index must keep matching exactly as they did before. A change
 * that only achieved the first would quietly delete the second: randomised
 * encryption never compares equal to itself, so every arriving message would
 * insert again and the history would grow without bound.
 */
@RunWith(AndroidJUnit4::class)
class SmsRepositoryEncryptionTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: SmsRepository
    private val cipher = SecureValueCipher()

    /**
     * File backed, not in-memory, and that is deliberate.
     *
     * An in-memory database writes nothing to disk, so any assertion about what
     * the database file does or does not contain would pass without ever looking
     * at a byte. These tests exist to prove the plaintext is absent from the file,
     * so the file has to be real.
     */
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(TEST_DATABASE)
        database =
            Room
                .databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
                .allowMainThreadQueries()
                .build()
        repository = SmsRepository(database.smsRecordDao(), cipher)
    }

    @After
    fun tearDown() {
        database.close()
        ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun storedRecordComesBackIntact() =
        runBlocking {
            repository.insertSmsRecord(record(sender = "+8613800138000", content = "Your code is 123456"))

            val stored = repository.getAllRecords().first().single()

            assertEquals("+8613800138000", stored.sender)
            assertEquals("Your code is 123456", stored.content)
        }

    @Test
    fun theDatabaseFileDoesNotContainTheBodyOrTheSender() =
        runBlocking {
            repository.insertSmsRecord(record(sender = "+8613800138000", content = "PlaintextProbeBody"))

            val row = rawRow()
            assertFalse("body stored in plaintext", row.contains("PlaintextProbeBody"))
            assertFalse("sender stored in plaintext", row.contains("+8613800138000"))
            // Without this the two assertions above would also pass on an empty
            // database, which is the same vacuous result as having no encryption
            // at all. The marker proves a value really was written in its place.
            assertTrue("nothing encrypted was written at all", row.contains(SecureValueCipher.PREFIX))
            // And the blind index, which is what makes duplicate suppression work.
            assertTrue(
                "the blind index needed for duplicate suppression is missing",
                row.contains(cipher.blindIndex("PlaintextProbeBody")),
            )
        }

    /**
     * The reason blind indexes exist. Without them this returns a fresh id every
     * time and the record table grows one row per SMS forever.
     */
    @Test
    fun duplicateSuppressionStillWorks() =
        runBlocking {
            val first = repository.insertSmsRecord(record(content = "duplicate me"))
            val second = repository.insertSmsRecord(record(content = "duplicate me"))

            assertTrue("first insert should succeed", first > 0)
            assertEquals("second insert should be suppressed as a duplicate", -1L, second)
            assertEquals(1, repository.getRecordsCount())
        }

    @Test
    fun aDifferentBodyIsNotTreatedAsADuplicate() =
        runBlocking {
            repository.insertSmsRecord(record(content = "first message"))
            repository.insertSmsRecord(record(content = "second message"))

            assertEquals(2, repository.getRecordsCount())
        }

    @Test
    fun theSameBodyFromADifferentSenderIsNotADuplicate() =
        runBlocking {
            repository.insertSmsRecord(record(sender = "+8613800138000", content = "same body"))
            repository.insertSmsRecord(record(sender = "+8613900139000", content = "same body"))

            assertEquals(2, repository.getRecordsCount())
        }

    @Test
    fun theSameMessageAtADifferentTimeIsNotADuplicate() =
        runBlocking {
            val base = LocalDateTime.of(2026, 10, 9, 12, 0, 0)
            repository.insertSmsRecord(record(content = "body", receivedTime = base))
            repository.insertSmsRecord(record(content = "body", receivedTime = base.plusMinutes(1)))

            assertEquals(2, repository.getRecordsCount())
        }

    @Test
    fun blindIndexesAreEqualForEqualValuesAndDifferentOtherwise() {
        assertEquals(cipher.blindIndex("same"), cipher.blindIndex("same"))
        assertNotEquals(cipher.blindIndex("one"), cipher.blindIndex("two"))
    }

    @Test
    fun blindIndexDoesNotRevealTheValue() {
        val index = cipher.blindIndex("+8613800138000")
        assertFalse(index.contains("8613800138000"))
    }

    @Test
    fun compositeIndexSeparatesFieldsSoTheValuesCannotBeConfused() {
        assertNotEquals(
            cipher.blindIndexOf("a", "b"),
            cipher.blindIndexOf("ab", ""),
        )
    }

    @Test
    fun errorMessageIsEncryptedBecauseItCanQuoteTheRecipient() =
        runBlocking {
            val id = repository.insertSmsRecord(record(content = "body"))
            repository.updateForwardStatus(
                id = id,
                success = false,
                errorMessage = "550 mailbox unavailable for recipient@example.com",
            )

            assertEquals(
                "550 mailbox unavailable for recipient@example.com",
                repository
                    .getAllRecords()
                    .first()
                    .single()
                    .errorMessage,
            )
            assertFalse(rawRow().contains("recipient@example.com"))
        }

    @Test
    fun forwardedStatusUpdatesDoNotNeedTheBody() =
        runBlocking {
            val id = repository.insertSmsRecord(record(content = "body"))
            repository.updateForwardStatus(id = id, success = true)

            val stored = repository.getForwardedRecords().first().single()
            assertTrue(stored.isForwarded)
            assertTrue(stored.forwardSuccess)
            assertEquals("body", stored.content)
        }

    /**
     * The lock screen case. The key is deliberately created without a user
     * authentication requirement, because the receiver has to read and forward
     * while the screen is locked. This asserts the cipher is usable in that
     * state, which it cannot be if the key were ever gated.
     */
    @Test
    fun valuesAreReadableWithoutAnyUserAuthentication() {
        val stored = cipher.encrypt("readable while locked")!!

        assertEquals("readable while locked", cipher.decrypt(stored))
    }

    @Test
    fun nonEnglishBodySurvivesTheRoundTrip() =
        runBlocking {
            val body = "【中国银行】您的验证码是 654321，请勿泄露给他人。"
            repository.insertSmsRecord(record(content = body))

            assertEquals(
                body,
                repository
                    .getAllRecords()
                    .first()
                    .single()
                    .content,
            )
            assertFalse(rawRow().contains("654321"))
        }

    @Test
    fun longBodySurvivesTheRoundTrip() =
        runBlocking {
            // An SMS is short, but the cipher must not silently truncate or fail on
            // an unexpectedly long value, because the failure mode would be a
            // record that saves fine and reads back empty.
            val body = "x".repeat(8_000)
            repository.insertSmsRecord(record(content = body))

            assertEquals(
                body,
                repository
                    .getAllRecords()
                    .first()
                    .single()
                    .content,
            )
        }

    private fun record(
        sender: String = "+8613800138000",
        content: String = "body",
        receivedTime: LocalDateTime = LocalDateTime.of(2026, 10, 9, 12, 0, 0),
    ): SmsRecord = SmsRecord(sender = sender, content = content, receivedTime = receivedTime)

    /**
     * Reads the bytes actually on disk, not the objects Room hands back.
     *
     * Every file SQLite has written is included, because a value can reach the
     * write-ahead log before it reaches the main database file, and checking only
     * the main file would miss that.
     */
    private fun rawRow(): String {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val main = context.getDatabasePath(TEST_DATABASE)
        val files =
            listOf(main, File("${main.path}-wal"), File("${main.path}-shm"), File("${main.path}-journal"))
        val text =
            files
                .filter { it.exists() }
                .joinToString(separator = "") { file ->
                    runCatching { file.readBytes().toString(Charsets.ISO_8859_1) }.getOrDefault("")
                }
        check(text.isNotEmpty()) {
            "no database bytes were written, so this test would pass without checking anything"
        }
        return text
    }

    private companion object {
        const val TEST_DATABASE = "sms-encryption-test.db"
    }
}
