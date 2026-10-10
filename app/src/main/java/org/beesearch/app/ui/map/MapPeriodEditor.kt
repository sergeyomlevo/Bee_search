package org.beesearch.app.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import org.beesearch.app.domain.model.ResearchDateInterval

/**
 * The approved `Период` editor of one type.
 *
 * The editor shows the projection of the current filter on the selected precision and nothing else:
 * an interval that the precision cannot express shows no selection while the summary keeps the real
 * value, so changing precision can never widen, lose or reset a filter (§13 of the approved
 * specification). A tap changes the filter immediately; there is no draft.
 */
@Composable
internal fun MapPeriodEditor(
    type: MapDataType,
    period: ResearchDateInterval?,
    availableYears: List<Int>,
    availableMonths: List<YearMonth>,
    hasRecords: Boolean,
    today: LocalDate,
    onPeriodChange: (ResearchDateInterval?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var precision by rememberSaveable(type.name) { mutableStateOf(coarsestPrecision(period)) }
    val selection = projectPeriod(period, precision)

    Column(
        modifier = modifier.fillMaxWidth().testTag("period-editor-${type.name}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The canonical-date sentence sits at the top of the expanded section, next to the control
        // that changes the value it explains.
        Text(
            text = type.filterSentence,
            modifier = Modifier
                .padding(horizontal = TEXT_INSET)
                .testTag("period-explanation-${type.name}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = TEXT_INSET),
        ) {
            PeriodPrecision.entries.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = precision == value,
                    onClick = { precision = value },
                    shape = SegmentedButtonDefaults.itemShape(index, PeriodPrecision.entries.size),
                    modifier = Modifier.testTag("period-precision-${type.name}-${value.name}"),
                ) { Text(precisionLabel(value)) }
            }
        }

        when {
            availableYears.isEmpty() && !hasRecords -> Text(
                text = "Нет записей этого типа в текущей территории.",
                modifier = Modifier
                    .padding(horizontal = TEXT_INSET)
                    .testTag("period-empty-${type.name}"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            availableYears.isEmpty() -> Text(
                // Records exist but their canonical date is unknown, so no year can be offered.
                text = "Нет записей этого типа с известной датой фиксации.",
                modifier = Modifier
                    .padding(horizontal = TEXT_INSET)
                    .testTag("period-undated-${type.name}"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            precision == PeriodPrecision.YEAR -> YearUnits(
                type = type,
                years = availableYears,
                selection = selection,
                onSelect = { onPeriodChange(it) },
            )

            precision == PeriodPrecision.MONTH -> MonthUnits(
                type = type,
                years = availableYears,
                selection = selection,
                today = today,
                onSelect = { onPeriodChange(it) },
            )

            else -> DayUnits(
                type = type,
                years = availableYears,
                months = availableMonths,
                selection = selection,
                today = today,
                onSelect = { onPeriodChange(it) },
            )
        }
    }
}

internal fun precisionLabel(precision: PeriodPrecision): String = when (precision) {
    PeriodPrecision.YEAR -> "Год"
    PeriodPrecision.MONTH -> "Месяц"
    PeriodPrecision.DAY -> "День"
}

/** One precision: a year, a month or a day, always selected as one contiguous interval. */
private fun unitTap(
    selection: PeriodSelection?,
    unit: PeriodUnit,
): ResearchDateInterval? = applyUnitTap(selection, unit)?.interval

@Composable
private fun YearUnits(
    type: MapDataType,
    years: List<Int>,
    selection: PeriodSelection?,
    onSelect: (ResearchDateInterval?) -> Unit,
) {
    UnitGrid(
        tag = "period-years-${type.name}",
        preferredColumns = YEARS_PER_ROW,
        cells = years.map { yearUnit(it) },
        selection = selection,
        tagOf = { it.label },
        onSelect = { onSelect(unitTap(selection, it)) },
    )
    PeriodHint(
        type = type,
        hint = when {
            // Approved mockup help for the shapes the reference shows.
            selection?.second != null ->
                "Непрерывный диапазон лет даёт период от 01.01 первого года до 31.12 последнего года."

            selection != null -> "Один год даёт период с 01.01 по 31.12 этого года."
            else -> null
        },
        tag = "period-hint-years",
    )
}

@Composable
private fun MonthUnits(
    type: MapDataType,
    years: List<Int>,
    selection: PeriodSelection?,
    today: LocalDate,
    onSelect: (ResearchDateInterval?) -> Unit,
) {
    var navigatedYear by rememberSaveable(type.name, "month-level") { mutableStateOf<Int?>(null) }
    // Navigation stays inside the window of years that actually have records of this type.
    val displayedYear = (navigatedYear ?: initialMonthLevelYear(selection, years, today))
        .coerceIn(years.first(), years.last())
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NavigationRow(
            tag = "period-months-${type.name}",
            label = "$displayedYear",
            previousDescription = "Предыдущий год",
            nextDescription = "Следующий год",
            canGoPrevious = displayedYear > years.first(),
            canGoNext = displayedYear < years.last(),
            onPrevious = { navigatedYear = displayedYear - 1 },
            onNext = { navigatedYear = displayedYear + 1 },
        )
        UnitGrid(
            tag = "period-month-grid-${type.name}",
            preferredColumns = MONTHS_PER_ROW,
            cells = (1..12).map { monthUnit(displayedYear, it) },
            selection = selection,
            tagOf = { it.label },
            // The visible year belongs to the navigation header, so the cell shows the month alone.
            displayOf = { shortMonthName(it.start.monthValue).uppercase() },
            onSelect = { onSelect(unitTap(selection, it)) },
        )
        PeriodHint(
            type = type,
            hint = if (selection?.second != null) {
                "Несколько месяцев подряд дают период от начала первого до конца последнего."
            } else {
                null
            },
            tag = "period-hint-months",
        )
    }
}

@Composable
private fun DayUnits(
    type: MapDataType,
    years: List<Int>,
    months: List<YearMonth>,
    selection: PeriodSelection?,
    today: LocalDate,
    onSelect: (ResearchDateInterval?) -> Unit,
) {
    var navigatedMonth by rememberSaveable(type.name, "day-level") { mutableStateOf<YearMonth?>(null) }
    val firstMonth = YearMonth.of(years.first(), 1)
    val lastMonth = YearMonth.of(years.last(), 12)
    val displayedMonth = (navigatedMonth ?: initialDayLevelMonth(selection, months, today))
        .coerceIn(firstMonth, lastMonth)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NavigationRow(
            tag = "period-days-${type.name}",
            label = "${fullMonthName(displayedMonth.monthValue)} ${displayedMonth.year}",
            previousDescription = "Предыдущий месяц",
            nextDescription = "Следующий месяц",
            canGoPrevious = displayedMonth > firstMonth,
            canGoNext = displayedMonth < lastMonth,
            onPrevious = { navigatedMonth = displayedMonth.minusMonths(1) },
            onNext = { navigatedMonth = displayedMonth.plusMonths(1) },
        )
        DayCalendar(
            type = type,
            displayedMonth = displayedMonth,
            selection = selection,
            onSelect = { onSelect(unitTap(selection, it)) },
        )
        // Approved mockup help: a range whose other end lies in another month stays in force and is
        // only visible in the summary, so the editor explains where it continues.
        val end = selection?.second?.start
        val endMonth = end?.let { YearMonth.from(it) }
        PeriodHint(
            type = type,
            hint = when {
                end == null || endMonth == displayedMonth -> null
                endMonth == displayedMonth.plusMonths(1) ->
                    "Конец диапазона — ${researchDateText(end)}; диапазон продолжается в следующем месяце."

                endMonth == displayedMonth.minusMonths(1) ->
                    "Конец диапазона — ${researchDateText(end)}; диапазон начался в предыдущем месяце."

                else ->
                    "Конец диапазона — ${researchDateText(end)}; он находится в другом месяце."
            },
            tag = "period-hint-days",
        )
    }
}

@Composable
private fun PeriodHint(type: MapDataType, hint: String?, tag: String) {
    if (hint == null) return
    Text(
        text = hint,
        modifier = Modifier.padding(horizontal = TEXT_INSET).testTag("$tag-${type.name}"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NavigationRow(
    tag: String,
    label: String,
    previousDescription: String,
    nextDescription: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = canGoPrevious,
            modifier = Modifier
                // A full square, not only the height: the Material container is narrower than the
                // approved touch target on its own.
                .size(TOUCH_TARGET)
                .semantics { contentDescription = previousDescription }
                .testTag("$tag-previous"),
        ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
        Text(
            text = label,
            modifier = Modifier.testTag("$tag-label"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier
                .size(TOUCH_TARGET)
                .semantics { contentDescription = nextDescription }
                .testTag("$tag-next"),
        ) { Text("›", style = MaterialTheme.typography.titleLarge) }
    }
}

/**
 * Grid of calendar units.
 *
 * Columns reflow instead of shrinking: the row uses the preferred count while every cell can still
 * offer the required touch target, and fewer columns when it cannot. A larger system `fontScale`
 * reflows the month and year grids one step earlier, so their labels are never squeezed, and no unit
 * is ever reachable only through a scroll gesture. The grids keep the small insets of the approved
 * frames, which is what lets the seven-column weekday calendar still fit a 360 dp phone.
 */
@Composable
private fun UnitGrid(
    tag: String,
    preferredColumns: Int,
    cells: List<PeriodUnit>,
    selection: PeriodSelection?,
    tagOf: (PeriodUnit) -> String,
    onSelect: (PeriodUnit) -> Unit,
    displayOf: (PeriodUnit) -> String = { it.label },
) {
    val interval = selection?.interval
    val fontScaleCap = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        LARGE_FONT_COLUMN_CAP
    } else {
        Int.MAX_VALUE
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GRID_INSET).testTag(tag),
    ) {
        val columns = minOf(fittingColumns(maxWidth, preferredColumns), fontScaleCap)
        val cellWidth = cellWidth(maxWidth, columns)
        Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
            cells.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                ) {
                    row.forEach { unit ->
                        UnitCell(
                            unit = unit,
                            display = displayOf(unit),
                            selection = selection,
                            interval = interval,
                            tag = tagOf(unit),
                            onClick = { onSelect(unit) },
                            modifier = Modifier.width(cellWidth),
                        )
                    }
                    // Keeps the last row aligned with the rows above.
                    repeat(columns - row.size) { Box(Modifier.width(cellWidth)) }
                }
            }
        }
    }
}

/**
 * Approved day level: a weekday-aligned grid of day numbers.
 *
 * The cell shows the day number (a full date would be truncated at the largest system font), while
 * the accessible name of the cell and the summary keep the full date. The weekday layout is kept
 * while all seven columns can still offer the required touch target; on a narrower screen the grid
 * reflows to fewer columns, where a weekday header would no longer be true to the calendar and is
 * therefore not shown.
 */
@Composable
private fun DayCalendar(
    type: MapDataType,
    displayedMonth: YearMonth,
    selection: PeriodSelection?,
    onSelect: (PeriodUnit) -> Unit,
) {
    val firstDay = displayedMonth.atDay(1)
    val days = (1..displayedMonth.lengthOfMonth()).map { dayUnit(displayedMonth.atDay(it)) }
    val interval = selection?.interval
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DAY_GRID_INSET)
            .testTag("period-day-grid-${type.name}"),
    ) {
        val columns = fittingColumns(maxWidth, DAYS_PER_ROW, DAY_GRID_GAP)
        val cellWidth = cellWidth(maxWidth, columns, DAY_GRID_GAP)
        val alignedToWeekdays = columns == DAYS_PER_ROW
        Column(verticalArrangement = Arrangement.spacedBy(DAY_GRID_GAP)) {
            if (alignedToWeekdays) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DAY_GRID_GAP),
                ) {
                    WEEKDAY_INITIALS.forEach { name ->
                        Text(
                            text = name,
                            modifier = Modifier
                                .width(cellWidth)
                                // The tag stays outside the cleared semantics; the header is
                                // decorative because each cell carries its own full date.
                                .testTag("period-weekday-${type.name}-$name")
                                .clearAndSetSemantics { },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            val leadingBlanks = if (alignedToWeekdays) firstDay.dayOfWeek.value - 1 else 0
            val padded = List(leadingBlanks) { null } + days
            padded.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DAY_GRID_GAP),
                ) {
                    row.forEach { unit ->
                        if (unit == null) {
                            Box(Modifier.width(cellWidth).heightIn(min = TOUCH_TARGET))
                        } else {
                            UnitCell(
                                unit = unit,
                                display = "${unit.start.dayOfMonth}",
                                selection = selection,
                                interval = interval,
                                tag = unit.label,
                                onClick = { onSelect(unit) },
                                modifier = Modifier.width(cellWidth),
                            )
                        }
                    }
                    repeat(columns - row.size) {
                        Box(Modifier.width(cellWidth).heightIn(min = TOUCH_TARGET))
                    }
                }
            }
        }
    }
}

/**
 * Fewest columns, at most [preferred], that still gives every cell [TOUCH_TARGET] of width.
 *
 * The count never falls below one column, so a cell is always reachable and never narrower than the
 * approved minimum.
 */
internal fun fittingColumns(
    available: Dp,
    preferred: Int,
    gap: Dp = GRID_GAP,
): Int {
    for (columns in preferred downTo 1) {
        if ((available - gap * (columns - 1)) / columns >= TOUCH_TARGET) return columns
    }
    return 1
}

internal fun cellWidth(available: Dp, columns: Int, gap: Dp = GRID_GAP): Dp =
    ((available - gap * (columns - 1)) / columns).coerceAtLeast(TOUCH_TARGET)

@Composable
private fun UnitCell(
    unit: PeriodUnit,
    display: String,
    selection: PeriodSelection?,
    interval: ResearchDateInterval?,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isStart = selection?.first?.start == unit.start
    val isEnd = (selection?.second ?: selection?.first)?.end == unit.end
    val isBoundary = isStart || isEnd
    val inRange = interval != null &&
        unit.start >= interval.fromDate && unit.end <= interval.toDate
    val state = when {
        isBoundary && selection?.second != null -> "Граница диапазона"
        isBoundary -> "Выбрано"
        inRange -> "В диапазоне"
        else -> "Не выбрано"
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = TOUCH_TARGET)
            .semantics {
                role = Role.Button
                contentDescription = unit.label
                selected = isBoundary
                stateDescription = state
            }
            .testTag(tag),
        shape = MaterialTheme.shapes.small,
        // Selection is not carried by colour alone: a boundary is a filled, bold cell with its own
        // container, units inside the range carry an outline, and the accessibility state names all
        // three cases in words.
        color = when {
            isBoundary -> MaterialTheme.colorScheme.primary
            inRange -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = when {
            isBoundary -> null
            inRange -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            else -> null
        },
        contentColor = if (isBoundary) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Text(
            text = display,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBoundary) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Approved composition: months and years in three cells per row, days in seven. */
private const val MONTHS_PER_ROW = 3
private const val YEARS_PER_ROW = 3
private const val DAYS_PER_ROW = 7

/** At the largest system font the month and year grids use fewer columns, as the specification asks. */
private const val LARGE_FONT_COLUMN_CAP = 2

private val GRID_GAP = 6.dp

/** Insets of the approved frames: text keeps its margin, the calendars stay nearly full-bleed. */
private val TEXT_INSET = 16.dp
private val GRID_INSET = 6.dp
private val DAY_GRID_INSET = 4.dp

/**
 * Day cells sit closer together than month cells, which is what lets the approved seven-column
 * weekday calendar offer a full touch target on a 360 dp phone (7 × 48 dp + 6 × 2 dp = 348 dp).
 */
private val DAY_GRID_GAP = 2.dp

private val TOUCH_TARGET = 48.dp

private val WEEKDAY_INITIALS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
