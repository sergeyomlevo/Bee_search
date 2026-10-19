package org.beesearch.app.domain.gpx

/** Closed supported projection, not a complete GPX/XSD validator. Stage1B §3. */
internal object GpxProfileSchema {
    const val NAMESPACE = "http://www.topografix.com/GPX/1/1"
    const val NAMESPACE_10 = "http://www.topografix.com/GPX/1/0"
    fun namespace(version: String): String? = when(version) { "1.0" -> NAMESPACE_10; "1.1" -> NAMESPACE; else -> null }
    enum class Node(val xmlName: String, val scope: GpxProfileScope? = null) {
        ROOT("gpx", GpxProfileScope.ROOT), METADATA("metadata", GpxProfileScope.METADATA),
        AUTHOR("author", GpxProfileScope.AUTHOR), TRACK("trk", GpxProfileScope.TRACK),
        SEGMENT("trkseg", GpxProfileScope.SEGMENT), SAMPLE("trkpt", GpxProfileScope.SAMPLE),
        AUTHOR_TEXT("author"), NAME("name"), DESC("desc"), ELE("ele"), TIME("time");
        val scalar: Boolean get() = scope == null
    }
    data class Rule(val child: Node, val order: Int, val repeated: Boolean = false)
    fun rule(parent: Node, name: String): Rule? = when (parent) {
        Node.ROOT -> when (name) { "metadata" -> Rule(Node.METADATA, 0); "trk" -> Rule(Node.TRACK, 1, true); else -> null }
        Node.METADATA -> when (name) { "name" -> Rule(Node.NAME, 0); "desc" -> Rule(Node.DESC, 1); "author" -> Rule(Node.AUTHOR, 2); else -> null }
        Node.AUTHOR -> if (name == "name") Rule(Node.NAME, 0) else null
        Node.TRACK -> when (name) { "name" -> Rule(Node.NAME, 0); "desc" -> Rule(Node.DESC, 1); "trkseg" -> Rule(Node.SEGMENT, 2, true); else -> null }
        Node.SEGMENT -> if (name == "trkpt") Rule(Node.SAMPLE, 0, true) else null
        Node.SAMPLE -> when (name) { "ele" -> Rule(Node.ELE, 0); "time" -> Rule(Node.TIME, 1); else -> null }
        else -> null
    }
    fun rule10(parent: Node, name: String): Rule? = if (parent == Node.ROOT) when(name) {
        "name" -> Rule(Node.NAME, 0); "desc" -> Rule(Node.DESC, 1);
        "author" -> Rule(Node.AUTHOR_TEXT, 2); "trk" -> Rule(Node.TRACK, 3, true); else -> null
    } else rule(parent, name)
    // These local names are canonical elsewhere, but legitimately ignored here.
    private val ignoredCollisions = mapOf(Node.METADATA to setOf("time"), Node.SAMPLE to setOf("name", "desc"))
    fun misplaced(parent: Node, name: String): Boolean =
        Node.entries.any { it.xmlName == name } && name !in ignoredCollisions[parent].orEmpty()
}