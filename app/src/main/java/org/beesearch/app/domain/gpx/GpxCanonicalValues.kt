package org.beesearch.app.domain.gpx

import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Safe categories only; never retain hostile field text in an exception. */
internal class GpxValueFailure(val category: String, val reason: String) :
    IllegalArgumentException("$category:$reason")

/** Counts decoded Unicode scalars, before any field-specific whitespace handling. */
internal object GpxValueText {
    fun check(text: String, ceiling: Int, limitId: String): String {
        var index = 0
        var count = 0
        while (index < text.length) {
            val first = text[index++]
            if (Character.isHighSurrogate(first)) {
                if (index == text.length || !Character.isLowSurrogate(text[index++])) invalidUnicode()
            } else if (Character.isLowSurrogate(first)) invalidUnicode()
            if (++count > ceiling) throw GpxValueFailure("LIMIT_EXCEEDED", limitId)
        }
        return text
    }

    private fun invalidUnicode(): Nothing =
        throw GpxValueFailure("MALFORMED_XML", "INVALID_UNICODE_SCALAR")

    fun trimEdges(text: String): String = text.trim { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
}

/** Equality is exact normalized value, not BigDecimal's scale-sensitive equality. */
internal class GpxDecimal private constructor(val canonicalPlain: String) {
    override fun equals(other: Any?): Boolean = other is GpxDecimal && canonicalPlain == other.canonicalPlain
    override fun hashCode(): Int = canonicalPlain.hashCode()
    override fun toString(): String = canonicalPlain

    internal fun inLatitudeRange(): Boolean = BigDecimal(canonicalPlain).let { it >= LAT_MIN && it <= LAT_MAX }
    internal fun inLongitudeRange(): Boolean = BigDecimal(canonicalPlain).let { it >= LON_MIN && it < LON_MAX }

    companion object {
        private val LAT_MIN = BigDecimal("-90")
        private val LAT_MAX = BigDecimal("90")
        private val LON_MIN = BigDecimal("-180")
        private val LON_MAX = BigDecimal("180")
        internal fun fromDecimal(value: BigDecimal): GpxDecimal = GpxDecimal(
            if (value.signum() == 0) "0" else value.stripTrailingZeros().toPlainString(),
        )
    }
}

internal enum class GpxTimeStatus { MISSING, UTC, NO_ZONE, UNSUPPORTED }

internal data class GpxSampleTime(
    val raw: String?,
    val status: GpxTimeStatus,
    val epochSeconds: Long?,
    val nanos: Int?,
) {
    init {
        raw?.let { GpxValueText.check(it, 128, "G10") }
        require((status == GpxTimeStatus.MISSING) == (raw == null))
        if (status == GpxTimeStatus.UTC) {
            require(epochSeconds != null && epochSeconds in -62135596800L..253402300799L)
            require(nanos != null && nanos in 0..999999999)
        } else require(epochSeconds == null && nanos == null)
    }
}

/** Stage1B §§5/11: pure decoded values, independent of XML/provider and device locale. */
internal object GpxCanonicalValues {
    private val decimalGrammar = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")
    private val timeGrammar = Regex(
        "([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})" +
            "(?:\\.([0-9]+))?(Z|[+-][0-9]{2}:[0-9]{2})?",
    )

    fun latitude(raw: String): GpxDecimal = decimal(raw, 128, "G10", "INVALID_COORDINATE").also {
        if (!it.inLatitudeRange()) invalid("INVALID_COORDINATE", "LATITUDE_RANGE")
    }

    fun longitude(raw: String): GpxDecimal = decimal(raw, 128, "G10", "INVALID_COORDINATE").also {
        if (!it.inLongitudeRange()) invalid("INVALID_COORDINATE", "LONGITUDE_RANGE")
    }

    fun elevation(raw: String?): GpxDecimal? = raw?.let { decimal(it, 8192, "G09", "INVALID_OPTIONAL_VALUE") }

    private fun decimal(raw: String, ceiling: Int, id: String, category: String): GpxDecimal {
        GpxValueText.check(raw, ceiling, id)
        val text = GpxValueText.trimEdges(raw)
        if (!decimalGrammar.matches(text)) invalid(category, "DECIMAL_GRAMMAR")
        return try {
            GpxDecimal.fromDecimal(BigDecimal(text))
        } catch (_: NumberFormatException) {
            invalid(category, "DECIMAL_REPRESENTATION")
        } catch (_: ArithmeticException) {
            invalid(category, "DECIMAL_REPRESENTATION")
        }
    }

    fun time(raw: String?): GpxSampleTime {
        if (raw == null) return GpxSampleTime(null, GpxTimeStatus.MISSING, null, null)
        GpxValueText.check(raw, 128, "G10")
        val match = timeGrammar.matchEntire(GpxValueText.trimEdges(raw)) ?: return unsupported(raw)
        val fields = match.groupValues
        val fraction = fields[7]
        if (fraction.length > 9) return unsupported(raw)
        val year = fields[1].toInt()
        if (year !in 1..9999) return unsupported(raw)
        val hour = fields[4].toInt()
        val minute = fields[5].toInt()
        val second = fields[6].toInt()
        val nanos = if (fraction.isEmpty()) 0 else fraction.padEnd(9, '0').toInt()
        if (hour == 24 && (minute != 0 || second != 0 || nanos != 0)) return unsupported(raw)
        return try {
            var local = LocalDateTime.of(year, fields[2].toInt(), fields[3].toInt(),
                if (hour == 24) 0 else hour, minute, second, nanos)
            if (hour == 24) local = local.plusDays(1)
            if (local.year !in 1..9999) return unsupported(raw)
            val zone = fields[8]
            if (zone.isEmpty()) return GpxSampleTime(raw, GpxTimeStatus.NO_ZONE, null, null)
            val offset = ZoneOffset.of(zone)
            if (kotlin.math.abs(offset.totalSeconds) > 14 * 3600) return unsupported(raw)
            val seconds = local.toEpochSecond(offset)
            if (seconds !in -62135596800L..253402300799L) return unsupported(raw)
            GpxSampleTime(raw, GpxTimeStatus.UTC, seconds, nanos)
        } catch (_: DateTimeException) {
            unsupported(raw)
        }
    }

    private fun unsupported(raw: String) = GpxSampleTime(raw, GpxTimeStatus.UNSUPPORTED, null, null)
    private fun invalid(category: String, reason: String): Nothing = throw GpxValueFailure(category, reason)
}