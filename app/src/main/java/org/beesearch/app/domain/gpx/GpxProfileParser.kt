package org.beesearch.app.domain.gpx

import org.beesearch.app.domain.gpx.GpxProfileSchema.Node
import java.util.ArrayDeque
import java.util.EnumMap

/** Deterministic supported-profile state machine over already validated XML facts. */
internal class GpxProfileParser(private val checkCancellation: () -> Unit = {}) {
    private class Frame(val node: Node) {
        var order = -1
        val seen = HashSet<Node>()
        val text = StringBuilder()
        var scalars = 0
        var highSurrogate = false
    }
    private data class Warning(var count: Long, val first: GpxProfileAddress)
    private val frames = ArrayDeque<Frame>()
    private val draft = GpxProfileDraftBuilder()
    private val warnings = EnumMap<GpxWarningCategory, Warning>(GpxWarningCategory::class.java)
    private var ignoredDepth = 0
    private var rootSeen = false
    private var rootClosed = false
    private var terminated = false

    fun on(event: GpxProfileEvent) {
        if (terminated) fail("INVALID_STRUCTURE", "PROFILE_NOT_ACTIVE")
        try {
            checkCancellation()
            when (event) {
                is GpxProfileEvent.Start -> start(event)
                is GpxProfileEvent.End -> end(event)
                is GpxProfileEvent.Text -> text(event.value)
                GpxProfileEvent.ProcessingInstruction -> if (ignoredDepth == 0) warn(GpxWarningCategory.UNSUPPORTED_CONTENT)
            }
        } catch (error: GpxValueFailure) {
            val address = address()
            abort()
            throw GpxProfileFailure(error.category, error.reason, address)
        } catch (error: Throwable) {
            abort()
            throw error
        }
    }

    /** Call only after the upstream XML/security/EOF/source-consistency gate has succeeded. */
    fun finish(): GpxProfileResult = try {
        if (terminated || !rootSeen || !rootClosed || frames.isNotEmpty() || ignoredDepth != 0)
            fail("INVALID_STRUCTURE", "INCOMPLETE_PROFILE")
        checkCancellation()
        if (draft.sampleCount == 0) fail("NO_IMPORTABLE_TRACK", "NO_SUPPORTED_SAMPLE")
        val result = GpxProfileResult(draft.freeze(), GpxFrozenList(GpxWarningCategory.entries.mapNotNull { category ->
            warnings[category]?.let { GpxProfileWarning(category, it.count, it.first) }
        }))
        checkCancellation()
        abort() // Release tentative builders; returned graph owns frozen lists.
        result
    } catch (error: Throwable) { abort(); throw error }

    fun abort() {
        terminated = true
        frames.clear(); warnings.clear(); draft.clear(); ignoredDepth = 0
    }

    private fun start(event: GpxProfileEvent.Start) {
        if (ignoredDepth > 0) { ignoredDepth++; return }
        if (rootClosed) fail("INVALID_STRUCTURE", "ELEMENT_AFTER_ROOT")
        if (frames.isEmpty()) {
            if (rootSeen || event.namespace != GpxProfileSchema.NAMESPACE || event.name != "gpx")
                fail("UNSUPPORTED_PROFILE", "ROOT_PROFILE")
            val version = attribute(event, "version") ?: fail("INVALID_STRUCTURE", "MISSING_VERSION")
            if (version != "1.1") fail("UNSUPPORTED_PROFILE", "GPX_VERSION")
            draft.creator = attribute(event, "creator") ?: fail("INVALID_STRUCTURE", "MISSING_CREATOR")
            GpxValueText.check(draft.creator, 1024, "G08")
            rootSeen = true
            frames.addLast(Frame(Node.ROOT))
            attributes(event, setOf("version", "creator"))
            return
        }
        val parent = frames.last
        if (parent.node.scalar) fail("INVALID_STRUCTURE", "MARKUP_IN_SCALAR")
        val rule = if (event.namespace == GpxProfileSchema.NAMESPACE) GpxProfileSchema.rule(parent.node, event.name) else null
        if (rule == null) {
            if (event.namespace == GpxProfileSchema.NAMESPACE && GpxProfileSchema.misplaced(parent.node, event.name))
                fail("INVALID_STRUCTURE", "MISPLACED_SUPPORTED_NODE")
            warn(GpxWarningCategory.UNSUPPORTED_CONTENT)
            ignoredDepth = 1
            return
        }
        if (rule.order < parent.order || (!rule.repeated && !parent.seen.add(rule.child)))
            fail("INVALID_STRUCTURE", "SUPPORTED_ORDER_OR_CARDINALITY")
        parent.order = rule.order
        frames.addLast(Frame(rule.child))
        when (rule.child) {
            Node.TRACK -> {
                if (draft.tracks.size >= 128) fail("LIMIT_EXCEEDED", "G02")
                draft.track = GpxProfileDraftBuilder.Track(draft.tracks.size)
            }
            Node.SEGMENT -> {
                if (draft.segmentCount >= 4096) fail("LIMIT_EXCEEDED", "G03")
                draft.segmentCount++
                draft.segment = GpxProfileDraftBuilder.Segment(checkNotNull(draft.track).segments.size)
            }
            Node.SAMPLE -> {
                if (draft.sampleCount >= 100000) fail("LIMIT_EXCEEDED", "G04")
                val lat = attribute(event, "lat") ?: fail("INVALID_COORDINATE", "MISSING_LATITUDE")
                val lon = attribute(event, "lon") ?: fail("INVALID_COORDINATE", "MISSING_LONGITUDE")
                draft.sample = GpxProfileDraftBuilder.Sample(checkNotNull(draft.segment).samples.size,
                    GpxCanonicalValues.latitude(lat), GpxCanonicalValues.longitude(lon))
                draft.sampleCount++
            }
            else -> Unit
        }
        attributes(event, if (rule.child == Node.SAMPLE) setOf("lat", "lon") else emptySet())
    }

    private fun end(event: GpxProfileEvent.End) {
        if (ignoredDepth > 0) { ignoredDepth--; return }
        val frame = frames.peekLast() ?: fail("INVALID_STRUCTURE", "UNEXPECTED_END")
        if (event.namespace != GpxProfileSchema.NAMESPACE || event.name != frame.node.xmlName)
            fail("INVALID_STRUCTURE", "NORMALIZED_END_MISMATCH")
        if (frame.highSurrogate) fail("MALFORMED_XML", "INVALID_UNICODE_SCALAR")
        when (frame.node) {
            Node.SAMPLE -> draft.closeSample()
            Node.SEGMENT -> draft.closeSegment()
            Node.TRACK -> draft.closeTrack()
            Node.ROOT -> rootClosed = true
            else -> if (frame.node.scalar) scalar(frame)
        }
        frames.removeLast()
    }

    private fun scalar(frame: Frame) {
        val value = frame.text.toString()
        val parent = frames.elementAt(frames.size - 2).node
        when (frame.node) {
            Node.NAME -> when (parent) {
                Node.METADATA -> draft.sourceName = value
                Node.AUTHOR -> draft.authorName = value
                Node.TRACK -> checkNotNull(draft.track).name = value
                else -> fail("INVALID_STRUCTURE", "SCALAR_PARENT")
            }
            Node.DESC -> if (parent == Node.METADATA) draft.sourceDesc = value else checkNotNull(draft.track).description = value
            Node.ELE -> checkNotNull(draft.sample).elevation = GpxCanonicalValues.elevation(value)
            Node.TIME -> {
                val time = GpxCanonicalValues.time(value)
                checkNotNull(draft.sample).time = time
                if (time.status == GpxTimeStatus.NO_ZONE || time.status == GpxTimeStatus.UNSUPPORTED)
                    warn(GpxWarningCategory.UNSUPPORTED_TIME)
            }
            else -> Unit
        }
    }

    private fun text(value: String) {
        if (ignoredDepth > 0) return
        val frame = frames.peekLast()
        if (frame == null || !frame.node.scalar) {
            if (value.any { it != ' ' && it != '\t' && it != '\r' && it != '\n' })
                fail("INVALID_STRUCTURE", "TEXT_IN_CONTAINER")
            return
        }
        val limit = when (frame.node) { Node.TIME -> 128; Node.ELE -> 8192; else -> 1024 }
        val id = when (frame.node) { Node.TIME -> "G10"; Node.ELE -> "G09"; else -> "G08" }
        // Count scalars incrementally across provider fragmentation, before append/trim.
        for (char in value) {
            if (frame.highSurrogate) {
                if (!Character.isLowSurrogate(char)) fail("MALFORMED_XML", "INVALID_UNICODE_SCALAR")
                frame.highSurrogate = false
            } else {
                if (Character.isLowSurrogate(char)) fail("MALFORMED_XML", "INVALID_UNICODE_SCALAR")
                frame.highSurrogate = Character.isHighSurrogate(char)
                if (++frame.scalars > limit) fail("LIMIT_EXCEEDED", id)
            }
        }
        frame.text.append(value)
    }

    private fun attribute(event: GpxProfileEvent.Start, name: String): String? =
        event.attributes.firstOrNull { it.namespace.isEmpty() && it.name == name }?.value

    private fun attributes(event: GpxProfileEvent.Start, supported: Set<String>) {
        event.attributes.forEach {
            if (it.namespace.isNotEmpty() || it.name !in supported) warn(GpxWarningCategory.UNSUPPORTED_CONTENT)
        }
    }

    private fun address(): GpxProfileAddress {
        val scope = frames.lastOrNull { !it.node.scalar }?.node?.scope ?: GpxProfileScope.ROOT
        return if (scope == GpxProfileScope.SAMPLE && draft.sample == null) GpxProfileAddress(scope, draft.track?.ordinal, draft.segment?.ordinal, draft.segment?.samples?.size) else draft.address(scope)
    }
    private fun warn(category: GpxWarningCategory) {
        val previous = warnings[category]
        if (previous == null) warnings[category] = Warning(1, address()) else previous.count++
    }
    private fun fail(category: String, reason: String): Nothing = throw GpxProfileFailure(category, reason, address())
}