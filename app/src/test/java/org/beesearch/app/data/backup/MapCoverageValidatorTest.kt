package org.beesearch.app.data.backup

import java.util.UUID
import org.beesearch.app.domain.backup.MalformedBackup
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaCodec
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.Assert.assertThrows
import org.junit.Test

class MapCoverageValidatorTest {
    private val areaId = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d")
    private val area = MapArea(
        id = areaId,
        name = "Лух",
        bounds = listOf(MapGeoBounds(north = 57.111673, east = 39.026918, south = 56.562186, west = 38.470994)),
    )

    @Test fun acceptsEmptyAndMultipleStrictV1Fragments() {
        MapCoverageValidator.validate("v1")
        MapCoverageValidator.validate("v1|56.2,42.8,56.1,42.7|57.0,43.0,56.9,42.9")
    }

    @Test fun acceptsAValidV2Area() {
        MapCoverageValidator.validate(MapAreaCodec.encode(area))
    }

    @Test fun acceptsAValidV2AreaWithSeveralBounds() {
        val wide = area.copy(
            bounds = area.bounds + MapGeoBounds(north = 56.9, east = 38.9, south = 56.8, west = 38.8),
        )

        MapCoverageValidator.validate(MapAreaCodec.encode(wide))
    }

    @Test fun rejectsMalformedVersionCoordinatesAndBounds() {
        listOf(
            "", "v2", "v1|bad", "v1|56,42,57,41",
            "v1|91,42,56,41", "v1|56,181,55,41", "v1|NaN,42,55,41",
        ).forEach { value ->
            assertThrows(value, MalformedBackup::class.java) { MapCoverageValidator.validate(value) }
        }
    }

    /** A damaged v2 must fail the export loudly instead of being packed as if it were correct. */
    @Test fun rejectsDamagedV2StructurallyNotOnlyByPrefix() {
        val valid = MapAreaCodec.encode(area)
        listOf(
            "v2|anything",
            "v2|{",
            "v2|[]",
            "v2|{\"areaId\":\"$areaId\",\"name\":\"Лух\",\"bounds\":[]}",
            "v2|{\"areaId\":\"not-a-uuid\",\"name\":\"Лух\",\"bounds\":[{\"north\":1.0,\"east\":1.0,\"south\":0.0,\"west\":0.0}]}",
            "v2|{\"areaId\":\"$areaId\",\"name\":\"  \",\"bounds\":[{\"north\":1.0,\"east\":1.0,\"south\":0.0,\"west\":0.0}]}",
            "v2|{\"areaId\":\"$areaId\",\"name\":\"Лух\"}",
            // One damaged character inside an otherwise valid value.
            valid.dropLast(3),
            "v3|{\"areaId\":\"$areaId\",\"name\":\"Лух\",\"bounds\":[]}",
        ).forEach { value ->
            assertThrows(value, MalformedBackup::class.java) { MapCoverageValidator.validate(value) }
        }
    }
}
