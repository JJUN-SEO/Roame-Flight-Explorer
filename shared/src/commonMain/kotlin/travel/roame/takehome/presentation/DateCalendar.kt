package travel.roame.takehome.presentation

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import travel.roame.takehome.model.FlightFilters

/**
 * The date range being picked (K3). Each app keeps one while its calendar is open, applies each tap with
 * `setDateRange(startIso, endIso)`, and draws [DateCalendar.months]. ISO `yyyy-MM-dd` dates, null for no bound.
 */
data class DateSelection(val startIso: String?, val endIso: String?, val awaitingEnd: Boolean)

/** One month of the calendar (K1): weeks of seven days, Sunday first. */
data class CalendarMonth(val year: Int, val month: Int, val weeks: List<CalendarWeek>)

data class CalendarWeek(val days: List<CalendarDay>)

data class CalendarDay(
    val iso: String,
    val day: Int,
    /** False for the neighbouring months' days that fill the first and last week. The apps leave those cells empty. */
    val inMonth: Boolean,
    /** K2: inside the data's dates. */
    val isSelectable: Boolean,
    val mark: DayMark,
)

/** K4: how a day shows the selection. */
enum class DayMark { Unmarked, Start, Between, End, Single }

/**
 * DOMAIN_RULES K1–K5: the date picker's months and taps, written once so both apps' calendars behave the same (D41).
 * The apps write the month names and weekday letters.
 */
object DateCalendar {

    /** What the calendar starts from when it opens: the current filter, with no end awaited. */
    fun selectionOf(filters: FlightFilters): DateSelection = DateSelection(filters.dateStart, filters.dateEnd, awaitingEnd = false)

    /**
     * K3: with a start and its end awaited, a day on or after the start ends the range; any other day starts a new one
     * and awaits its end. K2: a day outside [minIso, maxIso] changes nothing.
     */
    fun tap(selection: DateSelection, dayIso: String, minIso: String, maxIso: String): DateSelection {
        if (dayIso < minIso || dayIso > maxIso) return selection
        val start = selection.startIso
        return if (selection.awaitingEnd && start != null && dayIso >= start) {
            DateSelection(start, dayIso, awaitingEnd = false)
        } else {
            DateSelection(dayIso, null, awaitingEnd = true)
        }
    }

    /** K1, K2, K4: every month from [minIso]'s to [maxIso]'s, with each day's mark. Empty without data. */
    fun months(minIso: String?, maxIso: String?, selection: DateSelection): List<CalendarMonth> {
        if (minIso == null || maxIso == null) return emptyList()
        val min = LocalDate.parse(minIso)
        val max = LocalDate.parse(maxIso)
        val marks = marker(selection, minIso, maxIso)
        val months = mutableListOf<CalendarMonth>()
        var first = LocalDate(min.year, min.month.number, 1)
        while (first <= max) {
            val next = first.plus(1, DateTimeUnit.MONTH)
            // ISO numbers Monday 1 to Sunday 7, so this is how many days the week has before the 1st.
            var day = first.minus(first.dayOfWeek.isoDayNumber % 7, DateTimeUnit.DAY)
            val weeks = mutableListOf<CalendarWeek>()
            while (day < next) {
                weeks += CalendarWeek(
                    List(7) {
                        val date = day
                        day = day.plus(1, DateTimeUnit.DAY)
                        val iso = date.toString()
                        val inMonth = date >= first && date < next
                        CalendarDay(iso, date.day, inMonth, isSelectable = inMonth && date in min..max, mark = if (inMonth) marks(iso) else DayMark.Unmarked)
                    },
                )
            }
            months += CalendarMonth(first.year, first.month.number, weeks)
            first = next
        }
        return months
    }

    /**
     * K4: while the end is awaited, only the start is marked. Otherwise the range runs from the start, or the data's
     * first date, to the end, or its last date (F2), so the default shows the whole range.
     */
    private fun marker(selection: DateSelection, minIso: String, maxIso: String): (String) -> DayMark {
        if (selection.awaitingEnd && selection.startIso != null) {
            val start = selection.startIso
            return { iso -> if (iso == start) DayMark.Single else DayMark.Unmarked }
        }
        val start = selection.startIso ?: minIso
        val end = selection.endIso ?: maxIso
        return { iso ->
            when {
                start == end && iso == start -> DayMark.Single
                iso == start -> DayMark.Start
                iso == end -> DayMark.End
                iso > start && iso < end -> DayMark.Between
                else -> DayMark.Unmarked
            }
        }
    }
}
