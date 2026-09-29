package com.octopus.pigeon.post.data.database

import androidx.room.TypeConverter
import java.time.LocalDateTime
import java.time.ZoneOffset

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? = value?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? = date?.toEpochSecond(ZoneOffset.UTC)
}
