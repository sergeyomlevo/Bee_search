package org.beesearch.app.data.gpx

import java.nio.charset.StandardCharsets

/** Deterministic, generated GPX 1.1 inputs for the Stage 4 boundary matrix. */
internal object GpxBoundaryCorpus {
    data class Case(
        val id: String,
        val data: ByteArray,
        val family: String,
        val expectedReason: String? = null,
        val tracks: Int = 1,
        val segments: Int = 1,
        val samples: Int = 1,
        val events: Long? = null,
        val semanticText: String? = null,
    )

    fun tracks(n: Int): Case {
        require(n >= 1)
        val xml = graph(trackSegments = List(n) { if (it == 0) 1 else 0 }, sampleTrack = 0)
        return case("G02-tracks-$n", xml, "G02", tracks = n, segments = 1)
    }

    fun segments(n: Int): Case {
        require(n >= 1)
        val trackSegments = if (n == 1) listOf(1) else listOf(n - 1, 1)
        val xml = graph(trackSegments = trackSegments, sampleTrack = 0)
        return case("G03-segments-$n", xml, "G03", tracks = trackSegments.size, segments = n)
    }

    fun samples(n: Int): Case {
        require(n >= 1)
        val first = if (n == 1) 1 else n - 1
        val second = n - first
        val xml = graph(trackSegments = listOf(2), samplesBySegment = listOf(first, second))
        return case("G04-samples-$n", xml, "G04", segments = 2, samples = n)
    }

    fun text(n: Int): Case {
        require(n >= 1)
        val value = String(Character.toChars(0x1F600)).repeat(n)
        val xml = graphPrefix() + "<metadata><name>" + value +
            "</name></metadata>" + graphTracks() + graphSuffix()
        return case("G08-text-$n", xml, "G08", semanticText = value)
    }

    fun depth(n: Int): Case {
        require(n >= 3)
        val xml = graphPrefix() + graphTracks() + "<extensions>" + "<x>".repeat(n - 2) +
            "</x>".repeat(n - 2) + "</extensions>" + graphSuffix()
        return case("G05-depth-$n", xml, "G05")
    }

    fun attributes(n: Int): Case {
        require(n >= 1)
        val attributes = buildString {
            append("xmlns:p='urn:boundary'")
            repeat(n - 1) { append(" a").append(it).append("='v'") }
        }
        val xml = graphPrefix() + graphTracks() + "<extensions><x $attributes/></extensions>" + graphSuffix()
        return case("G06-attributes-$n", xml, "G06")
    }

    /** n is the total logical event count; the core contributes eight START/END events. */
    fun events(n: Int, chunk: Int = 4096): Case {
        require(n >= 8 && chunk > 0)
        val extra = n - 8
        val comments = when {
            extra == 0 -> 0
            extra % 2 == 0 -> 2
            else -> 1
        }
        val emptyElements = (extra - comments) / 2
        val xml = graphPrefix() + graphTracks() +
            "<x/>".repeat(emptyElements) + "<!---->".repeat(comments) +
            graphSuffix()
        return case("G07-events-$n-chunk-$chunk", xml, "G07", events = n.toLong())
    }

    fun token(n: Int): Case {
        require(n >= 1)
        val value = "a".repeat(n)
        val xml = graphPrefix() + graphTracks() +
            "<extensions><x value='$value'/></extensions>" + graphSuffix()
        return case("G09-token-$n", xml, "G09")
    }

    /** Coordinate lexical length is exactly n, including leading trim whitespace. */
    fun lexical(n: Int): Case {
        require(n >= 1)
        val latitude = if (n == 1) "1" else " " + "0".repeat(n - 2) + "1"
        val xml = graph(sampleAttribute = "lat='$latitude' lon='2'")
        return case("G10-lexical-$n", xml, "G10")
    }

    /** Pads inside the root with bounded comments/whitespace to exactly n bytes. */
    fun rawBytes(n: Int): Case {
        require(n >= 1)
        val prefix = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + (graphPrefix() + graphTracks()).toByteArray(StandardCharsets.UTF_8)
        val suffix = graphSuffix().toByteArray(StandardCharsets.UTF_8)
        require(n >= prefix.size + suffix.size)
        val room = n - prefix.size - suffix.size
        val data = ByteArray(n)
        prefix.copyInto(data)
        var cursor = prefix.size
        var remaining = room
        while (remaining >= 7) {
            val payload = minOf(8192, remaining - 7)
            "<!--".toByteArray(StandardCharsets.US_ASCII).copyInto(data, cursor)
            cursor += 4
            data.fill('a'.code.toByte(), cursor, cursor + payload)
            cursor += payload
            "-->".toByteArray(StandardCharsets.US_ASCII).copyInto(data, cursor)
            cursor += 3
            remaining -= payload + 7
        }
        if (remaining > 0) {
            data.fill(' '.code.toByte(), cursor, cursor + remaining)
            cursor += remaining
        }
        suffix.copyInto(data, cursor)
        cursor += suffix.size
        check(cursor == n)
        check(data.size == n)
        return Case("G01-raw-$n", data, "G01", if (n > 16777216) "G01" else null)
    }

    /** A long lexical numeric reference that decodes to scalar 49 ('1'). */
    fun reference(digits: Int): Case {
        require(digits >= 2)
        val spelling = "&#" + "0".repeat(digits - 2) + "49;"
        val xml = graph(sampleAttribute = "lat='$spelling' lon='2'")
        return case("G09-reference-$digits", xml, "G09")
    }

    private fun case(
        id: String,
        xml: String,
        family: String,
        expectedReason: String? = null,
        tracks: Int = 1,
        segments: Int = 1,
        samples: Int = 1,
        events: Long? = null,
        semanticText: String? = null,
    ): Case = Case(id, xml.toByteArray(StandardCharsets.UTF_8), family,
        if (isOver(id, family)) family else expectedReason, tracks, segments, samples,
        events, semanticText)

    private fun isOver(id: String, family: String): Boolean = when (family) {
        "G01" -> id.substringAfterLast('-').toLong() > 16L * 1024 * 1024
        "G02" -> id.substringAfterLast('-').toInt() > 128
        "G03" -> id.substringAfterLast('-').toInt() > 4096
        "G04" -> id.substringAfterLast('-').toInt() > 100000
        "G05" -> id.substringAfterLast('-').toInt() > 32
        "G06" -> id.substringAfterLast('-').toInt() > 64
        "G07" -> id.substringAfter("events-").substringBefore('-').toLong() > 2_000_000
        "G08" -> id.substringAfterLast('-').toInt() > 1024
        "G09" -> id.substringAfterLast('-').toInt() > 8192 && !id.contains("reference")
        "G10" -> id.substringAfterLast('-').toInt() > 128
        else -> false
    }

    private fun graph(
        trackSegments: List<Int> = listOf(1),
        samplesBySegment: List<Int> = listOf(1),
        sampleAttribute: String = "lat='1' lon='2'",
        sampleTrack: Int = 0,
    ): String {
        val body = StringBuilder()
        trackSegments.forEachIndexed { track, segmentCount ->
            body.append("<trk>")
            repeat(segmentCount) { segment ->
                body.append("<trkseg>")
                val count = if (track == sampleTrack) samplesBySegment.getOrElse(segment) { 0 } else 0
                repeat(count) { body.append("<trkpt ").append(sampleAttribute).append("/>") }
                body.append("</trkseg>")
            }
            body.append("</trk>")
        }
        return graphPrefix() + body + graphSuffix()
    }

    private fun graphPrefix() = "<gpx xmlns='$NAMESPACE' version='1.1' creator='boundary'>"
    private fun graphTracks() = "<trk><trkseg><trkpt lat='1' lon='2'/></trkseg></trk>"
    private fun graphSuffix() = "</gpx>"

    private const val NAMESPACE = "http://www.topografix.com/GPX/1/1"
}
