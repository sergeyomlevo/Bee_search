package org.beesearch.app.data.gpx

import org.beesearch.app.domain.gpx.GpxCanonicalDraft
import org.beesearch.app.domain.gpx.GpxCanonicalValues
import org.beesearch.app.domain.gpx.GpxFrozenList
import org.beesearch.app.domain.gpx.GpxMetadata
import org.beesearch.app.domain.gpx.GpxProfileAddress
import org.beesearch.app.domain.gpx.GpxProfileFailure
import org.beesearch.app.domain.gpx.GpxProfileResult
import org.beesearch.app.domain.gpx.GpxProfileScope
import org.beesearch.app.domain.gpx.GpxProfileWarning
import org.beesearch.app.domain.gpx.GpxWarningCategory
import org.beesearch.app.domain.gpx.GpxSample
import org.beesearch.app.domain.gpx.GpxSegment
import org.beesearch.app.domain.gpx.GpxTrack
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class GpxProcessorTest {
    private val originalBytes = "<gpx version=\"1.1\"><trk><trkseg><trkpt lat=\"0\" lon=\"0\"/></trkseg></trk></gpx>"
        .toByteArray(Charsets.UTF_8)
    private val identity = GpxOriginalIdentity(originalBytes.size.toLong(), sha256(originalBytes))
    private val acceptedRead = GpxProfileRead(
        GpxProfileResult(fixtureDraft(), GpxFrozenList(fixtureWarnings())),
        verification(identity),
    )

    @Test fun acceptedHandsThroughCanonicalGraphIdentityAndWarningsExactly() {
        val result = processor { acceptedRead }.process({ ByteArrayInputStream(byteArrayOf(1)) })

        assertTrue(result is GpxParseResult.Accepted)
        result as GpxParseResult.Accepted
        assertSame(acceptedRead.profile.draft, result.draft)
        assertSame(acceptedRead.verification.identity, result.originalIdentity)
        assertSame(acceptedRead.profile.warnings, result.diagnostics)
        assertEquals(fixtureDraft(), result.draft)
        assertEquals(fixtureWarnings(), result.diagnostics)
    }

    @Test fun optionalOriginalIdentityAcceptsMatchAndRejectsChange() {
        val matching = processor { acceptedRead }.process({ ByteArrayInputStream(byteArrayOf(1)) }, identity)
        assertTrue(matching is GpxParseResult.Accepted)

        val changed = processor { acceptedRead.copy(verification = verification(GpxOriginalIdentity(205L, "other"))) }
            .process({ ByteArrayInputStream(byteArrayOf(1)) }, identity)
        assertEquals(GpxFailureCategory.INPUT_IO_ERROR, (changed as GpxParseResult.Rejected).category)
        assertEquals(GpxFailureReason.SOURCE_CHANGED, changed.boundedContext.reason)
    }

    @Test fun everyClosedCategoryMapsToTypedFiniteContext() {
        val categories = GpxFailureCategory.entries
        categories.forEach { category ->
            val result = when (category) {
                GpxFailureCategory.INPUT_IO_ERROR -> processor { throw GpxProfileFailure(category.name, "hostile-".repeat(10_000), address()) }
                    .process({ ByteArrayInputStream(byteArrayOf()) })
                GpxFailureCategory.SECURITY_CONFIGURATION_UNAVAILABLE -> processor { throw GpxXmlFailure(category.name, "hostile-".repeat(10_000)) }
                    .process({ ByteArrayInputStream(byteArrayOf()) })
                else -> processor { throw GpxXmlFailure(category.name, "hostile-".repeat(10_000)) }
                    .process({ ByteArrayInputStream(byteArrayOf()) })
            }
            assertTrue("$category should reject", result is GpxParseResult.Rejected)
            result as GpxParseResult.Rejected
            assertEquals(category, result.category)
            assertTrue(result.boundedContext.reason.code.length <= 32)
            assertFalse(result.boundedContext.reason.code.contains("hostile"))
        }
    }

    @Test fun profileFailureRetainsBoundedAddressAndG01Mapping() {
        val expectedAddress = GpxProfileAddress(GpxProfileScope.SAMPLE, 1, 2, 3)
        val profileFailure = processor { throw GpxProfileFailure("INVALID_COORDINATE", "LATITUDE_RANGE", expectedAddress) }
            .process({ ByteArrayInputStream(byteArrayOf()) }) as GpxParseResult.Rejected
        assertEquals(GpxFailureCategory.INVALID_COORDINATE, profileFailure.category)
        assertEquals(GpxFailureReason.LATITUDE_RANGE, profileFailure.boundedContext.reason)
        assertEquals(expectedAddress, profileFailure.boundedContext.address)

        val g01 = processor { throw GpxProfileFailure("LIMIT_EXCEEDED", "raw input exceeds byte limit", expectedAddress) }
            .process({ ByteArrayInputStream(byteArrayOf()) }) as GpxParseResult.Rejected
        assertEquals(GpxFailureReason.G01, g01.boundedContext.reason)
        assertEquals(expectedAddress, g01.boundedContext.address)
    }

    @Test fun ioMessagesAndUnknownProgrammingErrorsNeverEscapeAsRawContext() {
        val io = IOException("io-secret-".repeat(20_000))
        val ioResult = processor { throw io }.process({ ByteArrayInputStream(byteArrayOf()) }) as GpxParseResult.Rejected
        assertEquals(GpxFailureCategory.INPUT_IO_ERROR, ioResult.category)
        assertEquals(GpxFailureReason.SOURCE_IO, ioResult.boundedContext.reason)
        assertEquals(null, ioResult.boundedContext.address)

        val programming = IllegalStateException("unexpected")
        try {
            processor { throw programming }.process({ ByteArrayInputStream(byteArrayOf()) })
            fail("unknown programming errors must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(programming, actual)
        }
    }

    @Test fun cancellationBeforeInsideAndAfterReadRethrowsSameObjectWithSuppressedClose() {
        val phases = listOf("before", "inside", "after")
        phases.forEach { phase ->
            val marker = CancellationException(phase)
            val closeFailure = IOException("close-secret-".repeat(20_000))
            val boundedCloseFailure = GpxXmlFailure("INPUT_IO_ERROR", "SOURCE_CLOSE")
            var checks = 0
            val result = try {
                processor {
                    if (phase == "inside") {
                        marker.addSuppressed(boundedCloseFailure)
                        throw marker
                    }
                    acceptedRead
                }.process({ ByteArrayInputStream(byteArrayOf()) }, checkCancellation = {
                    checks++
                    if (phase == "before" && checks == 1 || phase == "after" && checks == 2) {
                        marker.addSuppressed(closeFailure)
                        throw marker
                    }
                })
                null
            } catch (actual: CancellationException) {
                actual
            }
            assertSame(marker, result)
            assertTrue(result!!.suppressed.contains(if (phase == "inside") boundedCloseFailure else closeFailure))
        }
    }

    @Test fun cancellationNearReturnAndCloseSuppressionNeverProducesResult() {
        val marker = CancellationException("near-return")
        val closeFailure = IOException("close")
        var checks = 0
        try {
            processor { acceptedRead }.process({ ByteArrayInputStream(byteArrayOf()) }, checkCancellation = {
                checks++
                if (checks == 3) {
                    marker.addSuppressed(closeFailure)
                    throw marker
                }
            })
            fail("cancellation must not return Accepted")
        } catch (actual: CancellationException) {
            assertSame(marker, actual)
            assertSame(closeFailure, actual.suppressed.single())
        }
    }

    @Test fun repeatedProcessingReturnsDistinctAcceptedAndRetainedGraphNeedsNoSource() {
        val processor = processor { acceptedRead }
        val first = processor.process({ error("source must not be touched") })
        val second = processor.process({ error("source must not be touched") })
        assertTrue(first is GpxParseResult.Accepted)
        assertTrue(second is GpxParseResult.Accepted)
        assertNotSame(first, second)
        assertSame((first as GpxParseResult.Accepted).draft, (second as GpxParseResult.Accepted).draft)
    }

    @Test fun acceptedCanonicalDraftMatchesIndependentCm1LiteralAndIdentityIsSeparate() {
        val result = processor { acceptedRead }.process({ ByteArrayInputStream(byteArrayOf()) }) as GpxParseResult.Accepted
        val encoded = ByteArrayOutputStream().also { output -> GpxCm1Encoder.write(result.draft, output) }.toByteArray()
        val literal = """
            424545475058434d0001
            00010100000003312e31
            0002010000000766697874757265
            00030000000000 00040000000000 00050000000000
            0006030000008f 00000001 00010200000084
            0001010000000130 00020000000000 00030000000000
            00040300000067 00000001 0001020000005c
            0001010000000130 0002030000004d 00000001 00010200000042
            0001010000000130 0002010000000130 0003010000000130
            00040000000000 00050000000000
            000601000000074d495353494e47
            00070000000000 00080000000000
        """.filterNot { it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(205, literal.size)
        assertArrayEquals(literal, encoded)
        assertNotEquals(identity.sha256, sha256(encoded))
    }

    @Test fun differentOriginalBytesKeepSameCm1WithoutProcessorDeduplication() {
        val secondBytes = "<gpx version=\"1.1\"><trk><trkseg><trkpt lat=\"+0\" lon=\"0\"/></trkseg></trk></gpx>"
            .toByteArray(Charsets.UTF_8)
        val secondIdentity = GpxOriginalIdentity(secondBytes.size.toLong(), sha256(secondBytes))
        val first = processor { acceptedRead }.process({ ByteArrayInputStream(originalBytes) }) as GpxParseResult.Accepted
        val second = processor {
            acceptedRead.copy(verification = verification(secondIdentity))
        }.process({ ByteArrayInputStream(secondBytes) }) as GpxParseResult.Accepted
        assertNotEquals(first.originalIdentity, second.originalIdentity)
        assertArrayEquals(cm1(first.draft), cm1(second.draft))
        assertNotSame(first, second)
    }

    private fun processor(read: () -> GpxProfileRead) = GpxProcessor { _, _ -> read() }

    private fun fixtureDraft(): GpxCanonicalDraft {
        val sample = GpxSample(0, GpxCanonicalValues.latitude("0"), GpxCanonicalValues.longitude("0"))
        return GpxCanonicalDraft(GpxMetadata("fixture"), listOf(GpxTrack(0, listOf(GpxSegment(0, listOf(sample))))))
    }

    private fun fixtureWarnings() = listOf(
        GpxProfileWarning(GpxWarningCategory.UNSUPPORTED_CONTENT, 1L, GpxProfileAddress(GpxProfileScope.ROOT)),
    )

    private fun verification(identity: GpxOriginalIdentity) = GpxXmlVerification(
        identity, GpxXmlMetrics(), "fixture-provider", 35, 1, GpxXmlMetrics(), "fixture-config",
    )

    private fun address() = GpxProfileAddress(GpxProfileScope.ROOT)

    private fun cm1(draft: GpxCanonicalDraft): ByteArray = ByteArrayOutputStream().also {
        GpxCm1Encoder.write(draft, it)
    }.toByteArray()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
