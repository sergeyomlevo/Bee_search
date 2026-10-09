package org.beesearch.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.ui.observation.BeeMarkOutline
import org.beesearch.app.ui.observation.beeMarkSegmentColors
import org.beesearch.app.ui.observation.drawBeeMark

/** Silhouettes approved for the research-object marker vocabulary. */
internal enum class ResearchMarkerShape {
    BEE,
    TREE,
    LOG,
    BOX,
    HOUSE,
}

/** Product types represented by the marker presentation boundary. */
internal enum class ResearchMarkerType(
    val shape: ResearchMarkerShape,
    val familyColor: Color,
    val contentDescription: String,
) {
    OBSERVATION_POINT(ResearchMarkerShape.BEE, Color(0xFFFFB91F), "Точка наблюдения"),
    HOLLOW(ResearchMarkerShape.TREE, Color(0xFF168A37), "Дупло"),
    LOG_HIVE(ResearchMarkerShape.LOG, Color(0xFFA04420), "Колода"),
    TRAP(ResearchMarkerShape.BOX, Color(0xFF1268D9), "Ловушка"),
    APIARY(ResearchMarkerShape.HOUSE, Color(0xFF5C19B4), "Пасека"),
}

/**
 * Central marker sizing and semantic mapping.
 *
 * [NORMAL_SIZE_DP] is the owner-approved production normal size and the only definition of a
 * normal marker. The `GATE_*_PX` values are diagnostic physical-pixel candidates kept for the
 * DEBUG density comparison; only [researchMarkerSizeDp] converts them, so a px candidate can never
 * be mistaken for a dp size.
 */
internal object ResearchMarkerCatalog {
    /** Owner-approved on Samsung S25 Ultra (RFCY90MBYVZ), 2026-10-09. */
    const val NORMAL_SIZE_DP: Int = 32
    const val GATE_SMALL_SIZE_PX: Int = 20
    const val GATE_MID_SIZE_PX: Int = 24
    const val GATE_LARGE_SIZE_PX: Int = 32
    const val TOUCH_TARGET_SIZE_DP: Int = 48

    /**
     * Height fraction of the pin path where its tip sits. Geographic markers anchor the tip, so this
     * value is shared by the drawing and by the projection offset instead of being duplicated.
     */
    const val PIN_TIP_FRACTION: Float = 0.90f

    /** Diagnostic only: never a production normal size. */
    val diagnosticSizesPx: List<Int> = listOf(GATE_SMALL_SIZE_PX, GATE_MID_SIZE_PX, GATE_LARGE_SIZE_PX)

    fun presentation(type: ResearchMarkerType, selected: Boolean = false): ResearchMarkerPresentation =
        ResearchMarkerPresentation(
            type = type,
            shape = type.shape,
            familyColor = type.familyColor,
            contentDescription = type.contentDescription,
            selected = selected,
        )
}

internal data class ResearchMarkerPresentation(
    val type: ResearchMarkerType,
    val shape: ResearchMarkerShape,
    val familyColor: Color,
    val contentDescription: String,
    val selected: Boolean,
)

/** DEBUG diagnostic: converts a physical-pixel candidate to Compose dp at the current density. */
internal fun researchMarkerSizeDp(sizePx: Int, density: Float): Dp {
    require(sizePx in ResearchMarkerCatalog.diagnosticSizesPx) { "unsupported marker size: $sizePx px" }
    require(density > 0f) { "density must be positive" }
    return (sizePx / density).dp
}

/**
 * Reusable visual marker. It has a 48dp touch/semantics box while the pin
 * itself uses the explicitly selected visual size.
 */
@Composable
internal fun ResearchObjectMarker(
    type: ResearchMarkerType,
    modifier: Modifier = Modifier,
    visualSize: Dp? = null,
    selected: Boolean = false,
) {
    val presentation = ResearchMarkerCatalog.presentation(type, selected)
    val resolvedVisualSize = visualSize ?: ResearchMarkerCatalog.NORMAL_SIZE_DP.dp
    Box(
        modifier = modifier
            .size(ResearchMarkerCatalog.TOUCH_TARGET_SIZE_DP.dp)
            .semantics {
                contentDescription = presentation.contentDescription
                this.selected = presentation.selected
                stateDescription = if (presentation.selected) "Выбрано" else "Обычное состояние"
            }
            .testTag("research-marker-${type.name.lowercase()}"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(resolvedVisualSize)
                .testTag("research-marker-${type.name.lowercase()}-canvas"),
        ) {
            drawResearchMarker(presentation)
        }
    }
}

private fun DrawScope.drawResearchMarker(presentation: ResearchMarkerPresentation) {
    val width = size.minDimension
    val height = size.maxDimension
    val pin = markerPinPath(width, height)
    drawPath(
        path = pin,
        color = Color.White,
        style = Stroke(width = width * if (presentation.selected) 0.16f else 0.08f, join = StrokeJoin.Round),
    )
    drawPath(pin, presentation.familyColor)
    drawPath(
        path = pin,
        color = BeeMarkOutline,
        style = Stroke(width = width * 0.035f, join = StrokeJoin.Round),
    )
    drawMarkerPictogram(presentation.shape, width, height)
}

private fun markerPinPath(width: Float, height: Float): Path = Path().apply {
    moveTo(width * 0.50f, height * ResearchMarkerCatalog.PIN_TIP_FRACTION)
    cubicTo(width * 0.42f, height * 0.84f, width * 0.14f, height * 0.61f, width * 0.14f, height * 0.38f)
    cubicTo(width * 0.14f, height * 0.16f, width * 0.30f, height * 0.09f, width * 0.50f, height * 0.09f)
    cubicTo(width * 0.70f, height * 0.09f, width * 0.86f, height * 0.16f, width * 0.86f, height * 0.38f)
    cubicTo(width * 0.86f, height * 0.61f, width * 0.58f, height * 0.84f, width * 0.50f, height * ResearchMarkerCatalog.PIN_TIP_FRACTION)
    close()
}

private fun DrawScope.drawMarkerPictogram(
    shape: ResearchMarkerShape,
    width: Float,
    height: Float,
) {
    val center = Offset(width / 2f, height * 0.38f)
    when (shape) {
        ResearchMarkerShape.BEE -> {
            val beeHeight = height * 0.48f
            val beeWidth = beeHeight * org.beesearch.app.ui.observation.BeeMarkAspectRatio
            val left = center.x - beeWidth / 2f
            val top = center.y - beeHeight / 2f
            // inset changes DrawScope.size: the exact existing bee geometry scales uniformly.
            inset(left = left, top = top, right = size.width - left - beeWidth, bottom = size.height - top - beeHeight) {
                drawBeeMark(
                    colors = beeMarkSegmentColors("WHITE", MarkPosition.ABDOMEN),
                    legacyMark = false,
                    accent = Color.White,
                )
            }
        }
        ResearchMarkerShape.TREE -> drawTreePictogram(center, width, height)
        ResearchMarkerShape.LOG -> drawLogPictogram(center, width, height)
        ResearchMarkerShape.BOX -> drawBoxPictogram(center, width, height)
        ResearchMarkerShape.HOUSE -> drawHousePictogram(center, width, height)
    }
}

private fun DrawScope.drawTreePictogram(center: Offset, width: Float, height: Float) {
    val white = Color.White
    val tree = Path().apply {
        moveTo(center.x, center.y - height * 0.22f)
        lineTo(center.x - width * 0.22f, center.y + height * 0.10f)
        lineTo(center.x - width * 0.10f, center.y + height * 0.10f)
        lineTo(center.x - width * 0.25f, center.y + height * 0.27f)
        lineTo(center.x + width * 0.25f, center.y + height * 0.27f)
        lineTo(center.x + width * 0.10f, center.y + height * 0.10f)
        lineTo(center.x + width * 0.22f, center.y + height * 0.10f)
        close()
    }
    drawPath(tree, white)
    drawRoundRect(
        color = white,
        topLeft = Offset(center.x - width * 0.055f, center.y + height * 0.18f),
        size = androidx.compose.ui.geometry.Size(width * 0.11f, height * 0.14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.02f),
    )
}

private fun DrawScope.drawLogPictogram(center: Offset, width: Float, height: Float) {
    val white = Color.White
    drawRoundRect(
        color = white,
        topLeft = Offset(center.x - width * 0.16f, center.y - height * 0.12f),
        size = androidx.compose.ui.geometry.Size(width * 0.30f, height * 0.40f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.04f),
    )
    drawOval(
        color = white,
        topLeft = Offset(center.x - width * 0.17f, center.y - height * 0.18f),
        size = androidx.compose.ui.geometry.Size(width * 0.34f, height * 0.16f),
    )
    drawOval(
        color = BeeMarkOutline,
        topLeft = Offset(center.x - width * 0.10f, center.y - height * 0.14f),
        size = androidx.compose.ui.geometry.Size(width * 0.20f, height * 0.08f),
        style = Stroke(width = width * 0.025f),
    )
    drawLine(white, Offset(center.x + width * 0.14f, center.y - height * 0.01f), Offset(center.x + width * 0.28f, center.y - height * 0.08f), strokeWidth = width * 0.08f, cap = StrokeCap.Round)
}

private fun DrawScope.drawBoxPictogram(center: Offset, width: Float, height: Float) {
    val white = Color.White
    val top = Path().apply { moveTo(center.x, center.y - height * 0.18f); lineTo(center.x + width * 0.22f, center.y - height * 0.07f); lineTo(center.x, center.y + height * 0.04f); lineTo(center.x - width * 0.22f, center.y - height * 0.07f); close() }
    val left = Path().apply { moveTo(center.x - width * 0.22f, center.y - height * 0.07f); lineTo(center.x, center.y + height * 0.04f); lineTo(center.x, center.y + height * 0.28f); lineTo(center.x - width * 0.22f, center.y + height * 0.16f); close() }
    val right = Path().apply { moveTo(center.x, center.y + height * 0.04f); lineTo(center.x + width * 0.22f, center.y - height * 0.07f); lineTo(center.x + width * 0.22f, center.y + height * 0.16f); lineTo(center.x, center.y + height * 0.28f); close() }
    drawPath(top, white); drawPath(left, white); drawPath(right, white)
    drawLine(BeeMarkOutline, Offset(center.x, center.y + height * 0.04f), Offset(center.x, center.y + height * 0.28f), width * 0.018f)
}

private fun DrawScope.drawHousePictogram(center: Offset, width: Float, height: Float) {
    val white = Color.White
    val roof = Path().apply { moveTo(center.x, center.y - height * 0.22f); lineTo(center.x - width * 0.27f, center.y); lineTo(center.x + width * 0.27f, center.y); close() }
    drawPath(roof, white)
    drawRect(
        color = white,
        topLeft = Offset(center.x - width * 0.20f, center.y - height * 0.01f),
        size = androidx.compose.ui.geometry.Size(width * 0.40f, height * 0.28f),
    )
    drawRect(
        color = BeeMarkOutline,
        topLeft = Offset(center.x - width * 0.06f, center.y + height * 0.10f),
        size = androidx.compose.ui.geometry.Size(width * 0.12f, height * 0.17f),
    )
}
