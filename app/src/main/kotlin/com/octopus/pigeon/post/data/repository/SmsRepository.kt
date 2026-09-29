package com.octopus.pigeon.post.data.repository

import com.octopus.logging.PigeonLogRedactor
import com.octopus.logging.PigeonLogger
import com.octopus.pigeon.post.BuildConfig
import com.octopus.pigeon.post.data.database.SmsRecordDao
import com.octopus.pigeon.post.data.model.SmsRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Repository for managing SMS records in the database
 * Handles CRUD operations and data validation
 */
class SmsRepository(
    private val smsRecordDao: SmsRecordDao,
) {
    companion object {
        private const val TAG = "SmsRepository"
    }

    fun getRecentRecords(): Flow<List<SmsRecord>> = smsRecordDao.getRecentRecords()

    fun getForwardedRecords(): Flow<List<SmsRecord>> = smsRecordDao.getForwardedRecords()

    suspend fun insertSmsRecord(smsRecord: SmsRecord): Long {
        // The silent build keeps no SMS history at all. Storing every message
        // in plaintext would be worse than not storing it, so nothing is
        // written and the caller gets the "duplicate" sentinel back.
        if (BuildConfig.SILENT) return -1L

        // Check if the same record already exists
        val receivedTimeEpoch = smsRecord.receivedTime.toEpochSecond(java.time.ZoneOffset.UTC)
        val existingCount =
            smsRecordDao.countExistingRecord(
                smsRecord.sender,
                smsRecord.content,
                receivedTimeEpoch,
            )

        if (existingCount > 0) {
            PigeonLogger.warn(
                TAG,
                "Duplicate SMS record detected, skipping insertion: sender=${smsRecord.sender}, time=${smsRecord.receivedTime}",
            )
            return -1L // Return -1 indicating duplicate record not inserted
        }

        val result = smsRecordDao.insert(smsRecord)
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
            errorMessage = errorMessage,
        )
    }

    suspend fun updateErrorMessage(
        id: Long,
        errorMessage: String?,
    ) {
        smsRecordDao.updateErrorMessage(id, errorMessage)
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

    fun getAllRecords(): Flow<List<SmsRecord>> = smsRecordDao.getAllRecords()

    fun getUnforwardedRecords(): Flow<List<SmsRecord>> = smsRecordDao.getUnforwardedRecords()

    suspend fun getRecordsCount(): Int = smsRecordDao.getRecordsCount()

    suspend fun getForwardedRecordsCount(): Int = smsRecordDao.getForwardedRecordsCount()
}
