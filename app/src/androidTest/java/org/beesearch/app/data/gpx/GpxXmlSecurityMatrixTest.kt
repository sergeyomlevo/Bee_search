package org.beesearch.app.data.gpx

import android.os.Build
import android.os.Debug
import android.os.StrictMode
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Capability variants only. No Activity, MapLibre, GPX model or persistence fixture. */
@RunWith(AndroidJUnit4::class)
class GpxXmlSecurityMatrixTest {
    private fun bytes(body: String, attributes: String = ""): ByteArray =
        ("<?xml version='1.0' encoding='UTF-8'?><gpx xmlns='"+NS+
            "' version='1.1' creator='probe' "+attributes+">"+body+"</gpx>").toByteArray()

    private fun measured(id: String, data: ByteArray, chunk: Int = 4096,
        observe: (GpxXmlObservation) -> Unit = {}): GpxXmlVerification {
        assertTrue(Build.VERSION.SDK_INT == 29 || Build.VERSION.SDK_INT == 37)
        val runtime = Runtime.getRuntime()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val allocatedBefore = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
        val start = System.nanoTime()
        try {
            val r = AndroidGpxXmlReader.verify(open = { Chunks(data, chunk) }, observe = observe)
            val after = runtime.totalMemory() - runtime.freeMemory()
            val allocatedAfter = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
            val delta = if (allocatedBefore != null && allocatedAfter != null && allocatedAfter >= allocatedBefore)
                (allocatedAfter - allocatedBefore).toString() else "UNKNOWN"
            Log.i(TAG, "RECORD scenario="+id+" api="+Build.VERSION.SDK_INT+
                " build="+Build.FINGERPRINT+" provider="+r.provider+" config="+r.configuration+
                " inputBytes="+data.size+" passes=2 bytes1="+r.preflightMetrics.bytesRead+
                " bytes2="+r.metrics.bytesRead+" decoded1="+r.preflightMetrics.decodedChars+
                " decoded2="+r.metrics.decodedChars+" deliveredUtf16="+r.metrics.deliveredChars+
                " elapsedNs="+(System.nanoTime()-start)+" heapBefore="+before+" heapAfter="+after+
                " allocatedDelta="+delta+" heapPeak=UNKNOWN maxPieceUtf16="+r.metrics.maxBufferedChars+
                " maxPending="+r.metrics.maxPendingStarts+" maxToken="+r.metrics.maxTokenScalars+
                " depth="+r.metrics.maxDepth+" events="+r.metrics.events+
                " cancellation=NOT_APPLICABLE violations=NONE OOM=NO outcome=VERIFIED_XML")
            assertEquals(data.size.toLong(), r.identity.length)
            assertEquals(sha(data), r.identity.sha256)
            return r
        } catch (e: GpxXmlFailure) {
            Log.i(TAG, "RECORD scenario="+id+" api="+Build.VERSION.SDK_INT+" inputBytes="+data.size+
                " elapsedNs="+(System.nanoTime()-start)+" passes=UNKNOWN heapBefore="+before+
                " heapPeak=UNKNOWN allocation=UNKNOWN outcome="+e.category+" reason="+e.reason+
                " OOM=NO partialAccepted=NO")
            throw e
        }
    }

    private fun reject(category: String, id: String, data: ByteArray, chunk: Int = 1) {
        val failure = runCatching { measured(id, data, chunk) }.exceptionOrNull()
        assertTrue("No accepted result for "+id+": "+failure, failure is GpxXmlFailure)
        assertEquals(id, category, (failure as GpxXmlFailure).category)
    }

    @Test fun maximumLegallyDeliverableGuardedTokens() {
        val text = "a".repeat(8192)
        val cases = listOf(
            "text" to text, "comment" to ("<!--"+text+"-->"),
            "cdata" to ("<![CDATA["+text+"]]>"),
            "pi" to ("<?p "+"a".repeat(8190)+"?>"),
            "attribute" to ("<x value='"+text+"'/>"),
            "ignored" to ("<extensions><!--"+text+"--></extensions>"),
            "reference" to ("&#"+"0".repeat(1_000_000)+"65;"))
        for ((id, body) in cases) {
            var observedText = ""
            val r = measured("S18-"+id, bytes(body)) { event ->
                if (event.kind == GpxXmlObservation.Kind.TEXT ||
                    event.kind == GpxXmlObservation.Kind.CDATA) observedText += event.value.orEmpty()
                if (event.kind == GpxXmlObservation.Kind.START && event.name == "x")
                    assertEquals(text, event.attributes.single().value)
                if (event.kind == GpxXmlObservation.Kind.COMMENT && id != "reference")
                    assertEquals(text, event.value)
            }
            assertTrue(r.metrics.maxTokenScalars <= 8192)
            assertTrue(r.metrics.maxPendingStarts <= 4)
            if (id == "text" || id == "cdata") assertEquals(text, observedText)
            if (id == "reference") {
                assertEquals("A", observedText)
                assertTrue(r.metrics.maxBufferedChars < 1024)
            }
        }
    }


    @Test fun supplementaryAndReferenceDenseMaximumTokens() {
        val scalar=String(Character.toChars(0x10000))
        val unicode=scalar.repeat(8192)
        val maximum=String(Character.toChars(0x10FFFF)).repeat(8192)
        for ((id,body,expected) in listOf(
            Triple("unicode-text",unicode,unicode),
            Triple("unicode-comment","<!--"+unicode+"-->",unicode),
            Triple("unicode-cdata","<![CDATA["+unicode+"]]>",unicode),
            Triple("unicode-pi","<?"+unicode+"?>",unicode),
            Triple("dense-reference","&#x10ffff;".repeat(8192),maximum),
            Triple("unicode-ignored","<extensions><!--"+unicode+"--></extensions>",unicode))) {
            val value=StringBuilder()
            measured("S18-"+id,bytes(body)) { e ->
                if(e.kind in listOf(GpxXmlObservation.Kind.TEXT,GpxXmlObservation.Kind.CDATA,
                    GpxXmlObservation.Kind.COMMENT,GpxXmlObservation.Kind.PROCESSING_INSTRUCTION))
                    value.append(e.value)
            }
            assertEquals(expected,value.toString())
        }
    }

    @Test fun compoundMaximumAttributeDeliveryAndRawByteBudget() {
        val ref = "&#x10ffff;".repeat(8192)
        val attrs = (0 until 64).joinToString(" ") { index ->
            (String(Character.toChars(0x10000)).repeat(8191) + String(Character.toChars(0x10000+index))) + "='" + ref + "'"
        }
        val expected = String(Character.toChars(0x10FFFF)).repeat(8192)
        val tag = String(Character.toChars(0x10000)).repeat(8192)
        measured("S18-max-start-tag", bytes("<" + tag + " " + attrs + "/>")) {
            if (it.kind == GpxXmlObservation.Kind.START && it.name == tag) {
                assertEquals(64, it.attributes.size)
                for (a in it.attributes) assertEquals(expected, a.value)
            }
        }
        val block = "<!--" + "a".repeat(8192) + "-->"
        val baseLength = bytes("").size
        fun padded(total: Int): ByteArray {
            val n = total - baseLength
            val body = StringBuilder(n)
            repeat(n / block.length) { body.append(block) }
            val tail = n % block.length
            if (tail >= 7) body.append("<!--").append("a".repeat(tail - 7)).append("-->")
            else body.append(" ".repeat(tail))
            return bytes(body.toString()).also { assertEquals(total, it.size) }
        }
        measured("G01-below", padded(16 * 1024 * 1024 - 1))
        measured("G01-exact", padded(16 * 1024 * 1024))
        reject("LIMIT_EXCEEDED", "G01-over", padded(16 * 1024 * 1024 + 1), 4096)
    }

    @Test fun semanticAgreementAcrossEncodingsAndAdversarialChunks() {
        val core = "<gpx xmlns='"+NS+"' version='1.1' creator='probe'>"+
            "<extensions><x literal='a\tb\rc\nd\r\ne' referenced='&#9;&#13;&#10;&#32;'>"+
            "A😀\r\n<![CDATA[x&]]>&#9;</x><!--<!DOCTYPE fake>--><?p data?></extensions></gpx>"
        val variants = listOf(core.toByteArray(),
            byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte())+core.toByteArray(),
            byteArrayOf(0xFF.toByte(),0xFE.toByte())+core.toByteArray(Charsets.UTF_16LE),
            byteArrayOf(0xFE.toByte(),0xFF.toByte())+core.toByteArray(Charsets.UTF_16BE))
        for ((encoding, data) in variants.withIndex()) for (chunk in listOf(1,2,3,7,64)) {
            val text = StringBuilder()
            val r = measured("chunks-"+encoding+"-"+chunk, data, chunk) { e ->
                if (e.kind == GpxXmlObservation.Kind.START && e.name == "x") {
                    assertEquals("a b c d e", e.attributes.first { it.name=="literal" }.value)
                    assertEquals("\t\r\n ", e.attributes.first { it.name=="referenced" }.value)
                }
                if (e.kind == GpxXmlObservation.Kind.TEXT || e.kind == GpxXmlObservation.Kind.CDATA)
                    text.append(e.value)
            }
            assertEquals("A😀\nx&\t", text.toString())
            assertTrue(r.configuration.contains("namespaces=true;docdecl=false;relaxed=false"))
        }
    }


    @Test fun declarationAndReadAheadBoundariesUseInheritedTokenSemantics() {
        val core="<gpx xmlns='"+NS+"' version='1.1' creator='probe'>"+
            "<x>one<![CDATA[two]]>&amp;<![CDATA[three]]><!--four--><?p five?><y/>six</x></gpx>"
        val base="xml version='1.0'"
        for(size in listOf(8191,8192)) {
            measured("G09-declaration-"+size,("<?"+base+" ".repeat(size-base.length)+"?>"+core).toByteArray(),1)
        }
        reject("LIMIT_EXCEEDED","G09-declaration-over",
            ("<?"+base+" ".repeat(8193-base.length)+"?>"+core).toByteArray(),1)
    }

    @Test fun malformedEncodingAndTruncationFailClosed() {
        val prefix = "<gpx xmlns='"+NS+"' version='1.1' creator='probe'>"
        for ((name, charset, bom) in listOf(
            Triple("LE", Charsets.UTF_16LE, byteArrayOf(0xFF.toByte(),0xFE.toByte())),
            Triple("BE", Charsets.UTF_16BE, byteArrayOf(0xFE.toByte(),0xFF.toByte())))) {
            val start = bom + prefix.toByteArray(charset)
            val high = if (name=="LE") byteArrayOf(0x00,0xD8.toByte()) else byteArrayOf(0xD8.toByte(),0x00)
            val low = if (name=="LE") byteArrayOf(0x00,0xDC.toByte()) else byteArrayOf(0xDC.toByte(),0x00)
            reject("MALFORMED_XML", "UTF16-"+name+"-odd", start+byteArrayOf(0x41))
            reject("MALFORMED_XML", "UTF16-"+name+"-high", start+high)
            reject("MALFORMED_XML", "UTF16-"+name+"-low", start+low+"</gpx>".toByteArray(charset))
        }
        for (bad in listOf(byteArrayOf(0xC0.toByte(),0xAF.toByte()),
            byteArrayOf(0xF0.toByte(),0x9F.toByte()), byteArrayOf(0xED.toByte(),0xA0.toByte(),0x80.toByte()))) {
            reject("MALFORMED_XML", "UTF8-invalid", prefix.toByteArray()+bad)
        }
        for (utf32 in listOf(byteArrayOf(0xFF.toByte(),0xFE.toByte(),0,0,0x3C,0,0,0),
            byteArrayOf(0,0,0xFE.toByte(),0xFF.toByte(),0,0,0,0x3C),
            byteArrayOf(0x3C,0,0,0),byteArrayOf(0,0,0,0x3C))) {
            reject("UNSUPPORTED_ENCODING", "UTF32-forbidden", utf32)
        }
        reject("MALFORMED_XML", "truncated-XML", prefix.toByteArray())
        reject("UNSUPPORTED_ENCODING", "unsupported-declaration",
            "<?xml version='1.0' encoding='ISO-8859-1'?><gpx/>".toByteArray())
    }


    @Test fun sourceOpenAndReadFailureHaveBoundedDiagnostics() {
        for (failAt in listOf(1,2)) {
            var opens=0
            val error=runCatching {
                AndroidGpxXmlReader.verify(open={
                    if(++opens==failAt) throw java.io.IOException("unowned-secret-uri")
                    ByteArrayInputStream(bytes("<x/>"))
                })
            }.exceptionOrNull()
            assertTrue(error is GpxXmlFailure)
            assertEquals("INPUT_IO_ERROR", (error as GpxXmlFailure).category)
            assertEquals("source-open", error.reason)
        }
        val failure=runCatching {
            AndroidGpxXmlReader.verify(open={ object : InputStream() {
                override fun read():Int = throw java.io.IOException("attacker-text")
            }})
        }.exceptionOrNull()
        assertTrue(failure is GpxXmlFailure)
        assertEquals("source-read", (failure as GpxXmlFailure).reason)
        Log.i(TAG,"INPUT_IO open1/open2/read=INPUT_IO_ERROR rawTextExposed=NO partialAccepted=NO")
    }


    @Test fun predefinedReferencesRemainDataAndNeverReparse() {
        val encoded="&quot;&apos;&lt;&gt;&amp;"
        var attribute:String?=null
        val text=StringBuilder()
        measured("predefined-all-five",bytes("<x v='"+encoded+"'>"+encoded+
            "&amp;#65;&lt;e/&gt;</x>"),1) { e ->
            if(e.kind==GpxXmlObservation.Kind.START && e.name=="x")
                attribute=e.attributes.first { it.name=="v" }.value
            if(e.kind==GpxXmlObservation.Kind.TEXT) text.append(e.value)
        }
        assertEquals("\"'<>&",attribute)
        assertEquals("\"'<>&&#65;<e/>",text.toString())
    }

    @Test fun malformedReferencesNeverRecover() {
        for (ref in listOf("&#;", "&#x;", "&#X41;", "&#-1;", "&#0;", "&#xD800;",
            "&#x110000;", "&#12;", "&#65", "&#4q;", "&custom;")) {
            reject("MALFORMED_XML", "bad-reference", bytes("<x>"+ref+"</x>"))
        }
        val text = StringBuilder()
        measured("scalar-boundaries", bytes("<x>&#9;&#x10FFFF;</x>"), 1) {
            if(it.kind == GpxXmlObservation.Kind.TEXT) text.append(it.value)
        }
        assertEquals("\t"+String(Character.toChars(0x10FFFF)), text.toString())
    }

    @Test fun namespaceAndIgnoredAttributeInvariants() {
        for (content in listOf("<x a='1' a='2'/>",
            "<x xmlns:a='u' xmlns:a='v'/>", "<x xmlns='u' xmlns='v'/>",
            "<x xmlns:a='u' xmlns:b='u' a:x='1' b:x='2'/>",
            "<x p:a='1'/>", "<x xmlns:p=''/>",
            "<x xmlns:xml='u'/>", "<x xmlns:p='http://www.w3.org/XML/1998/namespace'/>",
            "<x xmlns='http://www.w3.org/XML/1998/namespace'/>",
            "<x xmlns:p='http://www.w3.org/2000/xmlns/'/>",
            "<x a:b:c='1'/>")) {
            reject("MALFORMED_XML", "M2-ignored", bytes("<extensions>"+content+"</extensions>"))
        }
        measured("M2-distinct/default", bytes(
            "<x xmlns:a='u1' xmlns:b='u2' a:x='1' b:x='2'/>"+
            "<x xmlns='' a='1'/><x xml:lang='en'/>"), 1) { e ->
            if (e.kind == GpxXmlObservation.Kind.START) {
                for (a in e.attributes) when(a.name) {
                    "a:x" -> assertEquals("u1", a.namespace)
                    "b:x" -> assertEquals("u2", a.namespace)
                    "a" -> { assertEquals("", a.namespace); assertEquals("", e.namespace) }
                    "xml:lang" -> assertEquals("http://www.w3.org/XML/1998/namespace", a.namespace)
                }
            }
        }
    }

    @Test fun forbiddenDeclarationsAndNoXmlControlledExternalIo() {
        measured("warm-input", bytes("<x/>"))
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 250
        val violations = mutableListOf<String>()
        val old = StrictMode.getThreadPolicy()
        try {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyListener(Executor { it.run() }) { violations += it.javaClass.simpleName }.build())
            for (decl in listOf("<!DOCTYPE gpx>", "<!DOCTYPE gpx [<!ENTITY x 'v'>]>",
                "<!DOCTYPE gpx SYSTEM 'file:///d106-unowned'>",
                "<!DOCTYPE gpx SYSTEM 'http://127.0.0.1:"+server.localPort+"/dtd'>",
                "<!DOCTYPE gpx PUBLIC 'id' 'http://127.0.0.1:"+server.localPort+"/public'>",
                "<!DOCTYPE gpx [<!ENTITY % p SYSTEM 'file:///d106-unowned'>%p;]>",
                "<!DOCTYPE gpx [<!ENTITY x '&x;'>]>")) {
                reject("SECURITY_REJECTED", "DTD-barrier", (decl+String(bytes("<x/>"))
                    .substringAfter("?>")).toByteArray(), 1)
            }
            measured("locator-is-data", bytes("<xi:include xmlns:xi='http://www.w3.org/2001/XInclude' "+
                "href='http://127.0.0.1:"+server.localPort+"/include'/>",
                "xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' "+
                "xsi:schemaLocation='urn:x http://127.0.0.1:"+server.localPort+"/schema'"), 1)
        } finally { StrictMode.setThreadPolicy(old) }
        try {
            server.accept().close()
            fail("XML-triggered loopback connection")
        } catch (_: SocketTimeoutException) {
            Log.i(TAG, "IO structuralBarrier=YES loopbackConnections=0 StrictMode="+violations+
                " universalSyscallProof=UNKNOWN")
        } finally { server.close() }
        assertTrue("Parser-thread I/O signals: "+violations, violations.isEmpty())
    }

    @Test fun changedSecondPassStillCannotDeliverForbiddenMarker() {
        var opens=0
        var starts=0
        val error=runCatching {
            AndroidGpxXmlReader.verify(open = {
                opens++
                ByteArrayInputStream(if(opens==1) bytes("<x/>")
                    else ("<!DOCTYPE gpx SYSTEM 'file:///d106-unowned'>"+String(bytes("")))
                        .toByteArray())
            }, observe = { if(it.kind==GpxXmlObservation.Kind.START) starts++ })
        }.exceptionOrNull()
        assertTrue(error is GpxXmlFailure)
        assertEquals("SECURITY_REJECTED", (error as GpxXmlFailure).category)
        assertEquals(2,opens)
        assertEquals(0,starts)
    }

    @Test fun depthAttributeAndEventBudgetsIncludeAllSubtrees() {
        measured("G05-exact",bytes("<x>".repeat(31)+"</x>".repeat(31)))
        reject("LIMIT_EXCEEDED","G05-over",bytes("<x>".repeat(32)+"</x>".repeat(32)))
        val attrs=(0 until 32).joinToString(" "){"xmlns:p"+it+"='u'"}+" "+
            (0 until 32).joinToString(" "){"a"+it+"='v'"}
        measured("G06-exact",bytes("<x "+attrs+"/>"),1)
        reject("LIMIT_EXCEEDED","G06-over",bytes("<x "+attrs+" extra='v'/>"))
        val children="<x/>".repeat(999_998)
        measured("G07-below",bytes(children))
        measured("G07-exact",bytes(children+"<!--x-->"))
        reject("LIMIT_EXCEEDED","G07-over",bytes(children+"<x/>"),4096)
    }

    @Test fun cancellationDuringActualProviderPassCannotReturnSuccess() {
        var opens=0
        var checks=0
        var accepted=false
        val data=bytes("&#"+"0".repeat(200_000)+"65;")
        val failure=runCatching {
            AndroidGpxXmlReader.verify(open={opens++; ByteArrayInputStream(data)},
                checkCancellation={ if(opens==2 && ++checks==10_000) throw CancellationException("probe") })
            accepted=true
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertFalse(accepted)
        Log.i(TAG,"CANCEL requestPoint=second-pass-check10000 observedChecks="+checks+
            " opens="+opens+" outcome=CANCELLED partialAccepted=NO")
    }

    @Test fun finiteUnguardedProviderCharacterizationIsContextOnly() {
        val large="a".repeat(20_000)
        for(body in listOf(large,"<!--"+large+"-->","<![CDATA["+large+"]]>",
            "<?p "+large+"?>","<x a='"+large+"'/>")) {
            val p=XmlPullParserFactory.newInstance().newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true)
            p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false)
            p.setInput(StringReader(String(bytes(body))))
            var maximum=0
            while(p.nextToken()!=XmlPullParser.END_DOCUMENT) {
                maximum=maxOf(maximum,p.text?.length?:0)
                if(p.eventType==XmlPullParser.START_TAG) for(i in 0 until p.attributeCount)
                    maximum=maxOf(maximum,p.getAttributeValue(i).length)
            }
            Log.i(TAG,"CHARACTERIZATION guardBypassed=true finitePayloadScalars=20000 provider="+
                p.javaClass.name+" maxReturnedUtf16="+maximum+" classification=CONTEXT_ONLY")
        }
    }

    private fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b)
        .joinToString(""){"%02x".format(it)}
    private class Chunks(val bytes:ByteArray,val chunk:Int):InputStream() {
        var at=0
        override fun read():Int=if(at==bytes.size)-1 else bytes[at++].toInt() and 255
        override fun read(b:ByteArray,off:Int,len:Int):Int {
            if(len==0)return 0
            if(at==bytes.size)return -1
            val n=minOf(len,chunk,bytes.size-at); bytes.copyInto(b,off,at,at+n); at+=n; return n
        }
    }
    companion object { const val TAG="D106_XML"; const val NS="http://www.topografix.com/GPX/1/1" }
}
