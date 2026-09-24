package org.beesearch.app.data.local.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Android backup rules select files and directories, so these two resources are what keeps install-local
 * state out of a cloud restore, a device transfer and the legacy full backup.
 *
 * A real cloud restore is not part of this suite, so the configuration itself is verified: the exclusion
 * set of every mode must match exactly, which also proves that no useful file was excluded by accident.
 */
class InstallStateBackupRulesTest {

    @Test
    fun installStateIsExcludedFromCloudBackupAndDeviceTransfer() {
        val rules = rulesDocument("data_extraction_rules.xml")

        assertEquals(expectedExclusions, excludedPaths(rules, "cloud-backup"))
        assertEquals(expectedExclusions, excludedPaths(rules, "device-transfer"))
    }

    @Test
    fun installStateIsExcludedFromTheLegacyFullBackup() {
        val rules = rulesDocument("backup_rules.xml")

        assertEquals("full-backup-content", rules.tagName)
        assertEquals(expectedExclusions, excludedPaths(rules, "full-backup-content"))
    }

    @Test
    fun theSettingsDataStoreStaysBackupAble() {
        val everyExcludedPath = listOf("data_extraction_rules.xml", "backup_rules.xml")
            .map(::rulesDocument)
            .flatMap { rules ->
                val excludes = rules.getElementsByTagName("exclude")
                (0 until excludes.length).map { (excludes.item(it) as Element).getAttribute("path") }
            }

        assertTrue(
            "the settings DataStore keeps current Territory, current Observer, map coverage and map " +
                "package pointers, so no rule may exclude it: $everyExcludedPath",
            everyExcludedPath.none { it.startsWith("datastore") },
        )
        assertTrue(everyExcludedPath.none { it.contains("bee_search_settings") })
    }

    private fun rulesDocument(name: String): Element {
        val candidates = listOf(File("src/main/res/xml/$name"), File("app/src/main/res/xml/$name"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Backup rules resource was not found in ${candidates.joinToString()}")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun excludedPaths(root: Element, section: String): Set<String> {
        // The legacy file has no separate sections: its root element is the section itself.
        val sectionElement = if (root.tagName == section) {
            root
        } else {
            val sections = root.getElementsByTagName(section)
            assertEquals("exactly one <$section> element is expected", 1, sections.length)
            sections.item(0) as Element
        }
        val excludes = sectionElement.getElementsByTagName("exclude")
        return (0 until excludes.length)
            .map { (excludes.item(it) as Element).getAttribute("path") }
            .toSet()
    }

    private companion object {
        /** Bound to the production directory constant, so code and backup rules cannot drift apart. */
        val expectedExclusions = setOf("map-packages/", "dev-bootstrap/", "$INSTALL_STATE_DIRECTORY/")
    }
}
