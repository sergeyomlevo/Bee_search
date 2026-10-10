package org.beesearch.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.cos
import kotlin.math.sin

internal const val MAIN_BOTTOM_PANEL_TAG = "main-bottom-panel"
internal const val OBJECTS_DESCRIPTION = "Объекты"
internal const val SETTINGS_DESCRIPTION = "Настройки"
internal const val MAP_DATA_DESCRIPTION = "Данные на карте"
internal const val MAP_DATA_INDICATOR_TAG = "map-data-indicator"

private val ToolbarBackground = Color(0xFF555555)
private val ToolbarActive = Color(0xFF966B4F)
private val ToolbarDivider = Color(0xFF8A8A8A)
private val ToolbarIcon = Color.White
private val ToolbarIndicator = Color(0xFFB7E27A)
private val ToolbarShape = RoundedCornerShape(10.dp)
private val ToolbarHeight = 56.dp
private val GlyphHeight = 48.dp
private val ActiveInset = 4.dp
private val DividerInset = 12.dp

/** Four equal map actions, kept icon-only for the compact field workflow. */
@Composable
internal fun MapBottomPanel(
    onOpenObjects: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMapData: () -> Unit = {},
    mapDataOpen: Boolean = false,
    mapDataRestricted: Boolean = false,
) {
    val mapDataDescription = if (mapDataRestricted) {
        "$MAP_DATA_DESCRIPTION. Есть активные ограничения отображения."
    } else {
        MAP_DATA_DESCRIPTION
    }

    Surface(
        color = ToolbarBackground,
        shape = ToolbarShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(ToolbarHeight)
            .testTag(MAIN_BOTTOM_PANEL_TAG),
    ) {
        Row(Modifier.fillMaxSize()) {
            ToolbarZone(
                contentDescription = "Анализ",
                stateDescription = "Пока недоступно",
                enabled = false,
                testTag = "open-analysis",
                onClick = {},
                drawDivider = true,
            ) { AnalysisGlyph() }
            ToolbarZone(
                contentDescription = mapDataDescription,
                enabled = true,
                active = mapDataOpen,
                testTag = "open-map-data",
                onClick = onOpenMapData,
                drawDivider = true,
                showIndicator = mapDataRestricted,
            ) {
                EyeGlyph()
            }
            ToolbarZone(
                contentDescription = OBJECTS_DESCRIPTION,
                testTag = "open-objects",
                onClick = onOpenObjects,
                drawDivider = true,
            ) { ObjectsGlyph() }
            ToolbarZone(
                contentDescription = SETTINGS_DESCRIPTION,
                testTag = "open-settings",
                onClick = onOpenSettings,
            ) { SettingsGlyph(background = ToolbarBackground) }
        }
    }
}

@Composable
private fun RowScope.ToolbarZone(
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
    drawDivider: Boolean = false,
    enabled: Boolean = true,
    active: Boolean = false,
    stateDescription: String? = null,
    showIndicator: Boolean = false,
    content: @Composable () -> Unit,
) {
    val buttonModifier = Modifier
        .fillMaxSize()
        .semantics {
            role = Role.Button
            this.contentDescription = contentDescription
            stateDescription?.let { this.stateDescription = it }
            selected = active
        }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .testTag(testTag)

    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(buttonModifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(ActiveInset)
                .clip(ToolbarShape)
                .then(if (active) Modifier.background(ToolbarActive) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalContentColor provides ToolbarIcon.copy(alpha = if (enabled) 1f else 0.45f),
            ) {
                content()
            }
        }
        }
        if (drawDivider) {
            Canvas(Modifier.width(1.dp).fillMaxHeight().align(Alignment.CenterEnd)) {
                drawLine(
                    color = ToolbarDivider,
                    start = Offset(0.5.dp.toPx(), DividerInset.toPx()),
                    end = Offset(0.5.dp.toPx(), size.height - DividerInset.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        if (showIndicator) {
            // The indicator is supplementary; the spoken restriction stays in the action description.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(9.dp)
                    .background(ToolbarIndicator, androidx.compose.foundation.shape.CircleShape)
                    .testTag(MAP_DATA_INDICATOR_TAG)
                    .clearAndSetSemantics { }
                    .zIndex(1f),
            )
        }
    }
}

@Composable
private fun AnalysisGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(GlyphHeight)) {
        val baseline = size.height - 2.dp.toPx()
        val barWidth = 11.dp.toPx()
        val gap = 4.dp.toPx()
        val left = 3.5.dp.toPx()
        listOf(18.dp, 32.dp, 44.dp).forEachIndexed { index, barHeight ->
            drawRoundRect(
                color = color,
                topLeft = Offset(left + index * (barWidth + gap), baseline - barHeight.toPx()),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun EyeGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(width = GlyphHeight * 1.5f, height = GlyphHeight)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val left = 3.dp.toPx()
        val right = size.width - left
        val eye = Path().apply {
            moveTo(left, center.y)
            cubicTo(size.width * .28f, -2.67.dp.toPx(), size.width * .72f, -2.67.dp.toPx(), right, center.y)
            cubicTo(size.width * .72f, size.height + 2.67.dp.toPx(), size.width * .28f, size.height + 2.67.dp.toPx(), left, center.y)
            close()
        }
        drawPath(eye, color = color, style = Stroke(width = 5.dp.toPx()))
        drawCircle(color = color, radius = 9.dp.toPx(), center = center)
    }
}

@Composable
private fun ObjectsGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(GlyphHeight)) {
        val cell = 20.dp.toPx()
        val gap = 4.dp.toPx()
        val total = cell * 2f + gap
        val startX = (size.width - total) / 2f
        val startY = (size.height - total) / 2f
        repeat(2) { row ->
            repeat(2) { column ->
                drawRoundRect(
                    color = color,
                    topLeft = Offset(startX + column * (cell + gap), startY + row * (cell + gap)),
                    size = androidx.compose.ui.geometry.Size(cell, cell),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun SettingsGlyph(background: Color) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(GlyphHeight)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = size.minDimension * 0.49f
        val valley = size.minDimension * 0.36f
        val path = Path().apply {
            repeat(32) { index ->
                val tooth = index / 4
                val corner = index % 4
                val degrees = -90.0 + tooth * 45 + listOf(-11.0, 11.0, 11.0, 34.0)[corner]
                val angle = Math.toRadians(degrees)
                val radius = if (index % 4 == 0 || index % 4 == 1) outer else valley
                val point = Offset(
                    center.x + (cos(angle) * radius).toFloat(),
                    center.y + (sin(angle) * radius).toFloat(),
                )
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
            close()
        }
        drawPath(path, color = color)
        drawCircle(color = background, radius = size.minDimension * 0.18f, center = center)
    }
}
