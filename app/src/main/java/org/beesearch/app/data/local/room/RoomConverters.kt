package org.beesearch.app.data.local.room

import androidx.room.TypeConverter
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.BeePresenceResult
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

    /**
     * Reads the persisted token through the compatibility mapping so that rows
     * written before the thorax/abdomen marking system stay readable:
     * `NONE` becomes the thorax, `RIGHT_WING` becomes the abdomen, and
     * `LEFT_WING` stays the legacy value it always was.
     */
    @TypeConverter
    fun stringToMarkPosition(value: String): MarkPosition =
        MarkPosition.fromPersistedToken(value)
            ?: throw IllegalArgumentException("unknown mark position token: $value")

    @TypeConverter
    fun beePresenceResultToString(value: BeePresenceResult): String = value.name

    @TypeConverter
    fun stringToBeePresenceResult(value: String): BeePresenceResult = BeePresenceResult.valueOf(value)
}
