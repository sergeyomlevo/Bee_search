package org.beesearch.app.data.backupsnapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SnapshotCoverageAlignmentTest {
    private val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val bounds = "{\"north\":56.0,\"east\":38.0,\"south\":55.0,\"west\":37.0}"

    @Test fun acceptsV1EmptyAndRectangles() {
        SnapshotCoverageValidator.validate("v1")
        SnapshotCoverageValidator.validate("v1|56,38,55,37|-90,-180,-90,180")
        SnapshotCoverageValidator.validate("v1|1e1,2.5e+1,1.0,0")
    }

    @Test fun acceptsV2AndPreservesAntimeridianAndDegenerateBounds() {
        SnapshotCoverageValidator.validate("v2|{\"areaId\":\"$id\",\"name\":\"meadow\",\"bounds\":[$bounds,{\"north\":0,\"east\":-180,\"south\":0,\"west\":180}]}")
    }

    @Test fun rejectsMalformedV1AndWhitespaceNumbers() {
        listOf("", "v3", "v1|", "v1|56,38,55", "v1|56, 38,55,37", "v1|NaN,38,55,37", "v1|91,38,55,37", "v1|56,181,55,37", "v1|55,38,56,37").forEach(::assertInvalid)
    }

    @Test fun rejectsV2ShapeIdentityNameNumbersAndBounds() {
        listOf(
            "v2|[]",
            "v2|{\"areaId\":\"$id\",\"name\":\"name\",\"bounds\":[]}",
            "v2|{\"areaId\":\"${id.uppercase()}\",\"name\":\"name\",\"bounds\":[$bounds]}",
            "v2|{\"areaId\":\"$id\",\"name\":\" name\",\"bounds\":[$bounds]}",
            "v2|{\"areaId\":\"$id\",\"name\":\"name\",\"bounds\":[{\"north\":\"56\",\"east\":38,\"south\":55,\"west\":37}]}",
            "v2|{\"areaId\":\"$id\",\"name\":\"name\",\"bounds\":[{\"north\":55,\"east\":38,\"south\":56,\"west\":37}]}",
            "v2|{\"areaId\":\"$id\",\"name\":\"name\",\"bounds\":[$bounds],\"extra\":1}",
        ).forEach(::assertInvalid)
    }

    @Test fun duplicateAndParserLimitFailuresRemainDistinct() {
        val duplicate = assertThrows(SnapshotException::class.java) {
            SnapshotCoverageValidator.validate("v2|{\"areaId\":\"$id\",\"name\":\"name\",\"bounds\":[$bounds],\"bounds\":[$bounds]}")
        }
        assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, duplicate.error)
        assertEquals("MAP_COVERAGE", duplicate.category)
        val limit = assertThrows(SnapshotException::class.java) {
            SnapshotCoverageValidator.validate("v2|{\"areaId\":\"$id\",\"name\":\"name\",\"bounds\":[[$bounds]]}", SnapshotLimits(depth = 1))
        }
        assertEquals(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, limit.error)
    }

    private fun assertInvalid(value: String) {
        val error = assertThrows(SnapshotException::class.java) { SnapshotCoverageValidator.validate(value) }
        assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error)
        assertEquals("MAP_COVERAGE", error.category)
    }
}
