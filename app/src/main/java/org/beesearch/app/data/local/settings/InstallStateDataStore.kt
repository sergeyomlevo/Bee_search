package org.beesearch.app.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

/**
 * Directory of the install-local preference file, excluded from every backup mode.
 *
 * `res/xml/data_extraction_rules.xml` excludes it from cloud backup and from device transfer, and
 * `res/xml/backup_rules.xml` excludes it from the legacy full backup. Android backup rules select files
 * and directories rather than single preference keys, so this directory is what keeps install-local
 * state out of a restored or transferred installation.
 */
internal const val INSTALL_STATE_DIRECTORY = "install-state"

private const val INSTALL_STATE_NAME = "install_state"

/**
 * Preferences that belong to one installation and must never travel with a backup.
 *
 * This is not a second first-run flag: it holds the Initial Setup offer flag, whose authoritative
 * storage moved out of the backed-up settings file (`bee_search_settings.preferences_pb`), where the
 * key of the same name is now inert.
 */
internal fun installStateDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        produceFile = { File(context.filesDir, "$INSTALL_STATE_DIRECTORY/$INSTALL_STATE_NAME.preferences_pb") },
    )
