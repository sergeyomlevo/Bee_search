package org.beesearch.app.data.backup

import org.beesearch.app.domain.backup.MalformedBackup
import org.beesearch.app.ui.map.MapAreaCodec
import org.beesearch.app.ui.map.MapAreaReadResult

/**
 * Validates the persisted map coverage value that travels in the backup settings section.
 *
 * It is deliberately structural rather than prefix-based: a damaged value must fail the export with
 * an explicit error instead of being packed into a new archive as if it were correct. Validation
 * never rewrites or clears the stored value.
 */
internal object MapCoverageValidator {
    fun validate(value: String) {
        // A legacy selection without участки is a valid persisted value even though it means
        // "no Ареал", so it stays acceptable here.
        if (value == MapAreaCodec.EMPTY_LEGACY_VALUE) return
        when (MapAreaCodec.decode(value)) {
            is MapAreaReadResult.Present, is MapAreaReadResult.Legacy -> return
            is MapAreaReadResult.Absent, is MapAreaReadResult.Corrupt ->
                throw MalformedBackup("invalid map coverage")
        }
    }
}
