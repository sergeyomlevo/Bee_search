package org.beesearch.app.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchObjectMarkerTest {
    @Test
    fun approvedTypesMapToDistinctShapeIdentities() {
        assertEquals(ResearchMarkerShape.BEE, ResearchMarkerType.OBSERVATION_POINT.shape)
        assertEquals(ResearchMarkerShape.TREE, ResearchMarkerType.HOLLOW.shape)
        assertEquals(ResearchMarkerShape.LOG, ResearchMarkerType.LOG_HIVE.shape)
        assertEquals(ResearchMarkerShape.BOX, ResearchMarkerType.TRAP.shape)
        assertEquals(ResearchMarkerShape.HOUSE, ResearchMarkerType.APIARY.shape)
        assertEquals(5, ResearchMarkerType.entries.map { it.shape }.toSet().size)
    }

    @Test
    fun catalogUsesApprovedFamilyColorsAndLabels() {
        assertEquals(Color(0xFFFFB91F), ResearchMarkerType.OBSERVATION_POINT.familyColor)
        assertEquals(Color(0xFF168A37), ResearchMarkerType.HOLLOW.familyColor)
        assertEquals(Color(0xFFA04420), ResearchMarkerType.LOG_HIVE.familyColor)
        assertEquals(Color(0xFF1268D9), ResearchMarkerType.TRAP.familyColor)
        assertEquals(Color(0xFF5C19B4), ResearchMarkerType.APIARY.familyColor)
        assertEquals("Точка наблюдения", ResearchMarkerType.OBSERVATION_POINT.contentDescription)
    }

    @Test
    fun selectedPresentationRetainsTypeAndShape() {
        val normal = ResearchMarkerCatalog.presentation(ResearchMarkerType.LOG_HIVE)
        val selected = ResearchMarkerCatalog.presentation(ResearchMarkerType.LOG_HIVE, selected = true)
        assertEquals(normal.type, selected.type)
        assertEquals(normal.shape, selected.shape)
        assertTrue(selected.selected)
    }

    @Test
    fun approvedNormalSizeIs32dpAndPxCandidatesStayDiagnostic() {
        assertEquals(32, ResearchMarkerCatalog.NORMAL_SIZE_DP)
        assertEquals(48, ResearchMarkerCatalog.TOUCH_TARGET_SIZE_DP)
        assertEquals(listOf(20, 24, 32), ResearchMarkerCatalog.diagnosticSizesPx)
        // A px candidate is not a dp size: the same entry scales with the device density.
        assertEquals(24.dp, researchMarkerSizeDp(24, density = 1f))
        assertEquals(8.dp, researchMarkerSizeDp(24, density = 3f))
    }
}
