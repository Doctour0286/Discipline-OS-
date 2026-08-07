package com.disciplineos.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.util.UUID

class Converters {
    @TypeConverter
    fun fromUuid(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun toUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.joinToString(separator = "\u0001")

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        if (value.isNullOrEmpty()) emptyList() else value.split("\u0001")
}
