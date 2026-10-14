package org.beesearch.app.data.gpx

import org.beesearch.app.domain.gpx.GpxCanonicalDraft
import org.beesearch.app.domain.gpx.GpxSample
import org.beesearch.app.domain.gpx.GpxSegment
import org.beesearch.app.domain.gpx.GpxTrack
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.IdentityHashMap

internal class GpxCm1Failure(val reason: String) : IOException("CM1:$reason")

/** This is the checked arithmetic used by real sizing AND every written u32 header. */
internal object GpxCm1Sizes {
    const val MAX_U32 = 0xffffffffL
    fun u32(value: Long): Long {
        if (value !in 0..MAX_U32) throw GpxCm1Failure("U32_OVERFLOW")
        return value
    }
    fun add(left: Long, right: Long): Long = checked {
        requireNonnegative(left, right)
        Math.addExact(left, right)
    }
    fun multiply(left: Long, right: Long): Long = checked {
        requireNonnegative(left, right)
        Math.multiplyExact(left, right)
    }
    fun field(payload: Long): Long = add(7, u32(payload))
    private fun requireNonnegative(a: Long, b: Long) {
        if (a < 0 || b < 0) throw GpxCm1Failure("NEGATIVE_SIZE")
    }
    private inline fun checked(block: () -> Long): Long = try { block() } catch (_: ArithmeticException) {
        throw GpxCm1Failure("LONG_OVERFLOW")
    }
}

/** Stage1B §8 only. CM1 is comparison material, never a digest, logical ID or backup codec. */
internal object GpxCm1Encoder {
    private val magic = "BEEGPXCM".toByteArray(Charsets.US_ASCII)
    private data class Field(val tag: Int, val value: Any?)

    // Bounded structural-size cache: no per-sample tree, payload copy or complete byte array.
    private class Sizing(val cancel: () -> Unit) {
        val records = IdentityHashMap<Any, Long>()
        val lists = IdentityHashMap<List<*>, Long>()
    }

    fun size(draft: GpxCanonicalDraft, checkCancellation: () -> Unit = {}): Long {
        val sizing = Sizing(checkCancellation)
        val size = GpxCm1Sizes.add(10, recordSize(draft, sizing))
        checkCancellation()
        return size
    }

    /** Caller owns the stream and cleanup of partial output on IO/cancellation failure. */
    fun write(draft: GpxCanonicalDraft, output: OutputStream, checkCancellation: () -> Unit = {}): Long {
        val sizing = Sizing(checkCancellation)
        val total = GpxCm1Sizes.add(10, recordSize(draft, sizing))
        checkCancellation()
        val out = DataOutputStream(output)
        out.write(magic)
        out.writeShort(1)
        writeRecord(draft, out, sizing)
        checkCancellation()
        return total
    }

    private fun fields(record: Any): List<Field> = when (record) {
        is GpxCanonicalDraft -> listOf(
            Field(1, record.declaredGpxVersion), Field(2, record.metadata.creator),
            Field(3, record.metadata.sourceName), Field(4, record.metadata.sourceDesc),
            Field(5, record.metadata.authorName), Field(6, record.tracks),
        )
        is GpxTrack -> listOf(
            Field(1, record.ordinal.toString()), Field(2, record.name), Field(3, record.description),
            Field(4, record.segments),
        )
        is GpxSegment -> listOf(Field(1, record.ordinal.toString()), Field(2, record.samples))
        is GpxSample -> listOf(
            Field(1, record.ordinal.toString()), Field(2, record.lat.canonicalPlain),
            Field(3, record.lon.canonicalPlain), Field(4, record.elevation?.canonicalPlain),
            Field(5, record.time.raw), Field(6, record.time.status.name),
            Field(7, record.time.epochSeconds?.toString()), Field(8, record.time.nanos?.toString()),
        )
        else -> throw GpxCm1Failure("INVALID_RECORD")
    }

    private fun recordSize(record: Any, sizing: Sizing): Long {
        sizing.cancel()
        sizing.records[record]?.let { return it }
        var size = 0L
        for (field in fields(record)) size = GpxCm1Sizes.add(size, GpxCm1Sizes.field(payloadSize(field.value, sizing)))
        if (record !is GpxSample) sizing.records[record] = size
        return size
    }

    private fun payloadSize(value: Any?, sizing: Sizing): Long {
        sizing.cancel()
        return when (value) {
            null -> 0L
            is String -> utf8(value).size.toLong()
            is List<*> -> sizing.lists[value] ?: run {
                val count = GpxCm1Sizes.u32(value.size.toLong())
                var size = GpxCm1Sizes.add(4, GpxCm1Sizes.multiply(count, 7))
                for (record in value) {
                    val payload = GpxCm1Sizes.u32(recordSize(record ?: invalidList(), sizing))
                    size = GpxCm1Sizes.add(size, payload)
                }
                GpxCm1Sizes.u32(size)
                sizing.lists[value] = size
                size
            }
            else -> recordSize(value, sizing)
        }
    }

    private fun writeRecord(record: Any, out: DataOutputStream, sizing: Sizing) {
        sizing.cancel()
        for ((tag, value) in fields(record)) {
            sizing.cancel()
            val kind = when (value) { null -> 0; is String -> 1; is List<*> -> 3; else -> 2 }
            header(out, tag, kind, payloadSize(value, sizing))
            when (value) {
                null -> Unit
                is String -> out.write(utf8(value))
                is List<*> -> {
                    u32(out, value.size.toLong())
                    for (item in value) {
                        sizing.cancel()
                        val child = item ?: invalidList()
                        header(out, 1, 2, recordSize(child, sizing))
                        writeRecord(child, out, sizing)
                    }
                }
                else -> writeRecord(value, out, sizing)
            }
        }
    }

    private fun header(out: DataOutputStream, tag: Int, kind: Int, length: Long) {
        // Tags/kinds come only from the closed, ascending schema above.
        out.writeShort(tag)
        out.writeByte(kind)
        u32(out, length)
    }
    private fun u32(out: DataOutputStream, value: Long) { out.writeInt(GpxCm1Sizes.u32(value).toInt()) }
    private fun invalidList(): Nothing = throw GpxCm1Failure("NULL_RECORD")
    private fun utf8(value: String): ByteArray = try {
        val buffer = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
        ByteArray(buffer.remaining()).also { buffer.get(it) }
    } catch (_: CharacterCodingException) { throw GpxCm1Failure("INVALID_UNICODE") }
}