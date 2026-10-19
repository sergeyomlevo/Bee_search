package org.beesearch.app.ui.map

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpxDisplayNameProviderTest {
    private val uri = Uri.parse("content://org.beesearch.app.test.gpx/document/opaque-id")
    private val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
    @Test
    fun returnsOnlyTheProviderDisplayNameIncludingLongCyrillicSpaces() {
        val name = "Полевой маршрут — северная поляна 2026.gpx"
        assertEquals(name, resolver.gpxDisplayName(uri.buildUpon().appendQueryParameter("name", name).build()))
    }

    @Test
    fun missingBlankAndProviderFailureDoNotFallBackToUri() {
        assertNull(resolver.gpxDisplayName(uri.buildUpon().appendQueryParameter("mode", "missing").build()))
        assertNull(resolver.gpxDisplayName(uri.buildUpon().appendQueryParameter("name", "   ").build()))
        assertNull(resolver.gpxDisplayName(uri))
        assertNull(resolver.gpxDisplayName(uri.buildUpon().appendQueryParameter("mode", "failure").build()))
    }
}
