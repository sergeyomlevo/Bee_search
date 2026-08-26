package org.beesearch.app.domain.validation

import org.beesearch.app.domain.model.InvalidObserverCodeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ObserverCodeTest {
    @Test
    fun `normalization trims edges and preserves case and content`() {
        assertEquals("Сергей-01", ObserverCode.normalize("  Сергей-01\t"))
        assertEquals("Иванов А.", ObserverCode.normalize("Иванов А."))
        assertEquals("sV", ObserverCode.normalize(" sV "))
    }

    @Test
    fun `normalization rejects a blank value`() {
        assertThrows(InvalidObserverCodeException::class.java) {
            ObserverCode.normalize(" \n\t ")
        }
    }
}
