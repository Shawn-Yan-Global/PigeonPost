package com.octopus.pigeon.post.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.octopus.pigeon.post.data.model.SmsRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsRecordDao {
    @Query("SELECT * FROM ${TableNames.SMS_RECORD} ORDER BY receivedTime DESC LIMIT 10")
    fun getRecentRecords(): Flow<List<SmsRecord>>

    @Query("SELECT * FROM ${TableNames.SMS_RECORD} WHERE isForwarded = 1 ORDER BY receivedTime DESC")
    fun getForwardedRecords(): Flow<List<SmsRecord>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(smsRecord: SmsRecord): Long

    // Check if a similar SMS record already exists (to prevent duplicates)
    @Query(
        "SELECT COUNT(*) FROM ${TableNames.SMS_RECORD} WHERE sender = :sender AND content = :content AND receivedTime = :receivedTime",
    )
    suspend fun countExistingRecord(
        sender: String,
        content: String,
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
