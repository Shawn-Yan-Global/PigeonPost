package com.octopus.pigeon.post.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.octopus.pigeon.post.data.model.SmsRecord

@Database(
    entities = [SmsRecord::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsRecordDao(): SmsRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "pigeon_post_database",
                    )
                    // Version 2 moved the sender and body to encrypted columns
                    // with blind indexes beside them. Records written by version 1
                    // are plaintext and cannot be converted in place, because the
                    // Keystore key is not available to a migration that runs before
                    // the app is unlocked, and because re-encrypting rows would mean
                    // reading every message into memory. The history is dropped
                    // instead, which is also the outcome of reinstalling.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
