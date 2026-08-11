package com.disciplineos.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalTime
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

    /**
     * Added for [com.disciplineos.data.entity.MissionPeriod.startTime] (Goal-Oriented Mission
     * Model, ROADMAP.md §5.32) — the first field in this codebase needing a bare time-of-day
     * with no date component. Stored as seconds-since-midnight (an `Int`, matching this file's
     * existing preference for primitive-backed converters over strings where the value is
     * numeric) rather than reusing [fromInstant]'s epoch-millis representation, since a
     * [LocalTime] deliberately carries no date/timezone and coercing it through [Instant] would
     * either fabricate a fake date or silently apply zone conversion neither the entity nor its
     * callers want.
     */
    @TypeConverter
    fun fromLocalTime(value: LocalTime?): Int? = value?.toSecondOfDay()

    @TypeConverter
    fun toLocalTime(value: Int?): LocalTime? = value?.let(LocalTime::ofSecondOfDay)
}
