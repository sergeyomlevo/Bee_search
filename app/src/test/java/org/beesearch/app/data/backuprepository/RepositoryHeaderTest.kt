package org.beesearch.app.data.backuprepository

import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryHeaderTest {
    private val id = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val header = RepositoryHeader(id, "Dev")

    @Test fun `encoding is deterministic and round trips`() {
        val encoded = RepositoryHeaderCodec.encode(header)
        assertArrayEquals(encoded, RepositoryHeaderCodec.encode(header))
        assertEquals(header, RepositoryHeaderCodec.decode(encoded))
        assertEquals(
            "{\"repositoryFormat\":\"beesearch-repository\",\"repositoryFormatVersion\":1,\"repositoryId\":\"$id\",\"variant\":\"Dev\"}",
            encoded.toString(Charsets.UTF_8),
        )
    }

    @Test fun `parser rejects malformed utf8 duplicates unknown fields and invalid values`() {
        val invalidInputs = listOf(
            "{\"repositoryFormat\":\"beesearch-repository\",\"repositoryFormatVersion\":1,\"repositoryId\":\"$id\",\"variant\":\"Dev\",\"x\":1}",
            "{\"repositoryFormat\":\"beesearch-repository\",\"repositoryFormat\":\"beesearch-repository\",\"repositoryFormatVersion\":1,\"repositoryId\":\"$id\",\"variant\":\"Dev\"}",
            "{\"repositoryFormat\":\"other\",\"repositoryFormatVersion\":1,\"repositoryId\":\"$id\",\"variant\":\"Dev\"}",
            "{\"repositoryFormat\":\"beesearch-repository\",\"repositoryFormatVersion\":2,\"repositoryId\":\"$id\",\"variant\":\"Dev\"}",
            "{\"repositoryFormat\":\"beesearch-repository\",\"repositoryFormatVersion\":1,\"repositoryId\":\"bad\",\"variant\":\"Dev\"}",
            "{\"repositoryFormat\":\"beesearch-repository\",\"repositoryFormatVersion\":1,\"repositoryId\":\"$id\",\"variant\":\"dev\"}",
        )
        invalidInputs.forEach { input -> assertInvalid(input.toByteArray()) }
        assertInvalid(byteArrayOf(0xC3.toByte(), 0x28))
        assertInvalid(ByteArray(4097) { ' '.code.toByte() })
    }

    @Test fun `parser accepts json escapes and only json whitespace`() {
        val escaped = "{\n  \"repositoryFormat\":\"beesearch-\\u0072epository\",\r\n  \"repositoryFormatVersion\":1,\t\"repositoryId\":\"$id\",\n  \"variant\":\"D\\u0065v\"\n}"
        assertEquals(header, RepositoryHeaderCodec.decode(escaped.toByteArray()))
        assertInvalid("{\u000B\"repositoryFormat\":\"beesearch-repository\"}".toByteArray())
    }

    @Test fun `only stable beta and dev variants encode`() {
        listOf("Stable", "Beta", "Dev").forEach { variant ->
            assertEquals(RepositoryHeader(id, variant), RepositoryHeaderCodec.decode(RepositoryHeaderCodec.encode(RepositoryHeader(id, variant))))
        }
        assertInvalid { RepositoryHeaderCodec.encode(RepositoryHeader(id, "staging")) }
    }

    @Test fun `unsupported numeric version is typed and string version rejected`() {
        val text = RepositoryHeaderCodec.encode(header).toString(Charsets.UTF_8)
        val failure = org.junit.Assert.assertThrows(RepositoryException::class.java) {
            RepositoryHeaderCodec.decode(text.replace("Version\":1", "Version\":2").toByteArray())
        }
        assertEquals(RepositoryError.UNSUPPORTED_FORMAT, failure.error)
        assertInvalid(text.replace("Version\":1", "Version\":\"1\"").toByteArray())
        assertInvalid(text.replace(id.toString(), "1-2-3-4-5").toByteArray())
        assertInvalid(text.replace("Version\":1", "Version\":1.0").toByteArray())
    }

    private fun assertInvalid(bytes: ByteArray) {
        org.junit.Assert.assertThrows(RepositoryException::class.java) { RepositoryHeaderCodec.decode(bytes) }
    }

    private fun assertInvalid(block: () -> Unit) {
        org.junit.Assert.assertThrows(RepositoryException::class.java, block)
    }
}
