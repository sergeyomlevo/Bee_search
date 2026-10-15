package org.beesearch.app.data.gpx

import android.os.Build
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.IOException
import java.io.Reader
import java.util.concurrent.CancellationException

/** A bounded observation from the Android XML provider; it is not a canonical GPX model. */
internal data class GpxXmlObservation(
    val kind: Kind,
    val name: String? = null,
    val namespace: String? = null,
    val value: String? = null,
    val attributes: List<GpxObservedAttribute> = emptyList(),
) {
    internal enum class Kind { START, END, TEXT, COMMENT, CDATA, PROCESSING_INSTRUCTION }
}

internal data class GpxObservedAttribute(
    val name: String,
    val namespace: String,
    val value: String,
)

internal data class GpxXmlVerification(
    val identity: GpxOriginalIdentity,
    val metrics: GpxXmlMetrics,
    val provider: String,
    val apiLevel: Int,
    val observedStarts: Long,
    val preflightMetrics: GpxXmlMetrics,
    val configuration: String,
)

/**
 * Slice-1 Android boundary. It deliberately stops at normalized XML observations and does not
 * know GPX domain objects, persistence, MapLibre, or logical import identity.
 */
internal object AndroidGpxXmlReader {
    private const val XMLNS_URI = "http://www.w3.org/2000/xmlns/"
    private const val XML_URI = "http://www.w3.org/XML/1998/namespace"
    private const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
    private const val FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"

    fun verify(
        open: () -> InputStream,
        checkCancellation: () -> Unit = {},
        // Tentative internal sink only: caller must discard/abort on any rejection.
        // This is not publication; only the final verification result is successful.
        observe: (GpxXmlObservation) -> Unit = {},
    ): GpxXmlVerification {
        checkCancellation()

        // Configuration is a gate in its own right. Establish it before opening any input,
        // then create a fresh equivalently verified parser for the actual parse pass.
        val parser = newConfiguredParser()

        val preflightMetrics = GpxXmlMetrics()
        val preflightInput = GpxInput(openSource(open), GpxXmlLimits(), preflightMetrics, checkCancellation)
        var preflightCancellation: CancellationException? = null
        val preflightIdentity = try {
            val guard = GpxXmlGuard(preflightInput.reader, metrics = preflightMetrics, checkCancellation = checkCancellation, collectStarts = false)
            drainGuard(guard, checkCancellation)
            preflightInput.identity()
        } catch (cancelled: CancellationException) {
            preflightCancellation = cancelled
            throw cancelled
        } finally {
            preflightInput.closePreservingCancellation(preflightCancellation)
        }

        val parseMetrics = GpxXmlMetrics()
        val input = GpxInput(openSource(open), GpxXmlLimits(), parseMetrics, checkCancellation)
        val observations = ParseAccumulator(observe)
        var parseCancellation: CancellationException? = null
        return try {
            val guard = GpxXmlGuard(input.reader, metrics = parseMetrics, checkCancellation = checkCancellation, collectStarts = true)
            try {
                parser.setInput(guard)
            } catch (error: GpxXmlFailure) {
                throw error
            } catch (error: XmlPullParserException) {
                gpxFail("MALFORMED_XML", error.javaClass.simpleName)
            }
            var depth = 0
            var rootSeen = false
            var rootClosed = false
            val namespaceCounts = ArrayList<Int>()
            var providerEvents = 0L
            var providerTextRun = false
            while (true) {
                checkCancellation()
                val token = try {
                    parser.nextToken()
                } catch (error: GpxXmlFailure) {
                    throw error
                } catch (error: XmlPullParserException) {
                    guard.failure?.let { throw it }
                    error.cause?.let { if (it is java.util.concurrent.CancellationException) throw it }
                    gpxFail("MALFORMED_XML", error.javaClass.simpleName)
                } catch (error: RuntimeException) {
                    if (error is java.util.concurrent.CancellationException) throw error
                    guard.failure?.let { throw it }
                    gpxFail("MALFORMED_XML", error.javaClass.simpleName)
                }
                when (token) {
                    XmlPullParser.START_TAG -> {
                        providerTextRun = false
                        if (rootClosed) gpxFail("MALFORMED_XML", "element-after-root")
                        val facts = guard.takeStartFacts()
                        guard.semantics.observed('S', qualifiedName(parser.prefix.orEmpty(), parser.name))
                        val observation = validateStart(parser, facts, depth == 0, namespaceCounts)
                        if (depth == 0) {
                            if (rootSeen) gpxFail("MALFORMED_XML", "duplicate-root")
                            rootSeen = true
                        }
                        depth++
                        if (depth > 32) gpxFail("LIMIT_EXCEEDED", "depth")
                        observations.emit(observation)
                        providerEvents++
                    }
                    XmlPullParser.END_TAG -> {
                        providerTextRun = false
                        guard.semantics.observed('E', qualifiedName(parser.prefix.orEmpty(), parser.name))
                        if (depth <= 0) gpxFail("MALFORMED_XML", "unbalanced-end")
                        depth--
                        if (depth == 0) rootClosed = true
                        observations.emit(
                            GpxXmlObservation(
                                GpxXmlObservation.Kind.END,
                                parser.name,
                                parser.namespaceOrEmpty(),
                            ),
                        )
                        providerEvents++
                    }
                    XmlPullParser.TEXT, XmlPullParser.IGNORABLE_WHITESPACE -> {
                        guard.semantics.observed('T', parser.text.orEmpty())
                        observations.emitText(parser.text, GpxXmlObservation.Kind.TEXT)
                        if (!parser.text.isNullOrEmpty() && !providerTextRun) providerEvents++
                        if (!parser.text.isNullOrEmpty()) providerTextRun = true
                    }
                    XmlPullParser.ENTITY_REF -> {
                        // The guard preserves reference origin where it matters; the provider must
                        // expose the resulting value consistently with the guarded stream.
                        if (parser.text == null) gpxFail("MALFORMED_XML", "UNRESOLVED_REFERENCE")
                        guard.semantics.observed('T', parser.text.orEmpty())
                        observations.emitText(parser.text, GpxXmlObservation.Kind.TEXT)
                        if (!parser.text.isNullOrEmpty() && !providerTextRun) providerEvents++
                        if (!parser.text.isNullOrEmpty()) providerTextRun = true
                    }
                    XmlPullParser.COMMENT -> {
                        providerTextRun = false
                        guard.semantics.observed('C', parser.text.orEmpty())
                        observations.emitText(parser.text, GpxXmlObservation.Kind.COMMENT)
                        providerEvents++
                    }
                    XmlPullParser.CDSECT -> {
                        guard.semantics.observed('T', parser.text.orEmpty())
                        observations.emitText(parser.text, GpxXmlObservation.Kind.CDATA)
                        if (!parser.text.isNullOrEmpty() && !providerTextRun) providerEvents++
                        if (!parser.text.isNullOrEmpty()) providerTextRun = true
                    }
                    XmlPullParser.PROCESSING_INSTRUCTION -> {
                        providerTextRun = false
                        guard.semantics.observed('P', parser.text.orEmpty())
                        observations.emitText(parser.text, GpxXmlObservation.Kind.PROCESSING_INSTRUCTION)
                        providerEvents++
                    }
                    XmlPullParser.DOCDECL -> gpxFail("SECURITY_REJECTED", "docdecl-reached-provider")
                    XmlPullParser.END_DOCUMENT -> break
                    else -> Unit
                }
            }
            if (!rootSeen || !rootClosed || depth != 0) gpxFail("MALFORMED_XML", "missing-root-or-end")
            checkCancellation()
            drainGuard(guard, checkCancellation)
            guard.semantics.complete()
            if (guard.pendingStartCount != 0) gpxFail("SECURITY_CONFIGURATION_UNAVAILABLE", "GUARD_SDK_AGREEMENT")
            if (guard.failure != null) throw guard.failure!!
            if (providerEvents + parseMetrics.declarationEvents != parseMetrics.events) {
                gpxFail("SECURITY_CONFIGURATION_UNAVAILABLE", "logical-event-accounting")
            }
            val secondIdentity = input.identity()
            if (secondIdentity != preflightIdentity) gpxFail("INPUT_IO_ERROR", "SOURCE_CHANGED")
            GpxXmlVerification(
                identity = preflightIdentity,
                metrics = parseMetrics,
                provider = parserProvider(parser),
                apiLevel = Build.VERSION.SDK_INT,
                observedStarts = observations.starts,
                preflightMetrics = preflightMetrics,
                configuration = "namespaces=true;docdecl=false;relaxed=false;validation=" +
                    parser.getFeature(XmlPullParser.FEATURE_VALIDATION),
            )
        } catch (cancelled: CancellationException) {
            parseCancellation = cancelled
            throw cancelled
        } finally {
            input.closePreservingCancellation(parseCancellation)
        }
    }

    private fun openSource(open: () -> InputStream): InputStream = try {
        open()
    } catch (error: IOException) {
        if (error is GpxXmlFailure) throw error
        gpxFail("INPUT_IO_ERROR", "source-open")
    }

    private fun drainGuard(guard: Reader, checkCancellation: () -> Unit) {
        val buffer = CharArray(4096)
        while (true) {
            checkCancellation()
            if (guard.read(buffer) < 0) return
        }
    }

    private fun newConfiguredParser(): XmlPullParser {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            parser.setFeature(FEATURE_RELAXED, false)
            if (parser.getFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES) != true ||
                parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL) != false ||
                parser.getFeature(FEATURE_RELAXED) != false
            ) {
                gpxFail("SECURITY_CONFIGURATION_UNAVAILABLE", "parser-feature-state")
            }
            return parser
        } catch (error: GpxXmlFailure) {
            throw error
        } catch (error: XmlPullParserException) {
            gpxFail("SECURITY_CONFIGURATION_UNAVAILABLE", error.javaClass.simpleName)
        } catch (error: RuntimeException) {
            gpxFail("SECURITY_CONFIGURATION_UNAVAILABLE", error.javaClass.simpleName)
        }
    }

    private fun validateStart(
        parser: XmlPullParser,
        facts: GpxStartFacts,
        isRoot: Boolean,
        namespaceCounts: MutableList<Int>,
    ): GpxXmlObservation {
        val qualifiedElementName = qualifiedName(parser.prefix.orEmpty(), parser.name)
        if (qualifiedElementName != facts.name) gpxFail("SECURITY_CONFIGURATION_UNAVAILABLE", "element-name-reconciliation")
        validateQualifiedName(facts.name)
        if (isRoot && (parser.name != "gpx" || parser.namespaceOrEmpty() != GPX_NAMESPACE)) {
            gpxFail("UNSUPPORTED_PROFILE", "root")
        }
        validateNamespaceBinding(parser.prefix.orEmpty(), parser.namespaceOrEmpty())
        val declarations = facts.attributes.filter { it.name == "xmlns" || it.name.startsWith("xmlns:") }
        val ordinaryFacts = facts.attributes.filterNot { it.name == "xmlns" || it.name.startsWith("xmlns:") }
        validateNamespaceDeclarations(parser, declarations, namespaceCounts)
        if (ordinaryFacts.size != parser.attributeCount) gpxFail("MALFORMED_XML", "attribute-count-reconciliation")
        val expectedByName = ordinaryFacts.associateBy { it.name }
        if (expectedByName.size != ordinaryFacts.size) gpxFail("MALFORMED_XML", "lexical-attribute-duplicate")
        val seenExpanded = HashSet<String>(parser.attributeCount)
        val attributes = ArrayList<GpxObservedAttribute>(parser.attributeCount)
        for (index in 0 until parser.attributeCount) {
            val prefix = parser.getAttributePrefix(index).orEmpty()
            val name = if (prefix.isEmpty()) parser.getAttributeName(index) else "$prefix:${parser.getAttributeName(index)}"
            val expected = expectedByName[name] ?: gpxFail("MALFORMED_XML", "attribute-name-reconciliation")
            val namespace = parser.getAttributeNamespace(index).orEmpty()
            validateNamespaceBinding(prefix, namespace)
            validateQualifiedName(name)
            val key = "$namespace\u0000${parser.getAttributeName(index)}"
            if (!seenExpanded.add(key)) gpxFail("MALFORMED_XML", "duplicate-expanded-attribute")
            val value = parser.getAttributeValue(index)
            if (value != expected.value) gpxFail("SECURITY_REJECTED", "attribute-normalization-disagreement")
            attributes += GpxObservedAttribute(name, namespace, value)
        }
        return GpxXmlObservation(
            GpxXmlObservation.Kind.START,
            parser.name,
            parser.namespaceOrEmpty(),
            attributes = attributes,
        )
    }

    private fun validateNamespaceDeclarations(
        parser: XmlPullParser,
        expected: List<GpxLexicalAttribute>,
        namespaceCounts: MutableList<Int>,
    ) {
        val depth = parser.depth
        val before = if (depth <= 1) 0 else namespaceCounts.getOrElse(depth - 2) { 0 }
        val after = parser.getNamespaceCount(depth)
        if (after - before != expected.size) gpxFail("MALFORMED_XML", "namespace-count-reconciliation")
        val actual = HashMap<String, String>()
        for (index in before until after) {
            val prefix = parser.getNamespacePrefix(index).orEmpty()
            val name = if (prefix.isEmpty()) "xmlns" else "xmlns:$prefix"
            val value = parser.getNamespaceUri(index).orEmpty()
            if (!actual.containsKey(name)) actual[name] = value
        }
        if (actual.size != expected.size) gpxFail("MALFORMED_XML", "namespace-duplicate")
        for (declaration in expected) {
            validateQualifiedName(declaration.name)
            val prefix = if (declaration.name == "xmlns") "" else declaration.name.removePrefix("xmlns:")
            if (prefix == "xmlns" || (prefix == "xml" && declaration.value != XML_URI) ||
                (prefix != "xml" && declaration.value == XML_URI) ||
                declaration.value == XMLNS_URI ||
                (prefix.isNotEmpty() && declaration.value.isEmpty())
            ) gpxFail("MALFORMED_XML", "reserved-namespace-binding")
            if (actual[declaration.name] != declaration.value) {
                gpxFail("SECURITY_CONFIGURATION_UNAVAILABLE", "namespace-fact-reconciliation")
            }
        }
        if (namespaceCounts.size <= depth - 1) {
            while (namespaceCounts.size < depth - 1) namespaceCounts += 0
            namespaceCounts += after
        } else {
            namespaceCounts[depth - 1] = after
        }
    }

    private fun validateQualifiedName(name: String) {
        val first = name.indexOf(':')
        if (first < 0) return
        if (first == 0 || first == name.lastIndex || name.indexOf(':', first + 1) >= 0) {
            gpxFail("MALFORMED_XML", "qualified-name")
        }
    }

    private fun qualifiedName(prefix: String, name: String): String =
        if (prefix.isEmpty()) name else "$prefix:$name"

    private fun validateNamespaceBinding(prefix: String, namespace: String) {
        if (prefix == "xml" && namespace != XML_URI) gpxFail("MALFORMED_XML", "xml-binding")
        if (prefix == "xmlns" || namespace == XMLNS_URI) gpxFail("MALFORMED_XML", "xmlns-as-attribute")
        if (prefix.isNotEmpty() && namespace.isEmpty()) gpxFail("MALFORMED_XML", "unbound-prefix")
    }

    private fun XmlPullParser.namespaceOrEmpty(): String = namespace.orEmpty()

    private fun parserProvider(parser: XmlPullParser): String =
        "${parser.javaClass.name};api=${Build.VERSION.SDK_INT}"

    private class ParseAccumulator(private val observe: (GpxXmlObservation) -> Unit) {
        var starts = 0L
            private set

        fun emit(observation: GpxXmlObservation) {
            if (observation.kind == GpxXmlObservation.Kind.START) starts++
            observe(observation)
        }

        fun emitText(text: String?, kind: GpxXmlObservation.Kind) {
            if (!text.isNullOrEmpty()) emit(GpxXmlObservation(kind, value = text))
        }
    }
}
