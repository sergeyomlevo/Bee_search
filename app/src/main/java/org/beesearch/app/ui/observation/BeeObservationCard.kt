@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.observation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.heading.HeadingAccuracy
import org.beesearch.app.domain.heading.HeadingState
import org.beesearch.app.domain.model.BeeMarkCombination
import org.beesearch.app.domain.model.FlightCycle
import java.time.Instant
import org.beesearch.app.BeeFieldState
import org.beesearch.app.BeeLastReversibleAction
import org.beesearch.app.BeeObservationCardModel
import org.beesearch.app.formatAzimuthDegrees
import org.beesearch.app.formatElapsedTime
import org.beesearch.app.headingContentDescription

@Composable
internal fun BeeObservationCard(
    card: BeeObservationCardModel,
    now: Instant,
    headingState: HeadingState,
    isEventInProgress: Boolean,
    isAzimuthInProgress: Boolean,
    onRegisterReturn: () -> Unit,
    onStartNextFlight: () -> Unit,
    onUndoLastAction: (BeeLastReversibleAction) -> Unit,
    onCaptureAzimuth: (FlightCycle, Int) -> Unit,
) {
    val state = card.fieldState
    val stateStartedAt = card.stateStartedAt
    val stateText = when (state) {
        BeeFieldState.IN_FLIGHT -> "В полёте"
        BeeFieldState.AT_POINT -> "На точке"
    }
    val actionText = when (state) {
        BeeFieldState.IN_FLIGHT -> "ПРИЛЕТЕЛА"
        BeeFieldState.AT_POINT -> "УЛЕТЕЛА"
    }
    val latestCycle = card.latestCycle
    val lastReversibleAction = card.lastReversibleAction
    val undoDescription = when (lastReversibleAction) {
        is BeeLastReversibleAction.Azimuth -> "Отменить последний азимут"
        is BeeLastReversibleAction.Return -> "Отменить последний прилёт"
        is BeeLastReversibleAction.NextFlight -> "Отменить последний вылет"
        is BeeLastReversibleAction.FirstDeparture -> "Отменить первый вылет"
        null -> null
    }
    val openCycle = latestCycle?.takeIf { it.returnTime == null }
    val liveHeading = headingState as? HeadingState.Available
    val captureEnabled = openCycle != null &&
        !openCycle.azimuthCaptureConsumed &&
        openCycle.azimuthDeg == null &&
        liveHeading != null &&
        liveHeading.accuracy != HeadingAccuracy.UNRELIABLE &&
        !isEventInProgress &&
        !isAzimuthInProgress

    val cardColors = when (state) {
        BeeFieldState.IN_FLIGHT -> CardDefaults.cardColors(
            containerColor = InFlightCardBackground,
            contentColor = InFlightCardContent,
        )
        BeeFieldState.AT_POINT -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    val cardBorderColor = when (state) {
        BeeFieldState.IN_FLIGHT -> InFlightCardBorder
        BeeFieldState.AT_POINT -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = if (latestCycle == null) {
                    "На точке; первый вылет ещё не зафиксирован"
                } else {
                    stateText
                }
            }
            .testTag("bee-card-${card.bee.id}"),
        colors = cardColors,
        border = BorderStroke(2.dp, cardBorderColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The mark spans the whole card height, so the mark graphic is as
            // large as the card allows while both working rows stay beside it.
            BeeMarkIcon(
                markColor = card.bee.markColor,
                markPosition = card.bee.markPosition,
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stateText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bee-state-${card.bee.id}"),
                    )
                    Text(
                        stateStartedAt?.let { formatElapsedTime(it, now) } ?: "--:--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("bee-timer-${card.bee.id}"),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(BeeAzimuthSlotWidth), contentAlignment = Alignment.CenterStart) {
                        if (state == BeeFieldState.IN_FLIGHT || latestCycle?.azimuthDeg != null) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
                                    .clickable(
                                        enabled = captureEnabled,
                                        role = Role.Button,
                                        onClick = {
                                            if (openCycle != null && liveHeading != null) {
                                                onCaptureAzimuth(openCycle, liveHeading.trueHeadingDeg)
                                            }
                                        },
                                    )
                                    .padding(horizontal = 6.dp)
                                    .testTag("bee-azimuth-${card.bee.id}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = when {
                                        isAzimuthInProgress -> "…°"
                                        latestCycle?.azimuthDeg != null -> formatAzimuthDegrees(latestCycle.azimuthDeg)
                                        openCycle == null -> "—°"
                                        latestCycle?.azimuthCaptureConsumed == true -> "—°"
                                        liveHeading != null && liveHeading.accuracy == HeadingAccuracy.UNRELIABLE -> "! —"
                                        liveHeading != null && liveHeading.accuracy == HeadingAccuracy.LOW ->
                                            "! ${liveHeading.trueHeadingDeg}°"
                                        liveHeading != null -> "${liveHeading.trueHeadingDeg}°"
                                        headingState is HeadingState.Initializing -> "…°"
                                        else -> "нет"
                                    },
                                    color = when {
                                        latestCycle?.azimuthDeg != null -> MaterialTheme.colorScheme.onSurface
                                        liveHeading?.accuracy == HeadingAccuracy.LOW ||
                                            liveHeading?.accuracy == HeadingAccuracy.UNRELIABLE ->
                                            MaterialTheme.colorScheme.error
                                        captureEnabled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.semantics {
                                        contentDescription = headingContentDescription(
                                            persistedAzimuth = latestCycle?.azimuthDeg,
                                            headingState = headingState,
                                            isInFlight = openCycle != null,
                                            captureConsumed = latestCycle?.azimuthCaptureConsumed == true,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    // The flexible middle slot centers Undo between the neighboring
                    // azimuth and primary-action edges, including when either control is absent.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (lastReversibleAction != null && undoDescription != null) {
                            BeeUndoIconButton(
                                description = undoDescription,
                                enabled = !isEventInProgress && !isAzimuthInProgress,
                                onClick = { onUndoLastAction(lastReversibleAction) },
                                modifier = Modifier.testTag("bee-undo-${card.bee.id}"),
                            )
                        }
                    }
                    Box(Modifier.width(BeePrimaryActionWidth), contentAlignment = Alignment.CenterEnd) {
                        Button(
                            onClick = {
                                when (state) {
                                    BeeFieldState.IN_FLIGHT -> onRegisterReturn()
                                    BeeFieldState.AT_POINT -> onStartNextFlight()
                                }
                            },
                            enabled = !isEventInProgress && !isAzimuthInProgress,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minWidth = 104.dp, minHeight = 48.dp)
                                .testTag("bee-action-${card.bee.id}"),
                        ) {
                            Text(
                                text = if (isEventInProgress) "Сохранение" else actionText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AvailableBeeMarkCard(
    mark: BeeMarkCombination,
    enabled: Boolean,
    onStartFirstFlight: () -> Unit,
) {
    val key = "${mark.markColor}-${mark.markPosition.name}"
    val darkTheme = isSystemInDarkTheme()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = if (enabled) "Выбор" else "Выбор; лимит 10 пчёл достигнут"
            }
            .testTag("available-mark-$key"),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) ChoiceCardBackgroundDark else ChoiceCardBackground,
            contentColor = if (darkTheme) ChoiceCardContentDark else ChoiceCardContent,
        ),
        border = BorderStroke(2.dp, if (darkTheme) ChoiceCardBorderDark else ChoiceCardBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same layout as a Bee card: the tall mark spans both rows, and only
            // the state word differs ("Выбор" instead of "На точке").
            BeeMarkIcon(
                markColor = mark.markColor,
                markPosition = mark.markPosition,
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Выбор",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("available-mark-state-$key"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f))
                    Box(Modifier.width(BeePrimaryActionWidth), contentAlignment = Alignment.CenterEnd) {
                        Button(
                            onClick = onStartFirstFlight,
                            enabled = enabled,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minWidth = 104.dp, minHeight = 48.dp)
                                .testTag("available-mark-action-$key"),
                        ) {
                            Text(
                                text = "УЛЕТЕЛА",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BeeUndoIconButton(
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CorrectionActionContainer,
            contentColor = CorrectionActionContent,
        ),
        border = BorderStroke(2.dp, CorrectionActionBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = "↶",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private val InFlightCardBackground = Color(0xFFDCEFFF)
private val InFlightCardContent = Color(0xFF12324A)
private val InFlightCardBorder = Color(0xFF176394)
private val CorrectionActionContainer = Color(0xFFFFE1C6)
private val CorrectionActionContent = Color(0xFF713B00)
private val CorrectionActionBorder = Color(0xFF9A5200)
private val ChoiceCardBackground = Color(0xFFD6D6D6)
private val ChoiceCardContent = Color(0xFF1F1F1F)
private val ChoiceCardBorder = Color(0xFF8A8A8A)
private val ChoiceCardBackgroundDark = Color(0xFF3C3C3C)
private val ChoiceCardContentDark = Color(0xFFEDEDED)
private val ChoiceCardBorderDark = Color(0xFF9C9C9C)
private val BeeAzimuthSlotWidth = 72.dp
private val BeePrimaryActionWidth = 156.dp
