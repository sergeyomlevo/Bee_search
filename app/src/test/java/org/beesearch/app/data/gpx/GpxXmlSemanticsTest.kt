package org.beesearch.app.data.gpx

import org.junit.Assert.*
import org.junit.Test

class GpxXmlSemanticsTest {
    @Test fun textEntityCdataFragmentationDoesNotChangeAgreement() {
        val s = GpxXmlSemantics(true)
        s.expected('S', "e")
        s.expected('T', "A😀")
        s.expected('T', "\t&")
        s.expected('E', "e")
        s.observed('S', "e")
        s.observed('T', "A")
        s.observed('T', "😀\t")
        s.observed('T', "&")
        s.observed('E', "e")
        s.complete()
    }
    @Test fun equalCountsCannotHideValueOrClassificationDisagreement() {
        for (kind in listOf('T','C','P')) {
            val s = GpxXmlSemantics(true)
            s.expected(kind, "a")
            val result=runCatching { s.observed(kind,"b") }
            assertTrue(result.exceptionOrNull() is GpxXmlFailure)
        }
        val s=GpxXmlSemantics(true)
        s.expected('T',"x")
        assertTrue(runCatching {s.observed('S',"x")}.isFailure)
    }
    @Test fun missingDataCannotBecomeComplete() {
        val s=GpxXmlSemantics(true)
        s.expected('T',"a")
        assertTrue(runCatching {s.complete()}.isFailure)
    }
}
