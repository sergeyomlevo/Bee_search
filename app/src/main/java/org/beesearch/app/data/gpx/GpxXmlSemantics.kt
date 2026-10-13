package org.beesearch.app.data.gpx

import java.util.ArrayDeque

/**
 * Bounded lexical witness, not XML tree/profile parsing. T is logical decoded character
 * data (permissible text/entity/CDATA callback fragmentation is irrelevant).
 * S/E compare lexical QNames; attribute/namespace facts have their separate adapter owner.
 * This ephemeral witness is NOT a content identity, canonical model, CM1 or persistence.
 */
internal class GpxXmlSemantics(private val enabled: Boolean) {
    private data class Pending(val kind: Char, val value: String, var at: Int = 0)
    private val queue = ArrayDeque<Pending>()

    fun expected(kind: Char, value: String) {
        if (!enabled || (kind == 'T' && value.isEmpty())) return
        // Qualification constraint on provider consumption/read-ahead, never a raw input limit.
        if (queue.size >= 4) gpxFail("SECURITY_CONFIGURATION_UNAVAILABLE", "GUARD_SDK_READ_AHEAD")
        queue.addLast(Pending(kind, value))
    }

    fun observed(kind: Char, value: String) {
        if (kind == 'T') {
            for (c in value) {
                val next = queue.peekFirst()
                    ?: gpxFail("SECURITY_REJECTED", "GUARD_SDK_UNEXPECTED_TEXT")
                if (next.kind != 'T' || next.at >= next.value.length || next.value[next.at++] != c)
                    gpxFail("SECURITY_REJECTED", "GUARD_SDK_TEXT_DISAGREEMENT")
                if (next.at == next.value.length) queue.removeFirst()
            }
            return
        }
        val next = queue.pollFirst()
            ?: gpxFail("SECURITY_REJECTED", "GUARD_SDK_UNEXPECTED_BOUNDARY")
        if (next.kind != kind || next.at != 0 || next.value != value)
            gpxFail("SECURITY_REJECTED", "GUARD_SDK_BOUNDARY_DISAGREEMENT")
    }

    fun complete() {
        if (queue.isNotEmpty()) gpxFail("SECURITY_REJECTED", "GUARD_SDK_MISSING_OBSERVATION")
    }
}
