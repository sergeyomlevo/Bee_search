package org.beesearch.app.ui.map

import java.time.LocalDate
import java.time.YearMonth
import org.beesearch.app.domain.model.ResearchDateInterval
import org.beesearch.app.domain.model.*

/**
 * Research-data types that «Данные на карте» can display.
 *
 * Only types with a user-reachable lifecycle are listed. `Apiary` and the future `Inspection` are
 * deliberately absent: the approved direction shows a type row only once its user-facing capability
 * exists, and never as a disabled row. `Trap` has no domain type at all, so it is not a product type
 * yet; its D102 box shape stays a reserved marker definition.
 */
internal enum class MapDataType(val userName: String) {
    OBSERVATION_POINT("Точки наблюдения"),
    HOLLOW("Дупла"),
    LOG_HIVE("Колоды"),
    ;

    /** The approved explanatory sentence shown inside the expanded period section. */
    val filterSentence: String
        get() = when (this) {
            OBSERVATION_POINT -> "Точки фильтруются по дате наблюдения."
            HOLLOW -> "Дупла фильтруются по дате фиксации."
            LOG_HIVE -> "Колоды фильтруются по дате фиксации."
        }
}

/** Visibility and typed independent filter criteria of one research type. */
internal data class MapTypeDisplay(
    val visible: Boolean = true,
    val filters: ResearchObjectFilterSet = ObservationPointFilterSet(),
) {
    // Legacy call sites and v1 codec use this convenience projection.
    val period: ResearchDateInterval? get() = filters.dateInterval
}

internal fun defaultFilters(type: MapDataType): ResearchObjectFilterSet = when (type) {
    MapDataType.OBSERVATION_POINT -> ObservationPointFilterSet()
    else -> PhysicalObjectFilterSet()
}

internal fun ResearchObjectFilterSet.withDate(value: ResearchDateInterval?): ResearchObjectFilterSet = when (this) {
    is ObservationPointFilterSet -> copy(dateInterval = value)
    is PhysicalObjectFilterSet -> copy(dateInterval = value)
}

internal fun filterSummary(filters: ResearchObjectFilterSet): String {
    val parts = buildList {
        filters.dateInterval?.let { add(periodSummary(it)) }
        when (filters) {
            is ObservationPointFilterSet -> {
                if (filters.beeCount.isActive) add("пчёл " + rangeSummary(filters.beeCount.min, filters.beeCount.max))
                if (filters.flightCycleCount.isActive) add("циклов " + rangeSummary(filters.flightCycleCount.min, filters.flightCycleCount.max))
            }
            is PhysicalObjectFilterSet -> {
                if (filters.entranceHeightCm.isActive) add("высота " + rangeSummary(filters.entranceHeightCm.min, filters.entranceHeightCm.max) + " см")
                if (filters.outerDiameterCm.isActive) add("диаметр " + rangeSummary(filters.outerDiameterCm.min, filters.outerDiameterCm.max) + " см")
            }
        }
    }
    return if (parts.isEmpty()) "Все данные" else parts.joinToString(" · ")
}

internal fun rangeSummary(min: Number?, max: Number?): String = when {
    min == null && max == null -> "Без ограничения"
    min == null -> "≤ ${numberText(max!!)}"
    max == null -> "≥ ${numberText(min)}"
    min.toDouble() == max.toDouble() -> "= ${numberText(min)}"
    else -> "${numberText(min)}–${numberText(max)}"
}

private fun numberText(value: Number): String = value.toString().removeSuffix(".0")

internal data class MapDataDisplayState(
    val types: Map<MapDataType, MapTypeDisplay> = defaultTypeDisplays(),
) {
    fun display(type: MapDataType): MapTypeDisplay = types[type] ?: MapTypeDisplay(filters = defaultFilters(type))
    fun isVisible(type: MapDataType): Boolean = display(type).visible
    fun period(type: MapDataType): ResearchDateInterval? = display(type).period
    fun summary(type: MapDataType): String = filterSummary(display(type).filters)
    fun withVisibility(type: MapDataType, visible: Boolean): MapDataDisplayState =
        copy(types = types + (type to display(type).copy(visible = visible)))
    fun withPeriod(type: MapDataType, period: ResearchDateInterval?): MapDataDisplayState =
        withFilters(type, display(type).filters.withDate(period))
    fun withFilters(type: MapDataType, filters: ResearchObjectFilterSet): MapDataDisplayState {
        require((type == MapDataType.OBSERVATION_POINT) == (filters is ObservationPointFilterSet))
        return copy(types = types + (type to display(type).copy(filters = filters)))
    }
    fun withResetPeriod(type: MapDataType): MapDataDisplayState = withPeriod(type, null)
    fun withResetFilters(): MapDataDisplayState =
        copy(types = MapDataType.entries.associateWith { display(it).copy(filters = defaultFilters(it)) })
    val hasActiveRestriction: Boolean
        get() = MapDataType.entries.any { !isVisible(it) || display(it).filters != defaultFilters(it) }
    val isDefault: Boolean get() = this == DEFAULT_MAP_DATA_DISPLAY
}

/** Default state of a Territory that has no saved display state: everything visible, no period. */
internal val DEFAULT_MAP_DATA_DISPLAY = MapDataDisplayState()

private fun defaultTypeDisplays(): Map<MapDataType, MapTypeDisplay> =
    MapDataType.entries.associateWith { MapTypeDisplay(filters = defaultFilters(it)) }

/** Calendar precision of the approved period editor. Precision is a mode, never a filter value. */
internal enum class PeriodPrecision {
    YEAR,
    MONTH,
    DAY,
}

internal const val ALL_TIME_SUMMARY = "Всё время"

/**
 * System font scale from which a panel row or grid reflows instead of sharing one line.
 *
 * The specification requires the largest supported scale (1.7) to keep every value readable and
 * unclipped; this is the point where a row stops trying to fit name and value side by side.
 */
internal const val LARGE_FONT_SCALE = 1.5f

private val RU_DATE = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Canonical user-facing spelling of one research date; shared by labels and the calendar. */
internal fun researchDateText(date: LocalDate): String = date.format(RU_DATE)

private val RU_MONTH_SHORT = listOf(
    "янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек",
)

internal fun shortMonthName(month: Int): String = RU_MONTH_SHORT[month - 1]

/** Full month name of the editor grid, e.g. «август». */
internal fun fullMonthName(month: Int): String = listOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
)[month - 1]

/**
 * Approved period summary: the coarsest precision that expresses the interval exactly.
 *
 * The rule is independent of the editor precision, so one interval always produces one summary.
 */
internal fun periodSummary(period: ResearchDateInterval?): String {
    if (period == null) return ALL_TIME_SUMMARY
    val from = period.fromDate
    val to = period.toDate
    if (from == to) return from.format(RU_DATE)
    if (isFullYears(from, to)) {
        return if (from.year == to.year) "${from.year}" else "${from.year}–${to.year}"
    }
    if (isFullMonths(from, to)) {
        val start = YearMonth.from(from)
        val end = YearMonth.from(to)
        return when {
            start == end -> "${shortMonthName(start.monthValue)} ${start.year}"
            start.year == end.year ->
                "${shortMonthName(start.monthValue)}–${shortMonthName(end.monthValue)} ${start.year}"
            else ->
                "${shortMonthName(start.monthValue)} ${start.year} – " +
                    "${shortMonthName(end.monthValue)} ${end.year}"
        }
    }
    return "${from.format(RU_DATE)} — ${to.format(RU_DATE)}"
}

/**
 * Coarsest precision that expresses [period] exactly; `Год` when there is no period.
 *
 * The same rule decides the editor precision on entering the type screen and the summary wording.
 */
internal fun coarsestPrecision(period: ResearchDateInterval?): PeriodPrecision = when {
    period == null -> PeriodPrecision.YEAR
    isFullYears(period.fromDate, period.toDate) -> PeriodPrecision.YEAR
    isFullMonths(period.fromDate, period.toDate) -> PeriodPrecision.MONTH
    else -> PeriodPrecision.DAY
}

private fun isFullYears(from: LocalDate, to: LocalDate): Boolean =
    from == LocalDate.of(from.year, 1, 1) && to == LocalDate.of(to.year, 12, 31)

private fun isFullMonths(from: LocalDate, to: LocalDate): Boolean =
    from.dayOfMonth == 1 && to.dayOfMonth == to.lengthOfMonth()

/** One selectable calendar unit: a year, a month or a day, always as a closed date range. */
internal data class PeriodUnit(
    val precision: PeriodPrecision,
    val start: LocalDate,
    val end: LocalDate,
    /** User-facing name of the unit, e.g. `2026`, `авг 2026`, `17.08.2026`. */
    val label: String,
) {
    init {
        require(start <= end) { "period unit is reversed" }
    }

    val year: Int get() = start.year

    val yearMonth: YearMonth get() = YearMonth.from(start)
}

internal fun yearUnit(year: Int): PeriodUnit =
    PeriodUnit(PeriodPrecision.YEAR, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "$year")

internal fun monthUnit(yearMonth: YearMonth): PeriodUnit = PeriodUnit(
    precision = PeriodPrecision.MONTH,
    start = yearMonth.atDay(1),
    end = yearMonth.atEndOfMonth(),
    label = "${shortMonthName(yearMonth.monthValue)} ${yearMonth.year}",
)

internal fun monthUnit(year: Int, month: Int): PeriodUnit = monthUnit(YearMonth.of(year, month))

internal fun dayUnit(date: LocalDate): PeriodUnit =
    PeriodUnit(PeriodPrecision.DAY, date, date, date.format(RU_DATE))

/**
 * Current selection inside the editor: one tapped unit, or a started range.
 *
 * The selection is always contiguous by construction, so a disjoint period cannot be expressed.
 */
internal data class PeriodSelection(val first: PeriodUnit, val second: PeriodUnit? = null) {
    /** The selected units as one closed interval, normalized when the two taps are reversed. */
    val interval: ResearchDateInterval
        get() {
            val other = second ?: first
            val start = minOf(first.start, other.start)
            val end = maxOf(first.end, other.end)
            return ResearchDateInterval(start, end)
        }

    val isSingleUnit: Boolean get() = second == null
}

/**
 * Approved tap rule, identical at every precision:
 *
 * - nothing selected → this unit is selected (start = end);
 * - one unit selected → a contiguous range between the two units, normalized;
 * - the same unit tapped again → the selection is cleared, which means «Всё время»;
 * - a range selected → a new selection starts with this unit.
 */
internal fun applyUnitTap(current: PeriodSelection?, unit: PeriodUnit): PeriodSelection? = when {
    current == null -> PeriodSelection(unit)
    current.second != null -> PeriodSelection(unit)
    current.first.start == unit.start -> null
    else -> PeriodSelection(current.first, unit)
}

/**
 * Projects [period] onto [precision] for display: the selection that reproduces the interval exactly,
 * or `null` when the interval cannot be expressed at that precision.
 *
 * The interval itself never changes, so switching precision never widens or drops a filter.
 */
internal fun projectPeriod(
    period: ResearchDateInterval?,
    precision: PeriodPrecision,
): PeriodSelection? {
    if (period == null) return null
    val from = period.fromDate
    val to = period.toDate
    return when (precision) {
        PeriodPrecision.DAY -> PeriodSelection(
            dayUnit(from),
            if (from == to) null else dayUnit(to),
        )

        PeriodPrecision.MONTH -> {
            if (!isFullMonths(from, to)) return null
            val start = YearMonth.from(from)
            val end = YearMonth.from(to)
            PeriodSelection(monthUnit(start), if (start == end) null else monthUnit(end))
        }

        PeriodPrecision.YEAR -> {
            if (!isFullYears(from, to)) return null
            PeriodSelection(
                yearUnit(from.year),
                if (from.year == to.year) null else yearUnit(to.year),
            )
        }
    }
}

/** Years with records of a type, ascending; the year list is built from data, never invented. */
internal fun availableYearsOf(dates: List<LocalDate>): List<Int> =
    dates.map { it.year }.distinct().sorted()

/** Months with records of a type, ascending; used by day-level navigation. */
internal fun availableMonthsOf(dates: List<LocalDate>): List<YearMonth> =
    dates.map { YearMonth.from(it) }.distinct().sorted()

/**
 * Year shown by the month grid on entry: the year of the current selection, else the newest year
 * with records, else the current calendar year.
 */
internal fun initialMonthLevelYear(
    selection: PeriodSelection?,
    availableYears: List<Int>,
    today: LocalDate,
): Int = selection?.first?.year ?: availableYears.lastOrNull() ?: today.year

/**
 * Month shown by the day grid on entry: the month of the current selection, else the newest month
 * with records, else the current calendar month.
 */
internal fun initialDayLevelMonth(
    selection: PeriodSelection?,
    availableMonths: List<YearMonth>,
    today: LocalDate,
): YearMonth = selection?.first?.yearMonth ?: availableMonths.lastOrNull() ?: YearMonth.from(today)
