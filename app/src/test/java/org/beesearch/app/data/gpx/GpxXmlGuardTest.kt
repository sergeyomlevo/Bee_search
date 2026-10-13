package org.beesearch.app.data.gpx

import java.io.StringReader
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class GpxXmlGuardTest {
    private fun transform(xml: String, metrics: GpxXmlMetrics = GpxXmlMetrics()): String =
        GpxXmlGuard(StringReader(xml), metrics = metrics, collectStarts = false).readText()

    private fun rejected(reason: String? = null, xml: String) {
        val error = runCatching { transform(xml) }.exceptionOrNull()
        assertTrue(error is GpxXmlFailure)
        if (reason != null) assertEquals(reason, (error as GpxXmlFailure).reason)
    }

    @Test fun referencesHaveBoundedSpellingWithoutRawDigitCeiling() {
        val metrics = GpxXmlMetrics()
        val zeros = "0".repeat(1_000_000)
        assertEquals("<e a=\"&#x9;\">&#x41;&#x1f600;&amp;#65;</e>",
            transform("<e a=\"&#9;\">&#" + zeros + "65;&#x" + zeros + "1F600;&amp;#65;</e>", metrics))
        assertEquals(2_000_008L, metrics.referenceDigits)
        assertTrue(metrics.maxReferenceState <= 4)
        assertTrue(metrics.maxBufferedChars < 100)
    }

    @Test fun attributeNormalizationPreservesReferencedOrigin() {
        assertEquals("<e a=\"a b c d e\" r=\"&#x9;&#xd;&#xa;&#x20;\"/>",
            transform("<e a=\"a\tb\rc\nd\r\ne\" r=\"&#9;&#13;&#10;&#32;\"/>"))
    }

    @Test fun lineEndsTextAndOpaqueConstructsAreNotAttributeWhitespace() {
        assertEquals("<e>a\nb\nc\t<![CDATA[<!DOCTYPE &x;]]><!--<!DOCTYPE-->"+
            "<?p\t data?></e>",
            transform("<e>a\rb\r\nc\t<![CDATA[<!DOCTYPE &x;]]><!--<!DOCTYPE-->"+
                "<?p\t data?></e>"))
    }

    @Test fun declarationBarrierAndUndefinedEntitiesFailClosed() {
        for (xml in listOf("<!DOCTYPE e><e/>", "<!ENTITY x 'x'><e/>",
            "<!DOCTYPE e SYSTEM 'file:///x'><e/>", "<!DOCTYPE e PUBLIC 'id' 'http://x'><e/>")) {
            val error = runCatching { transform(xml) }.exceptionOrNull() as GpxXmlFailure
            assertEquals("SECURITY_REJECTED", error.category)
        }
        rejected(xml="<e>&custom;</e>")
    }

    @Test fun malformedAndIllegalReferencesAreNotRepaired() {
        for (r in listOf("&#;", "&#x;", "&#X41;", "&#-1;", "&#0;",
            "&#xD800;", "&#x110000;", "&#12;", "&#65", "&#4q;")) {
            rejected(xml="<e>$r</e>")
        }
        assertEquals("<e>&#x10ffff;</e>", transform("<e>&#1114111;</e>"))
    }

    @Test fun lexicalDuplicateAttributesAndNamespaceDeclarationsReject() {
        rejected("DUPLICATE_LEXICAL_ATTRIBUTE", "<e a='1' a='2'/>")
        rejected("DUPLICATE_LEXICAL_ATTRIBUTE", "<e xmlns:a='u' xmlns:a='v'/>")
        rejected("DUPLICATE_LEXICAL_ATTRIBUTE", "<e xmlns='u' xmlns='v'/>")
    }

    @Test fun decodedRunLimitDoesNotResetAtReferenceOrCdataBoundaries() {
        assertEquals("<e>" + "a".repeat(8191) + "&#x41;</e>",
            transform("<e>" + "a".repeat(8191) + "&#00065;</e>"))
        rejected("G09", "<e>" + "a".repeat(8192) + "&#65;</e>")
        rejected("G09", "<e>" + "a".repeat(8192) + "<![CDATA[x]]></e>")
    }

    @Test fun legalAndOneOverCommentAndAttributeCeilings() {
        val l = "a".repeat(8192)
        assertEquals("<e><!--" + l + "--></e>", transform("<e><!--" + l + "--></e>"))
        rejected("G09", "<e><!--" + l + "a--></e>")
        assertEquals("<e a=\"" + l + "\"/>", transform("<e a='" + l + "'/>").replace("'", "\""))
        rejected("G09", "<e a='" + l + "a'/>")
    }

    @Test fun depthAndAttributesIncludeIgnoredContent() {
        transform("<e>".repeat(32) + "</e>".repeat(32))
        rejected("G05", "<e>".repeat(33) + "</e>".repeat(33))
        val attrs = (0 until 64).joinToString(" ") { "a$it='v'" }
        transform("<e $attrs/>")
        rejected("G06", "<e $attrs extra='v'/>")
    }

    @Test fun logicalEventsCoalesceCharacterDataAndCountEmptyElementsTwice() {
        val m = GpxXmlMetrics()
        transform("<?xml version='1.0'?><e>a&#65;<![CDATA[b]]><!--c--><?p x?><z/></e>", m)
        assertEquals(8L, m.events)
    }

    @Test fun cancellationInterruptsLongZeroReferenceWithoutAcceptedOutput() {
        var checks = 0
        var accepted = false
        val result = runCatching {
            GpxXmlGuard(StringReader("<e>&#" + "0".repeat(200_000) + "65;</e>"),
                checkCancellation = { if (++checks == 1000) throw CancellationException("probe") },
                collectStarts = false).readText()
            accepted = true
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(accepted)
    }
}
