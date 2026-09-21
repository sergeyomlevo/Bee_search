package org.beesearch.app.data.pointexport

import android.content.Intent
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.util.UUID
import org.beesearch.app.data.exchange.CreateExchangeDocument
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservationPointExportDocumentContractTest {
    @Test
    @Suppress("DEPRECATION")
    fun createDocumentUsesExpectedFilenameMimeAndExchangeDataInitialFolder() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val initial = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FBeeSearch%2FDev%2FExchange%2FData")
        val contract = CreateExchangeDocument("application/zip", initial)
        val expectedName = observationPointExportFileName(detail())

        val intent = contract.createIntent(context, expectedName)

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals(expectedName, intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(initial, intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI))
    }

    private fun detail(): ObservationPointDetail {
        val at = Instant.parse("2026-09-20T10:00:00Z")
        val territoryId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val observerId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        return ObservationPointDetail(
            ObservationPoint(UUID.fromString("11111111-1111-1111-1111-111111111111"), territoryId, observerId, 2026, 16, null, null, 0.0, 0.0, null, null, null, at, null, null),
            Territory(territoryId, "DEV", "Territory", "Region", "District", at, at),
            Observer(observerId, "O1", "Last", "First", null, null, at, at),
            emptyList(),
        )
    }
}
