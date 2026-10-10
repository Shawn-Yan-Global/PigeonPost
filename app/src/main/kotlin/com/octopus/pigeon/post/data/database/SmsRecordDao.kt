package com.octopus.pigeon.post.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.octopus.pigeon.post.data.model.SmsRecord
import com.octopus.pigeon.post.data.model.SmsRecord.SecureFields
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsRecordDao {
    @Query("SELECT * FROM ${TableNames.SMS_RECORD} ORDER BY receivedTime DESC LIMIT 10")
    fun getRecentRecords(): Flow<List<SmsRecord>>

    @Query("SELECT * FROM ${TableNames.SMS_RECORD} WHERE isForwarded = 1 ORDER BY receivedTime DESC")
    fun getForwardedRecords(): Flow<List<SmsRecord>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(smsRecord: SmsRecord): Long

    /**
     * Check if the same message is already stored, to suppress duplicates.
     *
     * Matches on the blind indexes rather than the ciphertext. The indexes are
     * derived from the same sender, body and timestamp, so the comparison is
     * exactly as strict as it was when these columns held plaintext.
     */
    @Query(
        "SELECT COUNT(*) FROM ${TableNames.SMS_RECORD} " +
            "WHERE ${SecureFields.SENDER_INDEX} = :senderIndex " +
            "AND ${SecureFields.CONTENT_INDEX} = :contentIndex " +
            "AND receivedTime = :receivedTime",
    )
    suspend fun countExistingRecord(
        senderIndex: String,
        contentIndex: String,
        receivedTime: Long,
    ): Int

    @Update
    suspend fun update(smsRecord: SmsRecord)

    @Query(
        "UPDATE ${TableNames.SMS_RECORD} SET isForwarded = 1, forwardedTime = :forwardedTime, forwardSuccess = :success, errorMessage = :errorMessage WHERE id = :id",
    )
    suspend fun updateForwardStatus(
        id: Long,
        forwardedTime: Long,
        success: Boolean,
        errorMessage: String?,
    )

    @Query("UPDATE ${TableNames.SMS_RECORD} SET errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateErrorMessage(
        id: Long,
        errorMessage: String?,
    )

    @Query("DELETE FROM ${TableNames.SMS_RECORD} WHERE receivedTime < :cutoffTime")
    suspend fun deleteOldRecords(cutoffTime: Long): Int

    @Query("DELETE FROM ${TableNames.SMS_RECORD} WHERE id IN (:ids)")
    suspend fun deleteRecordsByIds(ids: List<Long>): Int

    @Query("DELETE FROM ${TableNames.SMS_RECORD}")
    suspend fun deleteAllRecords(): Int

    @Query("SELECT * FROM ${TableNames.SMS_RECORD} ORDER BY receivedTime DESC")
    fun getAllRecords(): Flow<List<SmsRecord>>

    @Query("SELECT * FROM ${TableNames.SMS_RECORD} WHERE isForwarded = 0 ORDER BY receivedTime DESC")
    fun getUnforwardedRecords(): Flow<List<SmsRecord>>

    @Query("SELECT COUNT(*) FROM ${TableNames.SMS_RECORD}")
    suspend fun getRecordsCount(): Int

    @Query("SELECT COUNT(*) FROM ${TableNames.SMS_RECORD} WHERE isForwarded = 1")
    suspend fun getForwardedRecordsCount(): Int
}
