package org.beesearch.app.data.exchange

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * System-picker contracts that open inside the Bee Search exchange folder.
 *
 * SAF cannot be pointed at an arbitrary directory without a previously granted permission, so the
 * exchange folder is offered as the picker's *initial* location. The user still confirms every file,
 * which is what keeps the app free of broad storage permissions.
 */
internal class OpenExchangeDocument(
    private val initialFolder: Uri?,
) : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).withInitialFolder(initialFolder)
}

internal class CreateExchangeDocument(
    mimeType: String,
    private val initialFolder: Uri?,
) : ActivityResultContracts.CreateDocument(mimeType) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).withInitialFolder(initialFolder)
}

private fun Intent.withInitialFolder(initialFolder: Uri?): Intent = apply {
    if (initialFolder != null) {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialFolder)
    }
}
