@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.observation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.beesearch.app.domain.heading.HeadingProvider
import org.beesearch.app.domain.heading.HeadingReference
import org.beesearch.app.domain.heading.HeadingState
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.ObservationPoint
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import java.time.Instant
import org.beesearch.app.BeeFieldState
import org.beesearch.app.BeeLastReversibleAction
import org.beesearch.app.FEEDBACK_AUTO_DISMISS_MILLIS
import org.beesearch.app.FeedbackBanner
import org.beesearch.app.FeedbackDisplayMode
import org.beesearch.app.UiFeedback
import org.beesearch.app.buildBeeObservationCards
import org.beesearch.app.availableBeeMarks

@Composable
internal fun BeeObservationScreen(
    point: ObservationPoint,
    bees: List<Bee>,
    flightCycles: List<FlightCycle>,
    isStartingFirstFlight: Boolean = false,
    beeEventInProgressIds: Set<UUID>,
    flightAzimuthInProgressIds: Set<UUID> = emptySet(),
    headingProvider: HeadingProvider = HeadingProvider {
        flowOf(HeadingState.Unavailable("Датчик направления недоступен"))
    },
    feedback: UiFeedback? = null,
    onDismissFeedback: (Long) -> Unit = {},
    isCompleting: Boolean,
    onRegisterReturn: (UUID) -> Unit,
    onStartFirstFlight: (String, org.beesearch.app.domain.model.MarkPosition) -> Unit = { _, _ -> },
    onRecordNoBeesFound: () -> Unit = {},
    onStartNextFlight: (UUID) -> Unit,
    onUndoLastBeeAction: (UUID) -> Unit = {},
    onSetFlightAzimuth: (UUID, Double?, () -> Unit) -> Unit = { _, _, onSuccess -> onSuccess() },
    onCaptureFlightAzimuth: (UUID, Double, () -> Unit) -> Unit =
        { cycleId, value, onSuccess -> onSetFlightAzimuth(cycleId, value, onSuccess) },
    onComplete: () -> Unit,
    onOpenPointProperties: () -> Unit = {},
    nowProvider: () -> Instant = { Instant.now() },
    @Suppress("UNUSED_PARAMETER") undoTimeoutMillis: Long = FEEDBACK_AUTO_DISMISS_MILLIS,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var showCompletionConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoBeesConfirmation by rememberSaveable { mutableStateOf(false) }
    var pendingViewportTarget by remember { mutableStateOf<BeeViewportTarget?>(null) }
    var nowEpochMillis by remember { mutableLongStateOf(nowProvider().toEpochMilli()) }
    val observationListState = rememberLazyListState()
    val cards = remember(bees, flightCycles) {
        buildBeeObservationCards(bees, flightCycles)
    }
    val availableMarks = remember(bees) { availableBeeMarks(bees) }
    val beeLimitReached = bees.size >= BeeMarkCatalog.MAX_BEES_PER_OBSERVATION_POINT

    /**
     * A negative research result can be recorded only while no bee has ever been
     * observed at this point; once a real Bee exists, the presence result is
     * already BEES_FOUND.
     */
    val canRecordNoBeesFound = bees.isEmpty() && point.beePresenceResult == null
    val headingFlow = remember(headingProvider, point.latitude, point.longitude) {
        headingProvider.updates(
            HeadingReference(
                latitude = point.latitude,
                longitude = point.longitude,
                altitudeMeters = 0.0,
            ),
        )
    }
    val headingState by headingFlow.collectAsStateWithLifecycle(
        initialValue = HeadingState.Initializing,
    )

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                nowEpochMillis = nowProvider().toEpochMilli()
                delay(1_000)
            }
        }
    }

    LaunchedEffect(pendingViewportTarget, cards) {
        val target = pendingViewportTarget ?: return@LaunchedEffect
        val targetIndex = when {
            target.beeId != null -> cards.indexOfFirst { it.bee.id == target.beeId }
            target.beeMark != null -> cards.indexOfFirst { card ->
                card.bee.markColor == target.beeMark.first &&
                    card.bee.markPosition == target.beeMark.second
            }
            else -> -1
        }
        if (targetIndex < 0) return@LaunchedEffect
        val targetCard = cards[targetIndex]
        val stateMatches = targetCard.fieldState == target.expectedState
        val cycleMatches = target.expectedLatestCycleId == null ||
            targetCard.latestCycle?.id == target.expectedLatestCycleId
        if (!stateMatches || !cycleMatches) return@LaunchedEffect

        // The card can move between groups at the same time as feedback changes
        // the Scaffold viewport. Inspect the post-layout position so a newly
        // moved card is not treated as visible at its former location.
        withFrameNanos { }
        val layoutInfo = observationListState.layoutInfo
        val visibleTarget = layoutInfo.visibleItemsInfo.firstOrNull { it.key == targetCard.bee.id }
        val targetIsFullyVisible = visibleTarget != null &&
            visibleTarget.offset >= layoutInfo.viewportStartOffset &&
            visibleTarget.offset + visibleTarget.size <= layoutInfo.viewportEndOffset
        if (!targetIsFullyVisible) {
            observationListState.animateScrollToItem(
                index = targetIndex,
                // Leave enough context above the moved card for its marker and
                // state while keeping both the immediate next action and local
                // correction accessible at large font scales.
                scrollOffset = -layoutInfo.viewportSize.height / 4,
            )
        }
        pendingViewportTarget = null
    }

    if (showCompletionConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isCompleting) showCompletionConfirmation = false },
            title = { Text("Завершить наблюдение?") },
            text = {
                Text(
                    "Точка наблюдения будет завершена. " +
                        "Незаконченные полёты сохранятся как есть.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompletionConfirmation = false
                        onComplete()
                    },
                    enabled = !isCompleting,
                    modifier = Modifier.testTag("confirm-field-observation-completion"),
                ) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCompletionConfirmation = false },
                    enabled = !isCompleting,
                    modifier = Modifier.testTag("cancel-field-observation-completion"),
                ) { Text("Отмена") }
            },
        )
    }

    if (showNoBeesConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isCompleting) showNoBeesConfirmation = false },
            title = { Text("Пчёлы отсутствуют?") },
            text = {
                Text(
                    "Точка наблюдения будет сохранена с результатом " +
                        "«пчёлы отсутствуют» и завершена.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoBeesConfirmation = false
                        onRecordNoBeesFound()
                    },
                    enabled = !isCompleting,
                    modifier = Modifier.testTag("confirm-no-bees-from-observation"),
                ) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNoBeesConfirmation = false },
                    enabled = !isCompleting,
                    modifier = Modifier.testTag("cancel-no-bees-from-observation"),
                ) { Text("Отмена") }
            },
        )
    }

    // Only a real problem is announced here. A routine successful action - a departure, a return, a
    // recorded azimuth - is already visible on the card it belongs to, so a transient message would
    // duplicate the interface and, because it sits above the list, push the next card out from under
    // the finger that is working through the bees.
    val persistentFeedback = feedback?.takeIf {
        it.displayMode == FeedbackDisplayMode.PERSISTENT
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                .testTag("observation-header"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) { ObservationHeaderTitle() }
                            TextButton(
                                onClick = onOpenPointProperties,
                                modifier = Modifier
                                    .semantics { contentDescription = "Свойства точки" }
                                    .testTag("open-active-point-properties"),
                            ) { Text("ⓘ") }
                            TextButton(
                                onClick = { showCompletionConfirmation = true },
                                enabled = !isCompleting &&
                                    beeEventInProgressIds.isEmpty() &&
                                    flightAzimuthInProgressIds.isEmpty(),
                                modifier = Modifier.testTag("complete-field-observation"),
                            ) { Text(if (isCompleting) "Завершаем" else "Завершить") }
                        }
                        persistentFeedback?.let { banner ->
                            FeedbackBanner(
                                feedback = banner,
                                onDismiss = onDismissFeedback,
                                modifier = Modifier.testTag("observation-persistent-feedback"),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                state = observationListState,
                modifier = Modifier.fillMaxSize().padding(padding).testTag("bee-observation-list"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(cards, key = { it.bee.id }) { card ->
                    BeeObservationCard(
                        card = card,
                        now = Instant.ofEpochMilli(nowEpochMillis),
                        headingState = headingState,
                        isEventInProgress = card.bee.id in beeEventInProgressIds,
                        isAzimuthInProgress = card.latestCycle?.id in flightAzimuthInProgressIds,
                        onRegisterReturn = {
                            pendingViewportTarget = BeeViewportTarget(
                                beeId = card.bee.id,
                                expectedState = BeeFieldState.AT_POINT,
                                expectedLatestCycleId = card.latestCycle?.id,
                            )
                            onRegisterReturn(card.bee.id)
                        },
                        onStartNextFlight = {
                            pendingViewportTarget = BeeViewportTarget(
                                beeId = card.bee.id,
                                expectedState = BeeFieldState.IN_FLIGHT,
                            )
                            onStartNextFlight(card.bee.id)
                        },
                        onUndoLastAction = { action ->
                            pendingViewportTarget = BeeViewportTarget(
                                beeId = card.bee.id,
                                expectedState = when (action) {
                                    is BeeLastReversibleAction.Azimuth -> BeeFieldState.IN_FLIGHT
                                    is BeeLastReversibleAction.Return -> BeeFieldState.IN_FLIGHT
                                    is BeeLastReversibleAction.NextFlight -> BeeFieldState.AT_POINT
                                    is BeeLastReversibleAction.FirstDeparture -> BeeFieldState.AT_POINT
                                },
                                expectedLatestCycleId = when (action) {
                                    is BeeLastReversibleAction.NextFlight -> card.cycles
                                        .dropLast(1)
                                        .lastOrNull()
                                        ?.id
                                    is BeeLastReversibleAction.FirstDeparture -> null
                                    else -> action.flightCycleId
                                },
                            )
                            onUndoLastBeeAction(card.bee.id)
                        },
                        onCaptureAzimuth = { cycle, value ->
                            onCaptureFlightAzimuth(cycle.id, value.toDouble()) {}
                        },
                    )
                }
                items(
                    items = availableMarks,
                    key = { mark -> "${mark.markColor}-${mark.markPosition.name}" },
                ) { mark ->
                    AvailableBeeMarkCard(
                        mark = mark,
                        enabled = !beeLimitReached && !isStartingFirstFlight,
                        onStartFirstFlight = {
                            pendingViewportTarget = BeeViewportTarget(
                                beeMark = mark.markColor to mark.markPosition,
                                expectedState = BeeFieldState.IN_FLIGHT,
                            )
                            onStartFirstFlight(mark.markColor, mark.markPosition)
                        },
                    )
                }
                if (canRecordNoBeesFound) {
                    item(key = "record-no-bees") {
                        OutlinedButton(
                            onClick = { showNoBeesConfirmation = true },
                            enabled = !isCompleting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("record-no-bees-from-observation"),
                        ) { Text("Пчёлы отсутствуют") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObservationHeaderTitle() {
    Text(
        "Наблюдение",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.testTag("observation-header-title"),
    )
}

private data class BeeViewportTarget(
    val beeId: UUID? = null,
    /** Used right after the first departure, when the new Bee id is not known yet. */
    val beeMark: Pair<String, org.beesearch.app.domain.model.MarkPosition>? = null,
    val expectedState: BeeFieldState,
    val expectedLatestCycleId: UUID? = null,
)
