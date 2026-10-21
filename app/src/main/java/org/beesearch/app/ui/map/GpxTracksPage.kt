package org.beesearch.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.beesearch.app.ui.points.CompactScreenHeader

private val GpxInset = 16.dp

private enum class GpxGlyphKind { Upload, Map, Route, Pin }

/**
 * Nested page of the approved «Данные на карте» panel.
 *
 * Two independent actions: import is active; showing saved tracks is explicitly future work and is
 * disabled rather than an empty click target.
 */
@Composable
internal fun GpxTracksPage(
    onBack: () -> Unit,
    onImport: () -> Unit,
    onShowTracks: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .testTag("gpx-tracks-page"),
    ) {
        CompactScreenHeader(
            title = "Треки GPX",
            onBack = onBack,
            modifier = Modifier.testTag("gpx-tracks-header"),
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = GpxInset)) {
            GpxNavRow(
                kind = GpxGlyphKind.Upload,
                title = "Импорт GPX",
                subtitle = "Добавить GPX-файл из памяти телефона",
                enabled = true,
                onClick = onImport,
                testTag = "gpx-import-action",
                description = "Импорт GPX. Добавить GPX-файл из памяти телефона.",
            )
            GpxNavRow(
                kind = GpxGlyphKind.Map,
                title = "Показать трек",
                subtitle = "Выбрать сохранённый трек для карты",
                enabled = true,
                onClick = onShowTracks,
                testTag = "gpx-show-track-action",
                description = "Показать трек. Выбрать сохранённый трек для карты.",
            )
        }
    }
}

/** Entry row inside the main «Данные на карте» list; navigation, never visibility. */
@Composable
internal fun GpxEntryRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GpxNavRow(
        kind = GpxGlyphKind.Route,
        title = "Треки",
        subtitle = null,
        enabled = true,
        onClick = onClick,
        testTag = "gpx-tracks-row",
        modifier = modifier,
        description = "Треки",
    )
}

/** Independent user points use the existing nested map-data surface. */
@Composable
internal fun UserPointsEntryRow(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    GpxNavRow(
        kind = GpxGlyphKind.Pin,
        title = "Мои точки",
        subtitle = null,
        enabled = true,
        onClick = onClick,
        testTag = "user-points-row",
        modifier = modifier,
        description = "Мои точки",
    )
}

@Composable
private fun GpxNavRow(
    kind: GpxGlyphKind,
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    description: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val iconBackground = when {
            !enabled -> colors.surfaceVariant
            kind == GpxGlyphKind.Upload -> colors.primary
            else -> colors.primary.copy(alpha = 0.12f)
        }
        val iconTint = when {
            !enabled -> colors.onSurfaceVariant.copy(alpha = 0.65f)
            kind == GpxGlyphKind.Upload -> colors.onPrimary
            else -> colors.primary
        }
        Box(
            modifier = Modifier.size(34.dp).background(iconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GpxGlyph(kind = kind, tint = iconTint, modifier = Modifier.size(19.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = colors.outlineVariant,
                            shape = RoundedCornerShape(50),
                        ).padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun GpxGlyph(kind: GpxGlyphKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        when (kind) {
            GpxGlyphKind.Upload -> {
                val cx = w / 2f
                drawLine(tint, Offset(cx, h * 0.18f), Offset(cx, h * 0.62f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(cx, h * 0.18f), Offset(w * 0.34f, h * 0.38f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(cx, h * 0.18f), Offset(w * 0.66f, h * 0.38f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.2f, h * 0.82f), Offset(w * 0.8f, h * 0.82f), stroke, StrokeCap.Round)
            }
            GpxGlyphKind.Map -> {
                val path = Path().apply {
                    moveTo(w * 0.12f, h * 0.24f)
                    lineTo(w * 0.4f, h * 0.14f)
                    lineTo(w * 0.62f, h * 0.24f)
                    lineTo(w * 0.88f, h * 0.14f)
                    lineTo(w * 0.88f, h * 0.78f)
                    lineTo(w * 0.62f, h * 0.88f)
                    lineTo(w * 0.4f, h * 0.78f)
                    lineTo(w * 0.12f, h * 0.88f)
                    close()
                }
                drawPath(path, tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
                drawLine(tint, Offset(w * 0.4f, h * 0.16f), Offset(w * 0.4f, h * 0.8f), stroke)
                drawLine(tint, Offset(w * 0.62f, h * 0.22f), Offset(w * 0.62f, h * 0.86f), stroke)
            }
            GpxGlyphKind.Route -> {
                val path = Path().apply {
                    moveTo(w * 0.12f, h * 0.76f)
                    lineTo(w * 0.36f, h * 0.4f)
                    lineTo(w * 0.56f, h * 0.58f)
                    lineTo(w * 0.88f, h * 0.18f)
                }
                drawPath(
                    path, tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawCircle(tint, radius = 1.8.dp.toPx(), center = Offset(w * 0.12f, h * 0.76f))
                drawCircle(tint, radius = 1.8.dp.toPx(), center = Offset(w * 0.88f, h * 0.18f))
            }
            GpxGlyphKind.Pin -> {
                val pin = Path().apply {
                    moveTo(w * 0.5f, h * 0.9f)
                    cubicTo(w * 0.43f, h * 0.77f, w * 0.2f, h * 0.59f, w * 0.2f, h * 0.4f)
                    cubicTo(w * 0.2f, h * 0.17f, w * 0.8f, h * 0.17f, w * 0.8f, h * 0.4f)
                    cubicTo(w * 0.8f, h * 0.59f, w * 0.57f, h * 0.77f, w * 0.5f, h * 0.9f)
                    close()
                }
                drawPath(pin, tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
                drawCircle(tint, radius = 1.4.dp.toPx(), center = Offset(w * 0.5f, h * 0.4f))
            }
        }
    }
}
