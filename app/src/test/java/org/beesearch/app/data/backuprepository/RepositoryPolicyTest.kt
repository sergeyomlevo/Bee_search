package org.beesearch.app.data.backuprepository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RepositoryPolicyTest {
    private val policy = RepositoryCapacityPolicy()

    @Test fun `slack has minimum and rounds percentage up`() {
        assertEquals(16L * 1024 * 1024, policy.slack(0))
        assertEquals(214_748_365L, policy.slack(20L * 1024 * 1024 * 1024))
        assertEquals(16L * 1024 * 1024, policy.slack(1))
        assertEquals(16L * 1024 * 1024, policy.slack(1_600_000_001))
        assertEquals(16_777_216L, policy.slack(1_677_721_600))
        assertEquals(16_777_217L, policy.slack(1_677_721_601))
    }

    @Test fun `budget and capacity include reserve safely`() {
        val size = 20L * 1024 * 1024 * 1024
        assertEquals(size + 214_748_365L, policy.budget(size))
        policy.require(21L * 1024 * 1024 * 1024, 1L * 1024 * 1024 * 1024)
        assertThrows(RepositoryException::class.java) { policy.require(20L * 1024 * 1024 * 1024, 1) }
    }

    @Test fun `exact artifact boundary and one byte below`() {
        val required = 20L * 1024 * 1024 * 1024 + policy.budget(1234)
        policy.require(required, policy.budget(1234))
        assertError(RepositoryError.CAPACITY_INSUFFICIENT) { policy.require(required - 1, policy.budget(1234)) }
        assertError(RepositoryError.CAPACITY_UNKNOWN) { policy.require(Long.MAX_VALUE, Long.MAX_VALUE) }
        RepositoryCapacityPolicy(0).require(1234, 1234)
        assertEquals("mp4", CanonicalExtension.resolve(listOf("video/mp4", null)))
    }

    @Test fun `invalid and overflowing capacity fail closed`() {
        assertError(RepositoryError.CAPACITY_UNKNOWN) { policy.require(null, 1) }
        assertError(RepositoryError.CAPACITY_UNKNOWN) { policy.require(1, -1) }
        assertError(RepositoryError.CAPACITY_UNKNOWN) { policy.budget(Long.MAX_VALUE) }
        assertError(RepositoryError.CAPACITY_UNKNOWN) { policy.slack(-1) }
        assertError(RepositoryError.CAPACITY_INSUFFICIENT) { policy.require(0, 0) }
    }

    @Test fun `canonical extension ignores unknown hints and rejects conflicts`() {
        assertEquals("jpg", CanonicalExtension.resolve(listOf(" image/JPEG ", "application/octet-stream")))
        assertEquals("bin", CanonicalExtension.resolve(listOf(".MP4")))
        assertEquals("bin", CanonicalExtension.resolve(emptyList()))
        assertError(RepositoryError.METADATA_INCONSISTENCY) {
            CanonicalExtension.resolve(listOf("image/jpeg", "video/mp4"))
        }
    }

    private fun assertError(expected: RepositoryError, block: () -> Unit) {
        val error = assertThrows(RepositoryException::class.java, block)
        assertEquals(expected, error.error)
    }
}
