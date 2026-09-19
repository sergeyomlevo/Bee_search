package org.beesearch.app.ui.observation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.MarkPosition

/**
 * Stylised Bee that identifies a mark without any text: the colour sits on the
 * thorax or on the abdomen, and every other part of the body stays dark, so the
 * colour and the position are read from the same glance.
 *
 * The graphic is drawn parametrically, so no per-combination asset exists and
 * the icon scales to any requested [height].
 */
@Composable
internal fun BeeMarkIcon(
    markColor: String,
    markPosition: MarkPosition,
    modifier: Modifier = Modifier,
    height: Dp = BeeMarkIconHeight,
) {
    val colors = beeMarkSegmentColors(markColor, markPosition)
    val legacy = beeMarkMarkedSegment(markPosition) == null
    val accent = markColorValue(markColor)
    Canvas(
        modifier = modifier
            .size(width = height * BeeMarkAspectRatio, height = height)
            .semantics {
                contentDescription = BeeMarkCatalog.displayName(markColor, markPosition)
            }
            .testTag("bee-mark-$markColor-${markPosition.name}"),
    ) {
        drawBeeMark(colors = colors, legacyMark = legacy, accent = accent)
    }
}

/** Default drawn height of a mark inside a Bee card. */
internal val BeeMarkIconHeight = 96.dp

/**
 * Width/height of the drawn Bee. The silhouette is noticeably taller than wide
 * and wings are not drawn, so the icon reserves a tall narrow slot instead of a
 * square one.
 */
internal const val BeeMarkAspectRatio = 0.36f

// Body geometry as a fraction of the drawn height, measured from a field-review
// reference: a small head, a noticeably larger thorax, and an abdomen that is
// the dominant mass of the silhouette. Adjacent segments overlap slightly, the
// way a real head rests on a thorax.
internal const val BeeMarkOutlineFraction = 0.042f
internal const val BeeHeadRadius = 0.090f
internal const val BeeHeadCenterY = 0.114f
internal const val BeeThoraxRadius = 0.158f
internal const val BeeThoraxCenterY = 0.368f
internal const val BeeAbdomenRadiusX = 0.156f
internal const val BeeAbdomenRadiusY = 0.218f
internal const val BeeAbdomenCenterY = 0.760f

// A legacy mark has no confirmed position, so the colour cannot be placed on a
// body segment. The body shrinks and the colour becomes a ring around it.
private const val LegacyBodyScale = 0.72f
private const val LegacyRingStrokeFraction = 0.030f

private fun DrawScope.drawBeeMark(
    colors: BeeMarkSegmentColorSet,
    legacyMark: Boolean,
    accent: Color,
) {
    val drawnHeight = size.height
    val centerX = size.width / 2f
    val outline = drawnHeight * BeeMarkOutlineFraction
    val bodyScale = if (legacyMark) LegacyBodyScale else 1f

    scale(scaleX = bodyScale, scaleY = bodyScale, pivot = center) {
        val abdomenRadiusX = BeeAbdomenRadiusX * drawnHeight
        val abdomenRadiusY = BeeAbdomenRadiusY * drawnHeight
        val abdomenTopLeft = Offset(
            x = centerX - abdomenRadiusX,
            y = BeeAbdomenCenterY * drawnHeight - abdomenRadiusY,
        )
        val abdomenSize = Size(width = abdomenRadiusX * 2f, height = abdomenRadiusY * 2f)
        drawOval(color = colors.abdomen, topLeft = abdomenTopLeft, size = abdomenSize)
        drawOval(
            color = BeeMarkOutline,
            topLeft = abdomenTopLeft,
            size = abdomenSize,
            style = Stroke(width = outline),
        )

        val thoraxRadius = BeeThoraxRadius * drawnHeight
        val thoraxCenter = Offset(centerX, BeeThoraxCenterY * drawnHeight)
        drawCircle(color = colors.thorax, radius = thoraxRadius, center = thoraxCenter)
        drawCircle(
            color = BeeMarkOutline,
            radius = thoraxRadius,
            center = thoraxCenter,
            style = Stroke(width = outline),
        )

        val headRadius = BeeHeadRadius * drawnHeight
        val headCenter = Offset(centerX, BeeHeadCenterY * drawnHeight)
        drawCircle(color = colors.head, radius = headRadius, center = headCenter)
        drawCircle(
            color = BeeMarkOutline,
            radius = headRadius,
            center = headCenter,
            style = Stroke(width = outline),
        )
    }

    if (legacyMark) {
        // The dark ring keeps a WHITE legacy mark visible on a light background.
        val ringStroke = drawnHeight * LegacyRingStrokeFraction
        val ringRadiusX = size.width / 2f - ringStroke
        val ringRadiusY = drawnHeight / 2f - ringStroke
        val ringTopLeft = Offset(centerX - ringRadiusX, drawnHeight / 2f - ringRadiusY)
        val ringSize = Size(width = ringRadiusX * 2f, height = ringRadiusY * 2f)
        drawOval(
            color = BeeMarkOutline,
            topLeft = ringTopLeft,
            size = ringSize,
            style = Stroke(width = ringStroke * 2f),
        )
        drawOval(
            color = accent,
            topLeft = ringTopLeft,
            size = ringSize,
            style = Stroke(width = ringStroke),
        )
    }
}
