package org.beesearch.app.data.local.room

import androidx.room.TypeConverter
import org.beesearch.app.domain.model.MarkPosition
import java.time.Instant
import java.util.UUID

internal class RoomConverters {
    @TypeConverter
    fun uuidToString(value: UUID): String = value.toString()

    @TypeConverter
    fun stringToUuid(value: String): UUID = UUID.fromString(value)

    @TypeConverter
    fun instantToEpochMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun markPositionToString(value: MarkPosition): String = value.name

    @TypeConverter
    fun stringToMarkPosition(value: String): MarkPosition = MarkPosition.valueOf(value)
}
