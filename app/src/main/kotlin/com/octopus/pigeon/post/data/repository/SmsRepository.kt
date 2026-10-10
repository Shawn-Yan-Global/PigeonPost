package com.octopus.pigeon.post.data.repository

import com.octopus.logging.PigeonLogRedactor
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.BuildConfig
import com.octopus.pigeon.post.data.database.SmsRecordDao
import com.octopus.pigeon.post.data.model.SmsRecord
import com.octopus.pigeon.post.data.security.SecureValueCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

/**
 * Repository for managing SMS records in the database.
 *
 * This is the only place that talks to the DAO, and it is where the sender and
 * body are encrypted on the way in and decrypted on the way out. Everything
 * above this class deals in plaintext [SmsRecord]s and never sees ciphertext, so
 * the UI, the forwarding service and the record screen need no changes and
 * cannot accidentally log an encrypted body.
 */
class SmsRepository(
    private val smsRecordDao: SmsRecordDao,
    private val cipher: SecureValueCipher = SecureValueCipher(),
) {
    companion object {
        private const val TAG = "SmsRepository"
    }

    fun getRecentRecords(): Flow<List<SmsRecord>> = smsRecordDao.getRecentRecords().map { it.map(::decrypt) }

    fun getForwardedRecords(): Flow<List<SmsRecord>> = smsRecordDao.getForwardedRecords().map { it.map(::decrypt) }

    suspend fun insertSmsRecord(smsRecord: SmsRecord): Long {
        // The silent build keeps no SMS history at all. Storing every message
        // would be worse than not storing it, so nothing is written and the
        // caller gets the "duplicate" sentinel back.
        if (BuildConfig.SILENT) return -1L

        val receivedTimeEpoch = smsRecord.receivedTime.toEpochSecond(java.time.ZoneOffset.UTC)
        val senderIndex = cipher.blindIndex(smsRecord.sender)
        val contentIndex = cipher.blindIndex(smsRecord.content)

        // Matching on the indexes, exactly as the plaintext columns did before.
        val existingCount =
            smsRecordDao.countExistingRecord(
                senderIndex,
                contentIndex,
                receivedTimeEpoch,
            )

        if (existingCount > 0) {
            PigeonLogger.warn(
                TAG,
                "Duplicate SMS record detected, skipping insertion: sender=${PigeonLogRedactor.tag(smsRecord.sender)}, time=${smsRecord.receivedTime}",
            )
            return -1L
        }

        val result =
            smsRecordDao.insert(
                smsRecord.encrypt(senderIndex, contentIndex),
            )
        if (result > 0) {
            PigeonLogger.info(
                TAG,
                "SMS record inserted successfully: ID=$result, " +
                    "sender=${PigeonLogRedactor.tag(smsRecord.sender)}",
            )
        } else {
            PigeonLogger.warn(
                TAG,
                "SMS record insertion failed (possibly duplicate): " +
                    "sender=${PigeonLogRedactor.tag(smsRecord.sender)}",
            )
        }
        return result
    }

    suspend fun updateForwardStatus(
        id: Long,
        success: Boolean,
        errorMessage: String? = null,
    ) {
        val now = LocalDateTime.now()
        smsRecordDao.updateForwardStatus(
            id = id,
            forwardedTime = now.toEpochSecond(java.time.ZoneOffset.UTC),
            success = success,
            // A send failure message can quote the recipient address, so it is
            // treated as content rather than as status text.
            errorMessage = errorMessage?.let { cipher.encrypt(it) },
        )
    }

    suspend fun updateErrorMessage(
        id: Long,
        errorMessage: String?,
    ) {
        smsRecordDao.updateErrorMessage(id = id, errorMessage = errorMessage?.let { cipher.encrypt(it) })
    }

    suspend fun deleteOldRecords(cutoffTime: LocalDateTime): Int {
        val cutoffTimeEpoch = cutoffTime.toEpochSecond(java.time.ZoneOffset.UTC)
        val deletedCount = smsRecordDao.deleteOldRecords(cutoffTimeEpoch)
        PigeonLogger.info(TAG, "Old records deletion completed: deleted $deletedCount records, cutoff time=$cutoffTime")
        return deletedCount
    }

    suspend fun deleteRecordsByIds(ids: List<Long>): Int {
        val deletedCount = smsRecordDao.deleteRecordsByIds(ids)
        PigeonLogger.info(TAG, "Batch records deletion completed: deleted $deletedCount records")
        return deletedCount
    }

    suspend fun deleteAllRecords(): Int {
        val deletedCount = smsRecordDao.deleteAllRecords()
        PigeonLogger.info(TAG, "Clear all records completed: deleted $deletedCount records")
        return deletedCount
    }

    fun getAllRecords(): Flow<List<SmsRecord>> = smsRecordDao.getAllRecords().map { it.map(::decrypt) }

    fun getUnforwardedRecords(): Flow<List<SmsRecord>> = smsRecordDao.getUnforwardedRecords().map { it.map(::decrypt) }

    suspend fun getRecordsCount(): Int = smsRecordDao.getRecordsCount()

    suspend fun getForwardedRecordsCount(): Int = smsRecordDao.getForwardedRecordsCount()

    /**
     * Replaces the sender and body with ciphertext, keeping the supplied blind
     * indexes so the unique index covers the same values it would have covered
     * in plaintext.
     */
    private fun SmsRecord.encrypt(
        senderIndex: String,
        contentIndex: String,
    ): SmsRecord =
        copy(
            sender = cipher.encrypt(sender).orEmpty(),
            content = cipher.encrypt(content).orEmpty(),
            senderIndex = senderIndex,
            contentIndex = contentIndex,
        )

    /** The inverse of [encrypt]. */
    private fun decrypt(record: SmsRecord): SmsRecord =
        record.copy(
            sender = cipher.decrypt(record.sender),
            content = cipher.decrypt(record.content),
            errorMessage = record.errorMessage?.let { cipher.decrypt(it) },
        )
}
