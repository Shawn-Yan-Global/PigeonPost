package com.octopus.pigeon.post.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.octopus.pigeon.post.data.database.TableNames
import java.time.LocalDateTime

@Entity(
    tableName = TableNames.SMS_RECORD,
    indices = [
        androidx.room.Index(
            value = ["sender", "content", "receivedTime"],
            unique = true,
        ),
    ],
)
data class SmsRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val content: String,
    val receivedTime: LocalDateTime,
    val forwardedTime: LocalDateTime? = null,
    val isForwarded: Boolean = false,
    val forwardSuccess: Boolean = false,
    val errorMessage: String? = null,
)
