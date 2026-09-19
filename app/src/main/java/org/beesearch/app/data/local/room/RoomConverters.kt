package org.beesearch.app.data.local.room

import androidx.room.TypeConverter
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.WeatherStatus
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
    fun stringToMarkPosition(value: String): MarkPosition = when (value) {
        // Compatibility with DEV data written by the short-lived
        // thorax/abdomen marking build. In the current model NONE is the
        // thorax mark and RIGHT_WING carries the former abdomen meaning.
        "THORAX" -> MarkPosition.NONE
        "ABDOMEN" -> MarkPosition.RIGHT_WING
        else -> MarkPosition.valueOf(value)
    }

    @TypeConverter
    fun beePresenceResultToString(value: BeePresenceResult): String = value.name

    @TypeConverter
    fun stringToBeePresenceResult(value: String): BeePresenceResult = BeePresenceResult.valueOf(value)

    @TypeConverter fun attachmentTypeToString(value: AttachmentType): String = value.name
    @TypeConverter fun stringToAttachmentType(value: String): AttachmentType = AttachmentType.valueOf(value)
    @TypeConverter fun weatherStatusToString(value: WeatherStatus): String = value.name
    @TypeConverter fun stringToWeatherStatus(value: String): WeatherStatus = WeatherStatus.valueOf(value)
}
