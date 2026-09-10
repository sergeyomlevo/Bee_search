package org.beesearch.app.data.backup

import org.beesearch.app.domain.backup.MalformedBackup

internal object MapCoverageValidator {
    fun validate(value: String) {
        try {
            val parts = value.split('|')
            require(parts.firstOrNull() == "v1")
            parts.drop(1).forEach { item ->
                val n = item.split(',').map(String::toDouble)
                require(n.size == 4 && n.all(Double::isFinite) && n[0] in -90.0..90.0 && n[2] in -90.0..90.0 && n[1] in -180.0..180.0 && n[3] in -180.0..180.0 && n[0] >= n[2])
            }
        } catch (e: Exception) { throw MalformedBackup("invalid map coverage", e) }
    }
}
