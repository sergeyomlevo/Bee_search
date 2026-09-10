package org.beesearch.app.data.backup

import org.beesearch.app.domain.backup.MalformedBackup
import org.junit.Assert.assertThrows
import org.junit.Test

class MapCoverageValidatorTest {
    @Test fun acceptsEmptyAndMultipleStrictV1Fragments() {
        MapCoverageValidator.validate("v1")
        MapCoverageValidator.validate("v1|56.2,42.8,56.1,42.7|57.0,43.0,56.9,42.9")
    }

    @Test fun rejectsMalformedVersionCoordinatesAndBounds() {
        listOf(
            "", "v2", "v1|bad", "v1|56,42,57,41",
            "v1|91,42,56,41", "v1|56,181,55,41", "v1|NaN,42,55,41",
        ).forEach { value ->
            assertThrows(value, MalformedBackup::class.java) { MapCoverageValidator.validate(value) }
        }
    }
}
