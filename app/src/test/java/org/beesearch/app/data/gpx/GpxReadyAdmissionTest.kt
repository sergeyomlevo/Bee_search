package org.beesearch.app.data.gpx

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure boundary coverage for the READY admission decision. */
class GpxReadyAdmissionTest {
    @Test fun g11AllowsTheLastImportAndRejectsTheNext() {
        assertNull(gpxAdmissionRejection(1023, 0, 0, duplicate = false, confirmed = false))
        assertNotNull(gpxAdmissionRejection(1024, 0, 0, duplicate = false, confirmed = false))
    }

    @Test fun g12AllowsTheExactSampleBoundaryAndRejectsOverflow() {
        assertNull(gpxAdmissionRejection(0, 199_999, 1, duplicate = false, confirmed = false))
        assertNotNull(gpxAdmissionRejection(0, 200_000, 1, duplicate = false, confirmed = false))
        assertNull(gpxAdmissionRejection(0, 199_998, 2, duplicate = false, confirmed = false))
        assertNotNull(gpxAdmissionRejection(0, 199_999, 2, duplicate = false, confirmed = false))
    }

    @Test fun g11AndG12AreIndependentAndDuplicateRequiresConfirmation() {
        assertNotNull(gpxAdmissionRejection(1024, 0, 0, duplicate = false, confirmed = false))
        assertNull(gpxAdmissionRejection(0, 200_000, 0, duplicate = false, confirmed = false))
        assertNotNull(gpxAdmissionRejection(0, 0, 0, duplicate = true, confirmed = false))
        assertNull(gpxAdmissionRejection(0, 0, 0, duplicate = true, confirmed = true))
    }

    @Test fun sampleAdditionCannotOverflowAndCountsBelowBoundaryRemainAllowed() {
        assertNull(gpxAdmissionRejection(1022, 199_998, 1, false, false))
        assertNotNull(gpxAdmissionRejection(0, Long.MAX_VALUE, 1, false, false))
        assertNotNull(gpxAdmissionRejection(0, 0, Long.MAX_VALUE, false, false))
    }
}
