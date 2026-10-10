package com.octopus.pigeon.post.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.octopus.pigeon.post.data.database.TableNames
import java.time.LocalDateTime

/**
 * One stored SMS.
 *
 * [sender] and [content] hold ciphertext. The value the app works with is the
 * plaintext form, and [SecureFields.senderIndex] / [SecureFields.contentIndex]
 * carry keyed blind indexes of the same two values so the unique constraint and
 * duplicate suppression keep working in SQL. The index columns are not readable
 * back into the sender or body, and the indexes are what an attacker with the
 * database file can see; see [com.octopus.pigeon.post.data.security.SecureValueCipher]
 * for what that does and does not leak.
 *
 * Decryption happens in [com.octopus.pigeon.post.data.repository.SmsRepository],
 * so nothing above it deals in ciphertext.
 */
@Entity(
    tableName = TableNames.SMS_RECORD,
    indices = [
        // The unique constraint moved onto the blind indexes. It cannot stay on
        // the ciphertext columns because randomised encryption means the same
        // message encrypts differently every time, so the constraint would never
        // fire and duplicates would accumulate.
        //
        // The column names are spelled out rather than referenced through
        // SecureFields: Room's annotation processor resolves the empty list to an
        // error instead of the constants.
        androidx.room.Index(
            value = ["senderIndex", "contentIndex", "receivedTime"],
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
    val senderIndex: String = "",
    val contentIndex: String = "",
) {
    /**
     * Column names for the blind indexes.
     *
     * A nested object so the annotation can reference them as compile-time
     * constants, since Room needs literal column names.
     */
    object SecureFields {
        const val SENDER_INDEX = "senderIndex"
        const val CONTENT_INDEX = "contentIndex"
    }
}
